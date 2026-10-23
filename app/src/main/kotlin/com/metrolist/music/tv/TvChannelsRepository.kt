/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.tv

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
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
import java.net.URLEncoder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TvChannelsRepository(private val context: Context) {
    private val favoriteChannelsKey = stringSetPreferencesKey("favorite_tv_channels")
    private val customChannelsFile = File(context.filesDir, "custom_tv_channels.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; prettyPrint = true }

    private val _customChannels = MutableStateFlow<List<TvChannel>>(emptyList())
    val customChannels: StateFlow<List<TvChannel>> = _customChannels.asStateFlow()

    init {
        loadCustomChannels()
    }

    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[favoriteChannelsKey] ?: emptySet()
    }

    fun loadCustomChannels() {
        if (customChannelsFile.exists()) {
            try {
                val text = customChannelsFile.readText()
                val list = json.decodeFromString<List<TvChannel>>(text)
                _customChannels.value = list
            } catch (e: Exception) {
                Timber.e(e, "Error loading custom TV channels")
            }
        }
    }

    suspend fun addCustomChannel(channel: TvChannel) = withContext(Dispatchers.IO) {
        val current = _customChannels.value.filter { it.id != channel.id }.toMutableList()
        current.add(0, channel)
        try {
            val text = json.encodeToString(current)
            customChannelsFile.writeText(text)
            _customChannels.value = current
        } catch (e: Exception) {
            Timber.e(e, "Error saving custom TV channel")
        }
    }

    suspend fun removeCustomChannel(channelId: String) = withContext(Dispatchers.IO) {
        val current = _customChannels.value.filter { it.id != channelId }
        try {
            val text = json.encodeToString(current)
            customChannelsFile.writeText(text)
            _customChannels.value = current
        } catch (e: Exception) {
            Timber.e(e, "Error removing custom TV channel")
        }
    }

    fun getAllChannels(): List<TvChannel> {
        return _customChannels.value + getDefaultChannels()
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
        // === CANALES DE STREAMING (news.net.ar & Argentina) ===
        TvChannel(
            id = "tv_luzu",
            name = "LUZU TV",
            category = TvCategory.STREAMING,
            description = "Nadie Dice Nada, Antes Que Nadie, Patria y Familia, Tarde Para Nada",
            youtubeHandle = "@luzutv",
            channelId = "UCTHaNTsP7hsVgBxARZTuajw",
            fallbackVideoId = "UoSHrgK3LaQ",
            logoUrl = "https://yt3.googleusercontent.com/4qKjBqFw_iYkC4q1YjKq3kG9qN9n5vE=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_olga",
            name = "OLGA",
            category = TvCategory.STREAMING,
            description = "Migue Granados, Soñé Que Volaba, Sería Increíble, Tapados de Laburo",
            youtubeHandle = "@olgaenvivo_",
            channelId = "UC7mJ2EDXFomeDIRFu5FtEbA",
            fallbackVideoId = "HmPYnPYiIrI",
            logoUrl = "https://yt3.googleusercontent.com/9nF_z5lX4Bq1v2q7b=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_urbana",
            name = "Urbana Play 104.3",
            category = TvCategory.STREAMING,
            description = "Andy Kusnetzoff, Matías Martin, Sebastián Wainraich, María O'Donnell",
            youtubeHandle = "@UrbanaPlayFM",
            fallbackVideoId = "pKQr75PfCNE",
            logoUrl = "https://urbanaplayfm.com/wp-content/uploads/2021/01/logo-urbana-play-104-3.png",
        ),
        TvChannel(
            id = "tv_blender",
            name = "Blender",
            category = TvCategory.STREAMING,
            description = "Hay Algo Ahí, Escucho Ofertas, Dinastía, Periodismo Puro",
            youtubeHandle = "@somosblender",
            fallbackVideoId = "YHkCxXWeTx8",
            logoUrl = "https://yt3.googleusercontent.com/4yZ3q9nF=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_gelatina",
            name = "Gelatina",
            category = TvCategory.STREAMING,
            description = "Pedro Rosemblat, Fábrica de Junkies, Gelatina en Vivo",
            youtubeHandle = "@gelatina",
            fallbackVideoId = "UL5qiPazNB0",
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
            id = "tv_vorterix",
            name = "Vorterix",
            category = TvCategory.STREAMING,
            description = "Mario Pergolini, Maldición Va a Ser un Día Hermoso, Rock & Pop",
            youtubeHandle = "@vorterix",
            logoUrl = "https://yt3.googleusercontent.com/vorterix=s900-c-k-c0x00ffffff-no-rj",
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
            id = "tv_carajo",
            name = "Carajo Stream",
            category = TvCategory.STREAMING,
            description = "Daniel Parisini, La Misa, Toda, stream libertario",
            youtubeHandle = "@carajostream",
            fallbackVideoId = "WAMgW8A_4A8",
            logoUrl = "https://yt3.googleusercontent.com/carajo=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_cenital",
            name = "Cenital",
            category = TvCategory.STREAMING,
            description = "Periodismo independiente, streaming y política",
            youtubeHandle = "@cenitalcom",
            fallbackVideoId = "ySeQ8bdkzmM",
            logoUrl = "https://yt3.googleusercontent.com/cenital=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_futurock",
            name = "FutuRock",
            category = TvCategory.STREAMING,
            description = "Julia Mengolini, Mal Aconsejados, Segurola",
            youtubeHandle = "@futurockok",
            fallbackVideoId = "whFZzHcs3qQ",
            logoUrl = "https://yt3.googleusercontent.com/futurock=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_azzaro",
            name = "AZZaro Stream",
            category = TvCategory.STREAMING,
            description = "Flavio Azzaro, El Loco y el Cuerdo, fútbol y debate",
            youtubeHandle = "@FlavioAzzaro",
            fallbackVideoId = "ox_ZnKfYfyo",
            logoUrl = "https://yt3.googleusercontent.com/azzaro=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_lacasa",
            name = "La Casa Streaming",
            category = TvCategory.STREAMING,
            description = "Rumis, Lizardo Ponce, Zaira Nara, Sol Pérez",
            youtubeHandle = "@somoslacasaok",
            channelId = "UC4u0BhsSi33PS20_1JHiC5A",
            logoUrl = "https://yt3.googleusercontent.com/lacasa=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_republicaz",
            name = "República Z",
            category = TvCategory.STREAMING,
            description = "Canal de streaming con programas de entretenimiento y actualidad",
            youtubeHandle = "@republicaz",
            channelId = "UCmk9kjghfkM3k8I6jioDr3g",
            logoUrl = "https://yt3.googleusercontent.com/republicaz=s900-c-k-c0x00ffffff-no-rj",
        ),

        // === CANALES DE NOTICIAS & AIRE (news.net.ar) ===
        TvChannel(
            id = "tv_tn",
            name = "TN (Todo Noticias)",
            category = TvCategory.NEWS,
            description = "Noticias en vivo las 24 horas del día",
            youtubeHandle = "@todonoticias",
            fallbackVideoId = "cb12KmMMDJA",
            directStreamUrl = "https://live-01-01-tn.vodgc.net/TN_DAI_Beta/index.m3u8",
            logoUrl = "https://yt3.googleusercontent.com/tn=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_c5n",
            name = "C5N",
            category = TvCategory.NEWS,
            description = "La realidad en vivo, Minuto Uno, El Diario",
            youtubeHandle = "@c5n",
            fallbackVideoId = "j6oh4Kqz3UM",
            logoUrl = "https://yt3.googleusercontent.com/c5n=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_lanacion",
            name = "LN+ (La Nación+)",
            category = TvCategory.NEWS,
            description = "Actualidad, análisis político, debates y primicias",
            youtubeHandle = "@lanacion",
            fallbackVideoId = "FEWZjXJ7M0c",
            logoUrl = "https://yt3.googleusercontent.com/lanacion=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_cronica",
            name = "Crónica TV",
            category = TvCategory.NEWS,
            description = "Firme junto al pueblo con las placas rojas en vivo",
            youtubeHandle = "@cronica",
            fallbackVideoId = "hw4uHyct4vg",
            logoUrl = "https://yt3.googleusercontent.com/cronica=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_a24",
            name = "A24",
            category = TvCategory.NEWS,
            description = "Noticias, móviles y análisis en vivo",
            youtubeHandle = "@A24com",
            directStreamUrl = "https://vmf.edge-apps.net/embed/live.php?streamname=a24-100056&autoplay=true&mute=true",
            logoUrl = "https://yt3.googleusercontent.com/a24=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_canal26",
            name = "Canal 26",
            category = TvCategory.NEWS,
            description = "Noticias internacionales y nacionales 24 horas",
            youtubeHandle = "@canal26",
            fallbackVideoId = "C_RnFD6xiX8",
            logoUrl = "https://yt3.googleusercontent.com/canal26=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_telefe",
            name = "Telefe Noticias",
            category = TvCategory.NEWS,
            description = "El noticiero más visto de la televisión abierta",
            youtubeHandle = "@telefenoticias",
            fallbackVideoId = "XhAYcYpPzTc",
            logoUrl = "https://yt3.googleusercontent.com/telefe=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_eltrece",
            name = "El Trece",
            category = TvCategory.NEWS,
            description = "Telenoche, Mediodía Noticias, eltrece en vivo",
            youtubeHandle = "@eltrece",
            fallbackVideoId = "UVNvBIEKpBs",
            logoUrl = "https://yt3.googleusercontent.com/eltrece=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_tvpublica",
            name = "TV Pública",
            category = TvCategory.NEWS,
            description = "Televisión Pública Argentina en vivo",
            youtubeHandle = "@TVPublicaArgentina",
            fallbackVideoId = "3cPk5XFtl_w",
            logoUrl = "https://yt3.googleusercontent.com/tvpublica=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_america2",
            name = "América 2 Internacional",
            category = TvCategory.NEWS,
            description = "Señal en vivo de América TV",
            youtubeHandle = "@AmericaTV",
            fallbackVideoId = "zcWXboTnous",
            logoUrl = "https://yt3.googleusercontent.com/americatv=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_canaldelaciudad",
            name = "Canal de la Ciudad",
            category = TvCategory.NEWS,
            description = "Canal público de la Ciudad de Buenos Aires",
            youtubeHandle = "@CanaldelaCiudad",
            fallbackVideoId = "VHCnqfdX-t0",
            logoUrl = "https://yt3.googleusercontent.com/canaldelaciudad=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_infobae",
            name = "Infobae en Vivo",
            category = TvCategory.NEWS,
            description = "Entrevistas, móviles e información minuto a minuto",
            youtubeHandle = "@Infobae",
            fallbackVideoId = "SD9AsLucZLg",
            logoUrl = "https://yt3.googleusercontent.com/infobae=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_cadena3",
            name = "Cadena 3 Argentina",
            category = TvCategory.NEWS,
            description = "Transmisión audiovisual de Cadena 3",
            youtubeHandle = "@Cadena3Argentina",
            fallbackVideoId = "Hi06OnRcwDY",
            logoUrl = "https://yt3.googleusercontent.com/cadena3=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_canalrural",
            name = "Canal Rural",
            category = TvCategory.NEWS,
            description = "El canal del campo, la producción y los mercados",
            youtubeHandle = "@canalrural",
            fallbackVideoId = "EED_GN6fuMQ",
            logoUrl = "https://yt3.googleusercontent.com/canalrural=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_diputados",
            name = "Diputados TV",
            category = TvCategory.NEWS,
            description = "Transmisiones en vivo de la Cámara de Diputados de la Nación",
            youtubeHandle = "@DiputadosTVArgentina",
            fallbackVideoId = "GlS1fG8VhUg",
            logoUrl = "https://yt3.googleusercontent.com/diputadostv=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_senado",
            name = "Senado TV",
            category = TvCategory.NEWS,
            description = "Sesiones y comisiones del Senado de la Nación",
            youtubeHandle = "@SenadoTVArgentina",
            fallbackVideoId = "zFJitU6vBE0",
            logoUrl = "https://yt3.googleusercontent.com/senadotv=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_encuentro",
            name = "Canal Encuentro",
            category = TvCategory.NEWS,
            description = "Canal educativo y cultural del Estado argentino",
            youtubeHandle = "@CanalEncuentro",
            fallbackVideoId = "xFbrQRratWc",
            logoUrl = "https://yt3.googleusercontent.com/encuentro=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_pakapaka",
            name = "PakaPaka",
            category = TvCategory.NEWS,
            description = "El canal infantil público argentino",
            youtubeHandle = "@CanalPakapaka",
            fallbackVideoId = "J_OahpT0aDQ",
            logoUrl = "https://yt3.googleusercontent.com/pakapaka=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_deportv",
            name = "DeporTV",
            category = TvCategory.NEWS,
            description = "El canal público de deportes de Argentina",
            youtubeHandle = "@canaldeportv",
            fallbackVideoId = "pvaxvxIw6zI",
            logoUrl = "https://yt3.googleusercontent.com/deportv=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_dw",
            name = "Deutsche Welle Español",
            category = TvCategory.NEWS,
            description = "Noticias de Alemania y el mundo en español",
            youtubeHandle = "@dwespanol",
            fallbackVideoId = "jRnqxURJ120",
            logoUrl = "https://yt3.googleusercontent.com/dw=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_france24",
            name = "France 24 Español",
            category = TvCategory.NEWS,
            description = "Noticias internacionales 24 horas",
            youtubeHandle = "@france24espanol",
            fallbackVideoId = "l8PMl7tUDIE",
            logoUrl = "https://yt3.googleusercontent.com/france24=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_rtve",
            name = "RTVE Noticias (España)",
            category = TvCategory.NEWS,
            description = "Canal 24 horas de Radio Televisión Española",
            youtubeHandle = "@rtvenoticias",
            fallbackVideoId = "ku09JnYKhz0",
            logoUrl = "https://yt3.googleusercontent.com/rtve=s900-c-k-c0x00ffffff-no-rj",
        ),

        // === RADIOS CON ESTUDIO / CÁMARA (news.net.ar) ===
        TvChannel(
            id = "tv_mitre",
            name = "Radio Mitre AM 790",
            category = TvCategory.NEWS,
            description = "Estudio en vivo con cámara de Radio Mitre",
            channelId = "UCYvINPByAdCcpA0sWrF3I_w",
            logoUrl = "https://yt3.googleusercontent.com/mitre=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_lared",
            name = "Radio La Red AM 910",
            category = TvCategory.NEWS,
            description = "Estudio en vivo de Radio La Red",
            channelId = "UCs1U5lRXvUJHn0CgXI6oXAw",
            logoUrl = "https://yt3.googleusercontent.com/lared=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_radioconvos",
            name = "Radio con Vos 89.9",
            category = TvCategory.NEWS,
            description = "Reynaldo Sietecase, Ernesto Tenembaum, Alejandro Bercovich",
            channelId = "UCxDteokWBemJvLI_I0VUGdA",
            logoUrl = "https://yt3.googleusercontent.com/radioconvos=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_radio10",
            name = "Radio 10",
            category = TvCategory.NEWS,
            description = "Gustavo Sylvestre, Jorge Rial, Baby Etchecopar",
            channelId = "UCJozD5RVug7EZdTjqkGISYQ",
            logoUrl = "https://yt3.googleusercontent.com/radio10=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_la100",
            name = "La 100 FM 99.9",
            category = TvCategory.MUSIC,
            description = "Santiago del Moro, Guido Kaczka, Maju Lozano",
            channelId = "UC2zO2jZCDplwm8SVsXUcxCQ",
            logoUrl = "https://yt3.googleusercontent.com/la100=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_pop",
            name = "Pop Radio 101.5",
            category = TvCategory.MUSIC,
            description = "Beto Casella, La Negra Vernaci, Ronnie Arias",
            channelId = "UCtc_WjDRwCrZSEq2WEwD7XQ",
            logoUrl = "https://yt3.googleusercontent.com/pop=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_vale",
            name = "Vale 97.5",
            category = TvCategory.MUSIC,
            description = "Música romántica y latina en vivo",
            channelId = "UC1Oa7gHuqzGHB90FPb0ssPA",
            logoUrl = "https://yt3.googleusercontent.com/vale=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_mega",
            name = "Mega 98.3",
            category = TvCategory.MUSIC,
            description = "Puro Rock Nacional con cámara en estudio",
            channelId = "UCtloZ7xFNO9O114pkoV9a5g",
            logoUrl = "https://yt3.googleusercontent.com/mega=s900-c-k-c0x00ffffff-no-rj",
        ),
        TvChannel(
            id = "tv_quiero",
            name = "Quiero Música en mi Idioma",
            category = TvCategory.MUSIC,
            description = "Canal de videos y música latina",
            fallbackVideoId = "8Mw0YdKN2XY",
            logoUrl = "https://yt3.googleusercontent.com/quiero=s900-c-k-c0x00ffffff-no-rj",
        ),
    )

    private val resolvedCache = mutableMapOf<String, Pair<String, Long>>()

    suspend fun resolveStreamUri(channel: TvChannel): String? = withContext(Dispatchers.IO) {
        // 1. Direct HLS stream if available (.m3u8 or /hls/)
        if (!channel.directStreamUrl.isNullOrBlank() && 
            (channel.directStreamUrl.contains(".m3u8", ignoreCase = true) || channel.directStreamUrl.contains("/hls/", ignoreCase = true))) {
            return@withContext channel.directStreamUrl
        }

        // 2. Direct YouTube video or YouTube Live search result
        val directUrl = channel.directStreamUrl
        if (!directUrl.isNullOrBlank() && directUrl.contains("watch?v=")) {
            val vId = directUrl.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
            if (vId.length == 11) {
                val hls = resolveHlsManifestUrl(vId)
                if (hls != null) return@withContext hls
            }
        }
        if (channel.id.startsWith("yt_live_")) {
            val vId = channel.id.removePrefix("yt_live_")
            if (vId.length == 11) {
                val hls = resolveHlsManifestUrl(vId)
                if (hls != null) return@withContext hls
            }
        }

        val cacheKey = channel.youtubeHandle ?: channel.channelId ?: channel.id

        // Check 5-minute in-memory cache
        val cached = resolvedCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.second) < 5 * 60 * 1000L) {
            return@withContext cached.first
        }

        val handle = channel.youtubeHandle
        var videoId: String? = null

        // 3. Try live stream resolution via YouTube handle or channel ID
        if (!handle.isNullOrBlank()) {
            videoId = resolveLiveVideoId(handle)
        }
        if (videoId == null && !channel.channelId.isNullOrBlank()) {
            videoId = resolveLiveVideoId(channel.channelId)
        }
        if (videoId == null && !channel.fallbackVideoId.isNullOrBlank()) {
            videoId = channel.fallbackVideoId
        }

        if (videoId != null && videoId.length == 11) {
            val hlsUrl = resolveHlsManifestUrl(videoId)
            val streamUrlToUse = hlsUrl ?: "https://www.youtube.com/watch?v=$videoId"
            resolvedCache[cacheKey] = Pair(streamUrlToUse, now)
            return@withContext streamUrlToUse
        }

        // 4. Fallback to direct stream URL if provided
        if (!channel.directStreamUrl.isNullOrBlank()) {
            return@withContext channel.directStreamUrl
        }

        null
    }

    companion object {
        suspend fun resolveHlsManifestUrl(videoId: String): String? = withContext(Dispatchers.IO) {
            // 1. Try InnerTube PlayerResponse
            try {
                val response = YouTube.player(videoId, client = YouTubeClient.WEB).getOrNull()
                    ?: YouTube.player(videoId, client = YouTubeClient.ANDROID_MUSIC).getOrNull()
                val manifest = response?.streamingData?.hlsManifestUrl
                if (!manifest.isNullOrBlank()) {
                    return@withContext manifest
                }
            } catch (e: Exception) {
                Timber.w(e, "TvChannelsRepository: error getting player response for $videoId")
            }

            // 2. Try scraping ytInitialPlayerResponse from watch page
            try {
                val url = URL("https://www.youtube.com/watch?v=$videoId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                if (conn.responseCode in 200..299) {
                    val html = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val match = Regex("\"hlsManifestUrl\"\\s*:\\s*\"([^\"]+)\"").find(html)
                        ?: Regex("\"hlsManifestUrl\":\"(https:[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"").find(html)
                    if (match != null) {
                        val rawUrl = match.groupValues[1]
                        val cleanUrl = rawUrl.replace("\\/", "/").replace("\\u0026", "&")
                        return@withContext cleanUrl
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Timber.w(e, "TvChannelsRepository: error scraping hlsManifestUrl for $videoId")
            }

            null
        }
    }

    suspend fun resolveLiveVideoId(handleOrChannelId: String): String? = withContext(Dispatchers.IO) {
        try {
            val urlStr = when {
                handleOrChannelId.startsWith("UC") -> "https://www.youtube.com/channel/$handleOrChannelId/live"
                handleOrChannelId.startsWith("@") -> "https://www.youtube.com/$handleOrChannelId/live"
                else -> "https://www.youtube.com/@$handleOrChannelId/live"
            }
            val url = URL(urlStr)

            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Accept-Language", "es-419,es;q=0.9,en;q=0.8")
            }

            if (conn.responseCode in 200..299) {
                val finalUrl = conn.url.toString()
                val html = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                conn.disconnect()

                var videoId: String? = null
                if (finalUrl.contains("watch?v=")) {
                    videoId = finalUrl.substringAfter("watch?v=").substringBefore("&").substringBefore("?")
                }

                if (videoId == null) {
                    val canonicalMatch = Regex("canonical\"\\s+href=\"https://www\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})\"").find(html)
                    videoId = canonicalMatch?.groupValues?.get(1)
                }

                if (videoId != null && videoId.length == 11) {
                    val isLiveNow = html.contains("\"isLiveNow\":true") ||
                        html.contains("\"isLive\":true") ||
                        html.contains("isLiveBroadcast\" content=\"True\"") ||
                        html.contains("\"status\":\"LIVE\"") ||
                        html.contains("BADGE_STYLE_TYPE_LIVE_NOW") ||
                        html.contains("liveStreamability") ||
                        html.contains("liveBroadcastDetails")
                    val isUpcoming = html.contains("upcomingEventData") && !html.contains("\"isLiveNow\":true")

                    if (isLiveNow || !isUpcoming) {
                        return@withContext videoId
                    } else {
                        Timber.d("TvChannelsRepository: $handleOrChannelId has video $videoId but is upcoming")
                        return@withContext null
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "TvChannelsRepository: error resolving live stream for $handleOrChannelId")
        }
        null
    }

    suspend fun searchLiveStreamsFromYouTube(query: String): List<TvChannel> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.youtube.com/results?search_query=$encoded&sp=CAM%3D")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Accept-Language", "es-419,es;q=0.9,en;q=0.8")
            }
            if (conn.responseCode in 200..299) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val results = mutableListOf<TvChannel>()
                val seenIds = mutableSetOf<String>()

                val dataMatch = Regex("var ytInitialData = (\\{.*?\\});</script>").find(html)
                    ?: Regex("ytInitialData\\s*=\\s*(\\{.+?\\});").find(html)

                if (dataMatch != null) {
                    val jsonStr = dataMatch.groupValues[1]
                    val rendererRegex = Regex("\"videoRenderer\":\\s*\\{(.+?)(?:,\"navigationEndpoint\"|,\"viewCountText\")")
                    for (match in rendererRegex.findAll(jsonStr)) {
                        val block = match.groupValues[1]
                        val vId = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(block)?.groupValues?.get(1) ?: continue
                        if (seenIds.add(vId)) {
                            val title = Regex("\"title\":\\s*\\{[^}]*?\"text\":\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                                ?: Regex("\"title\":\\s*\\{\\s*\"runs\":\\s*\\[\\s*\\{\\s*\"text\":\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                                ?: "En Vivo"
                            val channelName = Regex("\"ownerText\":\\s*\\{\\s*\"runs\":\\s*\\[\\s*\\{\\s*\"text\":\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                                ?: Regex("\"longBylineText\":\\s*\\{\\s*\"runs\":\\s*\\[\\s*\\{\\s*\"text\":\\s*\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                                ?: ""
                            val thumb = "https://i.ytimg.com/vi/$vId/hqdefault.jpg"

                            results.add(
                                TvChannel(
                                    id = "yt_live_$vId",
                                    name = if (channelName.isNotBlank()) "$channelName - $title" else title,
                                    category = TvCategory.STREAMING,
                                    description = "En vivo en YouTube • $channelName",
                                    logoUrl = thumb,
                                    directStreamUrl = "https://www.youtube.com/watch?v=$vId"
                                )
                            )
                        }
                    }
                }
                return@withContext results
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "TvChannelsRepository: error searching live streams for $query")
        }
        emptyList()
    }
}
