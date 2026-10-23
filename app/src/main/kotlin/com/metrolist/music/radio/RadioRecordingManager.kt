/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.radio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class RadioRecording(
    val id: String,
    val title: String,
    val stationName: String,
    val filePath: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
)

class RadioRecordingManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    private val recordingsDir: File by lazy {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let {
            File(it, "radio_recordings")
        } ?: File(context.filesDir, "radio_recordings")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val indexFile: File by lazy {
        File(recordingsDir, "recordings_index.json")
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0L)
    val recordingSeconds: StateFlow<Long> = _recordingSeconds.asStateFlow()

    private val _recordingStationName = MutableStateFlow<String?>(null)
    val recordingStationName: StateFlow<String?> = _recordingStationName.asStateFlow()

    private val _recordings = MutableStateFlow<List<RadioRecording>>(emptyList())
    val recordings: StateFlow<List<RadioRecording>> = _recordings.asStateFlow()

    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var currentActiveFile: File? = null
    private var currentStation: String = ""
    private var recordingStartTime = 0L

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        scope.launch {
            val list = readPersistedRecordings().filter { File(it.filePath).exists() }
            _recordings.value = list.sortedByDescending { it.timestamp }
        }
    }

    fun startRecording(stationName: String, streamUrl: String) {
        if (_isRecording.value) {
            Timber.w("RadioRecordingManager: already recording")
            return
        }

        var resolvedStream = streamUrl.trim()
        if (resolvedStream.contains("cdn.instream.audio:9714") ||
            resolvedStream.contains("solumedia.com.ar/urbanaplay") ||
            resolvedStream.contains("cdn.instream.audio:9660") ||
            resolvedStream.contains("vmf.edge-apps.net/playlist/urbana")
        ) {
            resolvedStream = "https://live-ar1.cdnrad.com/hls/radio-285/live.m3u8"
        }

        val cleanName = stationName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Radio_${cleanName}_${dateFormat.format(Date(timestamp))}.mp3"
        val outputFile = File(recordingsDir, fileName)

        currentActiveFile = outputFile
        currentStation = stationName
        recordingStartTime = timestamp
        _recordingStationName.value = stationName
        _recordingSeconds.value = 0L
        _isRecording.value = true

        // Timer job
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _recordingSeconds.value = (System.currentTimeMillis() - recordingStartTime) / 1000L
            }
        }

        // Recording stream job
        recordingJob = scope.launch {
            try {
                Timber.d("RadioRecordingManager: starting recording of $stationName from $resolvedStream")
                if (resolvedStream.contains(".m3u8", ignoreCase = true) || resolvedStream.contains("hls", ignoreCase = true)) {
                    recordHlsStream(resolvedStream, outputFile)
                } else {
                    recordDirectStream(resolvedStream, outputFile)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Timber.e(e, "RadioRecordingManager: stream recording error")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al grabar radio: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (_isRecording.value) {
                        stopRecording()
                    }
                }
            }
        }
    }

    private suspend fun recordDirectStream(streamUrl: String, outputFile: File) = withContext(Dispatchers.IO) {
        val url = URL(streamUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrMusic4all)")
            setRequestProperty("Icy-MetaData", "0")
        }
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            throw java.io.IOException("HTTP $code")
        }
        conn.inputStream.use { inputStream ->
            FileOutputStream(outputFile, true).use { fileOut ->
                val buffer = ByteArray(16384)
                while (isActive) {
                    val count = inputStream.read(buffer)
                    if (count == -1) break
                    fileOut.write(buffer, 0, count)
                }
                fileOut.flush()
            }
        }
    }

    private suspend fun recordHlsStream(m3u8Url: String, outputFile: File) = withContext(Dispatchers.IO) {
        var activePlaylistUrl = m3u8Url
        val downloadedSegments = mutableSetOf<String>()

        val initialContent = runCatching { URL(activePlaylistUrl).readText() }.getOrNull()
        if (initialContent != null && initialContent.contains("#EXT-X-STREAM-INF")) {
            val subLine = initialContent.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (subLine != null) {
                activePlaylistUrl = resolveUrl(activePlaylistUrl, subLine.trim())
            }
        }

        FileOutputStream(outputFile, true).use { fileOut ->
            while (isActive) {
                try {
                    val playlistText = URL(activePlaylistUrl).readText()
                    val lines = playlistText.lines()
                    val segments = mutableListOf<String>()
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                            segments.add(resolveUrl(activePlaylistUrl, trimmed))
                        }
                    }

                    val toDownload = if (downloadedSegments.isEmpty()) {
                        segments.takeLast(2)
                    } else {
                        segments.filter { it !in downloadedSegments }
                    }

                    for (segUrl in toDownload) {
                        if (!isActive) break
                        downloadedSegments.add(segUrl)
                        try {
                            val segConn = (URL(segUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 8000
                                readTimeout = 8000
                                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrMusic4all)")
                            }
                            if (segConn.responseCode in 200..299) {
                                segConn.inputStream.use { segIn ->
                                    val buf = ByteArray(16384)
                                    while (isActive) {
                                        val n = segIn.read(buf)
                                        if (n == -1) break
                                        fileOut.write(buf, 0, n)
                                    }
                                }
                                fileOut.flush()
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "RadioRecordingManager: error downloading segment")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "RadioRecordingManager: error refreshing HLS playlist")
                }
                delay(3000)
            }
        }
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        return try {
            URI(baseUrl).resolve(relativeOrAbsolute).toString()
        } catch (e: Exception) {
            val baseWithoutFile = baseUrl.substringBeforeLast("/")
            "$baseWithoutFile/$relativeOrAbsolute"
        }
    }

    fun stopRecording(): RadioRecording? {
        if (!_isRecording.value) return null

        timerJob?.cancel()
        timerJob = null
        recordingJob?.cancel()
        recordingJob = null

        val file = currentActiveFile
        val station = currentStation
        val durationSec = _recordingSeconds.value
        val startTime = recordingStartTime

        _isRecording.value = false
        _recordingSeconds.value = 0L
        _recordingStationName.value = null
        currentActiveFile = null

        if (file == null || !file.exists() || file.length() < 1024L) {
            Timber.w("RadioRecordingManager: recording file too small or missing, deleting")
            file?.delete()
            return null
        }

        val displayDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(startTime))
        val newRecording = RadioRecording(
            id = "rec_${startTime}",
            title = "$station - $displayDate",
            stationName = station,
            filePath = file.absolutePath,
            timestamp = startTime,
            durationSeconds = durationSec,
            fileSizeBytes = file.length(),
        )

        scope.launch {
            val updated = (readPersistedRecordings() + newRecording).distinctBy { it.id }
            writePersistedRecordings(updated)
            _recordings.value = updated.sortedByDescending { it.timestamp }
        }

        Timber.i("RadioRecordingManager: saved recording ${newRecording.title} (${file.length()} bytes)")
        return newRecording
    }

    fun deleteRecording(recording: RadioRecording) {
        scope.launch {
            try {
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
                val updated = readPersistedRecordings().filter { it.id != recording.id }
                writePersistedRecordings(updated)
                _recordings.value = updated.sortedByDescending { it.timestamp }
                Timber.d("RadioRecordingManager: deleted recording ${recording.title}")
            } catch (e: Exception) {
                Timber.e(e, "RadioRecordingManager: error deleting recording")
            }
        }
    }

    fun playRecording(recording: RadioRecording, playerConnection: PlayerConnection) {
        val metadata = MediaMetadata(
            id = "local:${recording.id}",
            title = recording.title,
            artists = listOf(MediaMetadata.Artist(name = recording.stationName, id = null)),
            duration = (recording.durationSeconds * 1000).toInt(),
            thumbnailUrl = null,
        )
        val mediaItem = metadata.toMediaItem().buildUpon()
            .setUri(File(recording.filePath).toUri())
            .build()

        playerConnection.playQueue(
            ListQueue(
                title = "Grabación: ${recording.stationName}",
                items = listOf(mediaItem),
            )
        )
    }

    fun exportRecordingToDownloads(recording: RadioRecording, ctx: Context = context): Boolean {
        val sourceFile = File(recording.filePath)
        if (!sourceFile.exists()) {
            Toast.makeText(ctx, "Archivo de grabación no encontrado", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val fileName = sourceFile.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/FrMusic4all")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Toast.makeText(ctx, "Grabación guardada en Descargas/FrMusic4all", Toast.LENGTH_LONG).show()
                    return true
                }
            }

            // Fallback for Android 9 / API 28 and older
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFolder = File(downloadsDir, "FrMusic4all")
            if (!targetFolder.exists()) targetFolder.mkdirs()
            val destFile = File(targetFolder, fileName)
            sourceFile.copyTo(destFile, overwrite = true)

            MediaScannerConnection.scanFile(
                ctx,
                arrayOf(destFile.absolutePath),
                arrayOf("audio/mpeg"),
                null
            )
            Toast.makeText(ctx, "Grabación guardada en Descargas/FrMusic4all: $fileName", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Timber.e(e, "RadioRecordingManager: error exporting recording")
            Toast.makeText(ctx, "Error al guardar en Descargas: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun shareRecording(recording: RadioRecording, ctx: Context = context) {
        val sourceFile = File(recording.filePath)
        if (!sourceFile.exists()) {
            Toast.makeText(ctx, "Archivo de grabación no encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.FileProvider",
                sourceFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Compartir grabación de radio")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            Timber.e(e, "RadioRecordingManager: error sharing recording")
            Toast.makeText(ctx, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readPersistedRecordings(): List<RadioRecording> {
        return try {
            if (!indexFile.exists()) return emptyList()
            val text = indexFile.readText()
            if (text.isBlank()) emptyList() else json.decodeFromString(text)
        } catch (e: Exception) {
            Timber.e(e, "RadioRecordingManager: error reading recordings index")
            emptyList()
        }
    }

    private fun writePersistedRecordings(recordings: List<RadioRecording>) {
        try {
            val text = json.encodeToString(recordings)
            indexFile.writeText(text)
        } catch (e: Exception) {
            Timber.e(e, "RadioRecordingManager: error writing recordings index")
        }
    }

    companion object {
        @Volatile
        private var instance: RadioRecordingManager? = null

        fun getInstance(context: Context): RadioRecordingManager {
            return instance ?: synchronized(this) {
                instance ?: RadioRecordingManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
