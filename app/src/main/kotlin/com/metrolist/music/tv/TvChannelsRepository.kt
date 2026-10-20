/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.tv

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class TvChannelsRepository(private val context: Context) {
    private val favoriteChannelsKey = stringSetPreferencesKey("favorite_tv_channels")

    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[favoriteChannelsKey] ?: emptySet()
    }

    suspend fun toggleFavorite(channelId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[favoriteChannelsKey] ?: emptySet()).toMutableSet()
            if (current.contains(channelId)) {
                current.remove(channelId)
            } else {
                current.add(channelId)
            }
            prefs[favoriteChannelsKey] = current
        }
    }

    fun getDefaultChannels(): List<TvChannel> = listOf(
        // === CANALES DE STREAMING ===
        TvChannel(
            id = "tv_luzu",
            name = "LUZU TV",
            category = TvCategory.STREAMING,
            description = "Nadie Dice Nada, Antes Que Nadie, Patria y Familia, Tarde Para Nada",
            youtubeHandle = "@luzutv",
            logoUrl = "https://yt3.googleusercontent.com/4qKjBqFw_iYkC4q1YjKq3kG9qN9n5vE=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_olga",
            name = "OLGA",
            category = TvCategory.STREAMING,
            description = "Migue Granados, Soñé Que Volaba, Sería Increíble, Tapados de Laburo",
            youtubeHandle = "@olgaenvivo_",
            logoUrl = "https://yt3.googleusercontent.com/9nF_z5lX4Bq1v2q7b=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_blender",
            name = "Blender",
            category = TvCategory.STREAMING,
            description = "Hay Algo Ahí, Escucho Ofertas, Dinastía, Periodismo Puro",
            youtubeHandle = "@somosblender",
            logoUrl = "https://yt3.googleusercontent.com/4yZ3q9nF=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_gelatina",
            name = "Gelatina",
            category = TvCategory.STREAMING,
            description = "Pedro Rosemblat, Fábrica de Junkies, Gelatina en Vivo",
            youtubeHandle = "@gelatina",
            logoUrl = "https://yt3.googleusercontent.com/gelatina=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_bondi",
            name = "Bondi Live",
            category = TvCategory.STREAMING,
            description = "Ángel de Brito, Ángel Responde, El Ejército de LAM",
            youtubeHandle = "@bondi_live",
            logoUrl = "https://yt3.googleusercontent.com/bondi=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_urbana",
            name = "Urbana Play 104.3",
            category = TvCategory.STREAMING,
            description = "Andy Kusnetzoff, Matías Martin, Sebastián Wainraich, María O'Donnell",
            youtubeHandle = "@UrbanaPlay1043",
            directStreamUrl = "https://live-01-02-urbanaplay.solumedia.com.ar/urbanaplay/hls/urbanatv/main.m3u8",
            logoUrl = "https://yt3.googleusercontent.com/urbanaplay=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_neura",
            name = "Neura Media",
            category = TvCategory.STREAMING,
            description = "Alejandro Fantino, Multiverso Fantino, Troncal",
            youtubeHandle = "@neuramedia",
            logoUrl = "https://yt3.googleusercontent.com/neura=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_vorterix",
            name = "Vorterix",
            category = TvCategory.STREAMING,
            description = "Mario Pergolini, Maldición Va a Ser un Día Hermoso, Rock & Pop",
            youtubeHandle = "@vorterix",
            logoUrl = "https://yt3.googleusercontent.com/vorterix=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_lacasa",
            name = "La Casa Streaming",
            category = TvCategory.STREAMING,
            description = "Rumis, Lizardo Ponce, Zaira Nara, Sol Pérez",
            youtubeHandle = "@somoslacasaok",
            logoUrl = "https://yt3.googleusercontent.com/lacasa=s900-c-k-c0x00ffffff-no-rj",
        ),

        // === CANALES DE NOTICIAS & AIRE ===
        TvChannel(
            id = "tv_tn",
            name = "TN (Todo Noticias)",
            category = TvCategory.NEWS,
            description = "Noticias en vivo las 24 horas del día",
            youtubeHandle = "@todonoticias",
            logoUrl = "https://yt3.googleusercontent.com/tn=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_lanacion",
            name = "LN+ (La Nación+)",
            category = TvCategory.NEWS,
            description = "Actualidad, análisis político, debates y primicias",
            youtubeHandle = "@lanacion",
            logoUrl = "https://yt3.googleusercontent.com/lanacion=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_c5n",
            name = "C5N",
            category = TvCategory.NEWS,
            description = "La realidad en vivo, Minuto Uno, El Diario",
            youtubeHandle = "@c5n",
            logoUrl = "https://yt3.googleusercontent.com/c5n=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_cronica",
            name = "Crónica TV",
            category = TvCategory.NEWS,
            description = "Firme junto al pueblo con las placas rojas en vivo",
            youtubeHandle = "@cronica",
            logoUrl = "https://yt3.googleusercontent.com/cronica=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_a24",
            name = "A24",
            category = TvCategory.NEWS,
            description = "Noticias, móviles y análisis en vivo",
            youtubeHandle = "@A24com",
            logoUrl = "https://yt3.googleusercontent.com/a24=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_canal26",
            name = "Canal 26",
            category = TvCategory.NEWS,
            description = "Noticias internacionales y nacionales 24 horas",
            youtubeHandle = "@canal26",
            logoUrl = "https://yt3.googleusercontent.com/canal26=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_telefe",
            name = "Telefe Noticias",
            category = TvCategory.NEWS,
            description = "El noticiero más visto de la televisión abierta",
            youtubeHandle = "@telefenoticias",
            logoUrl = "https://yt3.googleusercontent.com/telefe=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_tvpublica",
            name = "TV Pública",
            category = TvCategory.NEWS,
            description = "Televisión Pública Argentina en vivo",
            youtubeHandle = "@TVPublicaArgentina",
            logoUrl = "https://yt3.googleusercontent.com/tvpublica=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_eltrece",
            name = "El Trece",
            category = TvCategory.NEWS,
            description = "Telenoche, Mediodía Noticias, eltrece en vivo",
            youtubeHandle = "@eltrece",
            logoUrl = "https://yt3.googleusercontent.com/eltrece=s900-c-k-c0x00ffffff-no-rj",
        ),
    )

    private val resolvedCache = mutableMapOf<String, Pair<String, Long>>()

    suspend fun resolveStreamUri(channel: TvChannel): String? = withContext(Dispatchers.IO) {
        // Direct HLS stream if available
        if (!channel.directStreamUrl.isNullOrBlank()) {
            return@withContext channel.directStreamUrl
        }

        val handle = channel.youtubeHandle ?: return@withContext null

        // Check 10-minute in-memory cache
        val cached = resolvedCache[handle]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.second) < 10 * 60 * 1000L) {
            return@withContext cached.first
        }

        val videoId = resolveLiveVideoId(handle)
        if (videoId != null) {
            resolvedCache[handle] = Pair(videoId, now)
            return@withContext videoId
        }

        null
    }

    suspend fun resolveLiveVideoId(handle: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanHandle = if (handle.startsWith("@")) handle else "@$handle"
            val urlStr = "https://www.youtube.com/$cleanHandle/live"
            val url = URL(urlStr)

            // Step 1: follow redirect or capture 303 location
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            val redirectLocation = conn.getHeaderField("Location")
            conn.disconnect()

            if (!redirectLocation.isNullOrBlank() && redirectLocation.contains("watch?v=")) {
                val videoId = redirectLocation.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
                if (videoId.length == 11) {
                    return@withContext videoId
                }
            }

            // Step 2: if no direct redirect, parse page HTML for videoId
            val conn2 = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            if (conn2.responseCode in 200..299) {
                val html = BufferedReader(InputStreamReader(conn2.inputStream)).use { it.readText() }
                conn2.disconnect()

                val canonicalMatch = Regex("canonical\"\\s+href=\"https://www\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})\"").find(html)
                    ?: Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(html)
                    ?: Regex("watch\\?v=([a-zA-Z0-9_-]{11})").find(html)

                return@withContext canonicalMatch?.groupValues?.get(1)
            }
            conn2.disconnect()
        } catch (e: Exception) {
            Timber.w(e, "TvChannelsRepository: error resolving live stream for $handle")
        }
        null
    }
}
