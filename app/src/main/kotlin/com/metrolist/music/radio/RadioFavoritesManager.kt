/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.radio

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

class RadioFavoritesManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    companion object {
        val DEFAULT_STATIONS = listOf(
            RadioStation(
                stationuuid = "def_mitre",
                name = "Radio Mitre AM 790",
                url = "https://stream-gtlc.telecentro.net.ar/hls/radiomitre/main.m3u8",
                country = "Argentina",
                countrycode = "AR",
                tags = "noticias, actualidad",
                favicon = "https://radiomitre.cienradios.com.ar/favicon.ico",
            ),
            RadioStation(
                stationuuid = "def_latina",
                name = "Radio Latina 101.1",
                url = "https://stream-gtlc.telecentro.net.ar/hls/latinatvhls/main.m3u8",
                country = "Argentina",
                countrycode = "AR",
                tags = "latino, pop, éxitos",
                favicon = "https://radiolatina.com.ar/favicon.ico",
            ),
            RadioStation(
                stationuuid = "def_la100",
                name = "La 100 FM 99.9",
                url = "https://stream-gtlc.telecentro.net.ar/hls/la100/main.m3u8",
                country = "Argentina",
                countrycode = "AR",
                tags = "pop, rock, hits",
                favicon = "https://la100.cienradios.com.ar/favicon.ico",
            ),
            RadioStation(
                stationuuid = "def_aspen",
                name = "Aspen 102.3",
                url = "https://stream.aspen1023.com/live",
                country = "Argentina",
                countrycode = "AR",
                tags = "clásicos, 80s, 90s",
                favicon = "https://aspen1023.com/favicon.ico",
            ),
            RadioStation(
                stationuuid = "def_urbanaplay",
                name = "Urbana Play 104.3",
                url = "https://live-ar1.cdnrad.com/hls/radio-285/live.m3u8",
                country = "Argentina",
                countrycode = "AR",
                tags = "rock, pop, streaming",
                favicon = "https://urbanaplayfm.com/favicon.ico",
            ),
        )
    }

    private val _favorites = MutableStateFlow<List<RadioStation>>(emptyList())
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    init {
        loadFavorites()
    }

    private fun getUserId(): String {
        return try {
            val email = context.dataStore[AccountEmailKey]?.trim()?.takeIf { it.isNotBlank() }
            val handle = context.dataStore[AccountChannelHandleKey]?.trim()?.takeIf { it.isNotBlank() }
            val spotifyId = context.dataStore[stringPreferencesKey("spotify_user_id")]?.trim()?.takeIf { it.isNotBlank() }
            val rawId = email ?: handle ?: spotifyId ?: "guest"
            rawId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        } catch (e: Exception) {
            "guest"
        }
    }

    private fun getFavoritesFile(): File {
        val userId = getUserId()
        return File(context.filesDir, "radio_favorites_$userId.json")
    }

    suspend fun getFavorites(): List<RadioStation> = withContext(Dispatchers.IO) {
        val file = getFavoritesFile()
        if (file.exists()) {
            try {
                val text = file.readText()
                val list = json.decodeFromString<List<RadioStation>>(text)
                _favorites.value = list
                return@withContext list
            } catch (e: Exception) {
                Timber.e(e, "Failed to load radio favorites")
            }
        }
        _favorites.value = emptyList()
        emptyList()
    }

    fun loadFavorites() {
        scope.launch {
            mutex.withLock {
                val file = getFavoritesFile()
                if (file.exists()) {
                    try {
                        val text = file.readText()
                        val list = json.decodeFromString<List<RadioStation>>(text)
                        _favorites.value = list
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load radio favorites")
                        _favorites.value = emptyList()
                    }
                } else {
                    _favorites.value = emptyList()
                }
            }
        }
    }

    private suspend fun saveFavoritesInternal(list: List<RadioStation>) {
        try {
            val text = json.encodeToString(list)
            getFavoritesFile().writeText(text)
            _favorites.value = list
        } catch (e: Exception) {
            Timber.e(e, "Failed to save radio favorites")
        }
    }

    fun isFavorite(uuid: String): Boolean {
        return _favorites.value.any { it.stationuuid == uuid }
    }

    fun toggleFavorite(station: RadioStation) {
        scope.launch {
            mutex.withLock {
                val current = _favorites.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.stationuuid == station.stationuuid }
                if (existingIndex != -1) {
                    current.removeAt(existingIndex)
                } else {
                    current.add(0, station)
                }
                saveFavoritesInternal(current)
            }
        }
    }

    fun addFavorite(station: RadioStation) {
        scope.launch {
            mutex.withLock {
                val current = _favorites.value.toMutableList()
                if (current.none { it.stationuuid == station.stationuuid }) {
                    current.add(0, station)
                    saveFavoritesInternal(current)
                }
            }
        }
    }

    fun removeFavorite(uuid: String) {
        scope.launch {
            mutex.withLock {
                val current = _favorites.value.toMutableList()
                val removed = current.removeAll { it.stationuuid == uuid }
                if (removed) {
                    saveFavoritesInternal(current)
                }
            }
        }
    }

    suspend fun exportToM3U(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val list = _favorites.value
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write("#EXTM3U\n")
                    for (station in list) {
                        val title = station.name
                        val logo = station.favicon ?: ""
                        val country = station.country ?: ""
                        writer.write("#EXTINF:-1 tvg-logo=\"$logo\" group-title=\"$country\",$title\n")
                        writer.write("${station.streamUrl}\n")
                    }
                    writer.flush()
                }
            } ?: throw java.io.IOException("Cannot open output stream")
            list.size
        }
    }

    suspend fun exportM3u(uri: Uri): Boolean = exportToM3U(uri).isSuccess

    suspend fun importFromM3U(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var importedCount = 0
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    var currentTitle = ""
                    var currentLogo: String? = null
                    var currentCountry: String? = null

                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isEmpty()) continue

                        if (trimmed.startsWith("#EXTINF:")) {
                            val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)
                            currentLogo = logoMatch?.groupValues?.get(1)

                            val groupMatch = Regex("group-title=\"([^\"]+)\"").find(trimmed)
                            currentCountry = groupMatch?.groupValues?.get(1)

                            currentTitle = trimmed.substringAfterLast(",", "").trim()
                        } else if (!trimmed.startsWith("#") && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                            val station = RadioStation(
                                stationuuid = "m3u_${UUID.randomUUID()}",
                                name = currentTitle.ifEmpty { "Radio Importada" },
                                url = trimmed,
                                country = currentCountry,
                                favicon = currentLogo,
                            )
                            addFavorite(station)
                            importedCount++
                            currentTitle = ""
                            currentLogo = null
                            currentCountry = null
                        }
                    }
                }
            } ?: throw java.io.IOException("Cannot open input stream")
            importedCount
        }
    }

    suspend fun importM3u(uri: Uri): Int = importFromM3U(uri).getOrDefault(0)
}
