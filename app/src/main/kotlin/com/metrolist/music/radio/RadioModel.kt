/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.radio

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioStation(
    @SerialName("stationuuid") val stationuuid: String,
    @SerialName("name") val name: String,
    @SerialName("url") val url: String,
    @SerialName("url_resolved") val urlResolved: String? = null,
    @SerialName("homepage") val homepage: String? = null,
    @SerialName("favicon") val favicon: String? = null,
    @SerialName("tags") val tags: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("countrycode") val countrycode: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("votes") val votes: Int = 0,
    @SerialName("codec") val codec: String? = null,
    @SerialName("bitrate") val bitrate: Int = 0,
    @SerialName("clickcount") val clickcount: Int = 0,
) {
    val streamUrl: String
        get() = urlResolved?.takeIf { it.isNotBlank() } ?: url

    fun toMediaMetadata(): MediaMetadata {
        val stream = streamUrl
        val mediaId = "radio:$stream"
        val subtitleParts = mutableListOf<String>()
        if (!country.isNullOrBlank()) subtitleParts.add(country)
        if (!tags.isNullOrBlank()) {
            val tagSummary = tags.split(",", " ").filter { it.isNotBlank() }.take(3).joinToString(", ")
            if (tagSummary.isNotBlank()) subtitleParts.add(tagSummary)
        }
        if (bitrate > 0) subtitleParts.add("$bitrate kbps")
        val subtitle = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ") else "Live Radio"

        return MediaMetadata(
            id = mediaId,
            title = name.trim().ifEmpty { "Radio Station" },
            artists = listOf(MediaMetadata.Artist(id = null, name = subtitle)),
            duration = -1,
            thumbnailUrl = favicon?.takeIf { it.isNotBlank() },
            album = country?.takeIf { it.isNotBlank() }?.let { MediaMetadata.Album(id = it, title = it) },
        )
    }

    fun toMediaItem(): MediaItem = toMediaMetadata().toMediaItem()
}

@Serializable
data class RadioCountry(
    @SerialName("name") val name: String,
    @SerialName("iso_3166_1") val isoCode: String,
    @SerialName("stationcount") val stationCount: Int = 0,
)

@Serializable
data class RadioTag(
    @SerialName("name") val name: String,
    @SerialName("stationcount") val stationCount: Int = 0,
)
