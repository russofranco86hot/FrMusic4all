/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberEnumPreference
import androidx.compose.ui.platform.LocalLocale
import java.io.File

@Composable
fun getLoudnessLevelLabel(loudnessLevel: LoudnessLevel): String {
    return when (loudnessLevel) {
        LoudnessLevel.AGGRESSIVE -> stringResource(R.string.loudness_level_aggressive)
        LoudnessLevel.LOUD -> stringResource(R.string.loudness_level_loud)
        LoudnessLevel.BALANCED -> stringResource(R.string.loudness_level_balanced)
        LoudnessLevel.QUIET -> stringResource(R.string.loudness_level_quiet)
    }
}

@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank()) return

    val windowInsets = WindowInsets.systemBars
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    val database = LocalDatabase.current
    var song by remember { mutableStateOf<Song?>(null) }
    var currentFormat by remember { mutableStateOf<FormatEntity?>(null) }

    val playerConnection = LocalPlayerConnection.current
    val currentStreamClient by playerConnection?.currentStreamClient?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    val artistNameAliases = LocalArtistNameAliases.current

    val loudnessLevel by rememberEnumPreference(
        LoudnessLevelKey,
        defaultValue = LoudnessLevel.BALANCED
    )
    val targetLufs: Float = loudnessLevel.targetLufs

    val isLocal = videoId.startsWith("local:") || song?.song?.isLocal == true

    val localFilePath = remember(song, videoId) {
        if (!isLocal) null
        else {
            val path = song?.song?.localPath
            if (!path.isNullOrBlank() && File(path).exists()) {
                path
            } else {
                val rawId = videoId.removePrefix("local:")
                try {
                    val uri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        rawId.toLong()
                    )
                    val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                            if (idx != -1) cursor.getString(idx) else null
                        } else null
                    }
                } catch (e: Exception) {
                    path
                }
            }
        }
    }

    LaunchedEffect(Unit, videoId) {
        if (!videoId.startsWith("local:")) {
            info = YouTube.getMediaInfo(videoId).getOrNull()
        }
    }

    LaunchedEffect(Unit, videoId) {
        database.song(videoId).collect {
            song = it
        }
    }

    LaunchedEffect(Unit, videoId) {
        database.format(videoId).collect {
            currentFormat = it
        }
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .padding(windowInsets.asPaddingValues())
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (info != null || isLocal || song != null) {
            item(contentType = "MediaDetails") {
                Column {
                    val baseList = mutableListOf<Pair<String, String?>>()
                    val baseIconsList = mutableListOf<Int>()

                    baseList += stringResource(R.string.song_title) to (song?.title ?: info?.title)
                    baseIconsList += R.drawable.music_note

                    baseList += stringResource(R.string.song_artists) to (
                        song?.artists?.joinToString {
                            ArtistNameAliases.resolve(artistNameAliases, it.id, it.name)
                        } ?: info?.author?.let {
                            ArtistNameAliases.resolve(artistNameAliases, null, it)
                        }
                    )
                    baseIconsList += R.drawable.person

                    if (!song?.song?.albumName.isNullOrBlank()) {
                        baseList += stringResource(R.string.album) to song?.song?.albumName
                        baseIconsList += R.drawable.album
                    }

                    baseList += stringResource(R.string.media_id) to (song?.id ?: info?.videoId)
                    baseIconsList += R.drawable.media3_icon_bookmark_filled

                    if (!localFilePath.isNullOrBlank()) {
                        baseList += stringResource(R.string.file_path) to localFilePath
                        baseIconsList += R.drawable.folder
                    }

                    val iconsList = mutableListOf<Int>()
                    val extendedList = mutableListOf<Pair<String, String?>>()

                    if (isLocal) {
                        if (song?.song?.duration != null && song!!.song.duration > 0) {
                            extendedList += stringResource(R.string.duration) to DateUtils.formatElapsedTime(song!!.song.duration.toLong())
                            iconsList += R.drawable.play
                        }
                        val fileSize = localFilePath?.let { File(it) }?.takeIf { it.exists() }?.length()
                        if (fileSize != null && fileSize > 0) {
                            extendedList += stringResource(R.string.file_size) to Formatter.formatShortFileSize(context, fileSize)
                            iconsList += R.drawable.content_copy
                        }
                    } else if (currentFormat != null) {
                        val measuredLufs: Double? = currentFormat?.perceptualLoudnessDb ?: currentFormat?.loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }

                        extendedList += stringResource(R.string.views) to info?.viewCount?.let(::numberFormatter).orEmpty()
                        iconsList += R.drawable.media3_icon_feed

                        extendedList += stringResource(R.string.likes) to info?.like?.let(::numberFormatter).orEmpty()
                        iconsList += R.drawable.media3_icon_thumb_up_unfilled

                        extendedList += stringResource(R.string.dislikes) to info?.dislike?.let(::numberFormatter).orEmpty()
                        iconsList += R.drawable.media3_icon_thumb_down_unfilled

                        extendedList += "Itag" to currentFormat?.itag?.toString()
                        iconsList += R.drawable.key

                        extendedList += stringResource(R.string.stream_client) to currentStreamClient
                        iconsList += R.drawable.play

                        extendedList += stringResource(R.string.mime_type) to currentFormat?.mimeType
                        iconsList += R.drawable.info

                        extendedList += stringResource(R.string.codecs) to currentFormat?.codecs
                        iconsList += R.drawable.radio

                        extendedList += stringResource(R.string.bitrate) to currentFormat?.bitrate?.let { "${it / 1000} Kbps" }
                        iconsList += R.drawable.gradient

                        extendedList += stringResource(R.string.sample_rate) to currentFormat?.sampleRate?.let { "$it Hz" }
                        iconsList += R.drawable.contrast

                        extendedList += stringResource(R.string.loudness) to measuredLufs?.let {
                            String.format(LocalLocale.current.platformLocale, "%.2f dB", it - targetLufs)
                        }
                        iconsList += R.drawable.volume_up

                        extendedList += stringResource(R.string.loudness_level) to getLoudnessLevelLabel(loudnessLevel)
                        iconsList += R.drawable.volume_up

                        extendedList += stringResource(R.string.volume) to if (playerConnection != null) "${(playerConnection.player.volume * 100).toInt()}%" else null
                        iconsList += R.drawable.volume_mute

                        extendedList += stringResource(R.string.file_size) to currentFormat?.contentLength?.let {
                            Formatter.formatShortFileSize(context, it)
                        }
                        iconsList += R.drawable.content_copy
                    }

                    val cardsBaseList = mutableListOf<Material3SettingsItem>()
                    val cardsExtendedList = mutableListOf<Material3SettingsItem>()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                    baseList.forEachIndexed { index, (label, text) ->
                        val displayText = text ?: stringResource(R.string.unknown)
                        cardsBaseList += Material3SettingsItem(
                            title = { Text(label) },
                            description = { Text(displayText) },
                            icon = painterResource(baseIconsList[index]),
                            onClick = {
                                cm.setPrimaryClip(ClipData.newPlainText("text", displayText))
                                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    extendedList.forEachIndexed { index, (label, text) ->
                        val displayText = text ?: stringResource(R.string.unknown)
                        cardsExtendedList += Material3SettingsItem(
                            title = { Text(label) },
                            description = { Text(displayText) },
                            icon = painterResource(iconsList[index]),
                            onClick = {
                                cm.setPrimaryClip(ClipData.newPlainText("text", displayText))
                                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    Material3SettingsGroup(
                        title = stringResource(if (isLocal) R.string.local_file_details else R.string.general),
                        items = cardsBaseList
                    )

                    if (cardsExtendedList.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Material3SettingsGroup(
                            title = stringResource(R.string.information),
                            items = cardsExtendedList
                        )
                    }

                    val descriptionText = info?.description
                    if (!descriptionText.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Material3SettingsGroup(
                            title = stringResource(R.string.description),
                            items = listOf(
                                Material3SettingsItem(
                                    title = { Text(stringResource(R.string.description)) },
                                    description = { Text(descriptionText) },
                                    onClick = {
                                        cm.setPrimaryClip(ClipData.newPlainText("text", descriptionText))
                                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            )
                        )
                    }
                }
            }
        } else {
            item(contentType = "MediaInfoLoader") {
                ShimmerHost {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp)
                    ) {
                        TextPlaceholder()
                    }
                }
            }
        }
    }
}
