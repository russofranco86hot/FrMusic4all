/**
 * FrMusic4all Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.SpotifyTokenManager
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages Spotify Connect device discovery and playback transfer for Amazon Echo,
 * Alexa speaker groups, smart speakers, smartwatches, PCs, and other Spotify Connect targets.
 */
object SpotifyConnectManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<SpotifyDevice?>(null)
    val connectedDevice: StateFlow<SpotifyDevice?> = _connectedDevice.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<SpotifyDevice>>(emptyList())
    val availableDevices: StateFlow<List<SpotifyDevice>> = _availableDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private var cloudDevicesList = listOf<SpotifyDevice>()

    fun init(context: Context) {
        appContext = context.applicationContext

        // Reactively observe local ZeroConf mDNS devices (Amazon Echo, Alexa groups, etc.)
        scope.launch {
            SpotifyZeroConfDiscovery.discoveredDevices.collect { localMap ->
                val localDevices = localMap.values.map { it.device }
                mergeAndPublishDevices(cloudDevicesList, localDevices)
            }
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun mergeAndPublishDevices(cloud: List<SpotifyDevice>, local: List<SpotifyDevice>) {
        val mergedMap = LinkedHashMap<String, SpotifyDevice>()

        // 1. Add cloud devices first (these have official Spotify cloud IDs, required for playback)
        for (device in cloud) {
            val key = device.id ?: device.name
            mergedMap[key] = device
        }

        // 2. Add or enrich with locally discovered devices (Echoes in standby, etc.)
        for (localDevice in local) {
            val matchingCloudKey = mergedMap.entries.firstOrNull { (_, cloudDev) ->
                (localDevice.id != null && cloudDev.id == localDevice.id) ||
                (normalizeName(cloudDev.name) == normalizeName(localDevice.name)) ||
                (cloudDev.name.isNotBlank() && normalizeName(cloudDev.name).contains(normalizeName(localDevice.name))) ||
                (localDevice.name.isNotBlank() && normalizeName(localDevice.name).contains(normalizeName(cloudDev.name)))
            }?.key

            if (matchingCloudKey != null) {
                // If matched to cloud device, preserve cloud ID and friendly cloud name
                val existing = mergedMap[matchingCloudKey]!!
                mergedMap[matchingCloudKey] = existing.copy(
                    type = if (existing.type.isBlank()) localDevice.type else existing.type
                )
            } else {
                // Not in cloud list yet (e.g. Echo in standby)
                val key = localDevice.id ?: localDevice.name
                mergedMap[key] = localDevice
            }
        }

        val merged = mergedMap.values.toList()
        _availableDevices.value = merged

        val active = merged.firstOrNull { it.isActive }
        if (active != null) {
            _connectedDevice.value = active
            _isConnected.value = true
        }
    }

    fun scanDevices(context: Context? = null) {
        val targetContext = context?.applicationContext ?: appContext
        if (targetContext != null && appContext == null) {
            init(targetContext)
        }

        scope.launch {
            _isScanning.value = true
            try {
                val authed = SpotifyTokenManager.ensureAuthenticated()
                _isLoggedIn.value = authed
                if (!authed) {
                    _availableDevices.value = emptyList()
                    return@launch
                }

                // 1. Start local mDNS ZeroConf discovery for Echo / Alexa devices
                targetContext?.let {
                    SpotifyZeroConfDiscovery.startDiscovery(it)
                }

                // 2. Fetch cloud-registered devices from Spotify API
                Spotify.getAvailableDevices().fold(
                    onSuccess = { devices ->
                        cloudDevicesList = devices
                        val localDevices = SpotifyZeroConfDiscovery.discoveredDevices.value.values.map { it.device }
                        mergeAndPublishDevices(cloudDevicesList, localDevices)
                        Timber.d("SpotifyConnect: discovered ${devices.size} cloud devices, ${localDevices.size} local devices")
                    },
                    onFailure = { err ->
                        Timber.e(err, "SpotifyConnect: failed to get cloud devices")
                        val localDevices = SpotifyZeroConfDiscovery.discoveredDevices.value.values.map { it.device }
                        mergeAndPublishDevices(emptyList(), localDevices)
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "SpotifyConnect: error during scan")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun connectToDevice(
        device: SpotifyDevice,
        currentMedia: MediaMetadata?,
        onLocalPause: (() -> Unit)? = null,
        onSuccess: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        val initialDeviceId = device.id ?: return
        scope.launch {
            try {
                val authed = SpotifyTokenManager.ensureAuthenticated()
                if (!authed) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("No has iniciado sesión en Spotify")
                    }
                    return@launch
                }

                // 1. Wake up the Echo via local ZeroConf probe
                SpotifyZeroConfDiscovery.wakeUpDevice(initialDeviceId, device.name)

                // 2. Poll Spotify cloud devices to resolve the canonical cloud device ID
                var targetDeviceId = initialDeviceId
                var resolvedDevice = device

                for (attempt in 1..5) {
                    delay(500)
                    val cloudDevices = Spotify.getAvailableDevices().getOrDefault(emptyList())
                    val matched = cloudDevices.firstOrNull { cloud ->
                        cloud.id == initialDeviceId ||
                        normalizeName(cloud.name) == normalizeName(device.name) ||
                        (device.name.isNotBlank() && normalizeName(cloud.name).contains(normalizeName(device.name))) ||
                        (cloud.name.isNotBlank() && normalizeName(device.name).contains(normalizeName(cloud.name)))
                    }
                    if (matched != null && !matched.id.isNullOrBlank()) {
                        targetDeviceId = matched.id
                        resolvedDevice = matched
                        Timber.d("SpotifyConnect: resolved cloud device_id=${matched.id} (${matched.name}) on attempt $attempt")
                        break
                    }
                }

                // 3. Resolve track URI if music is currently playing
                if (currentMedia?.id?.startsWith("radio:") == true) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Las radios en vivo se transmiten a tu Echo o Reloj por Bluetooth. Di «Alexa, conecta mi teléfono».")
                    }
                    return@launch
                }

                var trackUri: String? = null
                if (currentMedia != null) {
                    val registered = SpotifyMetadataRegistry.get(currentMedia.id)
                    if (registered != null) {
                        trackUri = "spotify:track:${registered.id}"
                    } else {
                        // Quick lookup by track title and artist
                        val query = "${currentMedia.title} ${currentMedia.artists.firstOrNull()?.name.orEmpty()}".trim()
                        if (query.isNotEmpty()) {
                            val searchResult = Spotify.search(query, listOf("track"), limit = 1).getOrNull()
                            val foundTrack = searchResult?.tracks?.items?.firstOrNull()
                            if (foundTrack != null) {
                                trackUri = "spotify:track:${foundTrack.id}"
                            }
                        }
                    }
                }

                val finalTrackUri = trackUri
                Timber.d("SpotifyConnect: transferring playback to device ${resolvedDevice.name} [id=$targetDeviceId]")

                // Transfer playback to wake up and activate target device
                val transferResult = Spotify.transferPlayback(targetDeviceId, play = (finalTrackUri == null))

                val result = if (finalTrackUri != null) {
                    delay(300)
                    Timber.d("SpotifyConnect: playing $finalTrackUri on device ${resolvedDevice.name}")
                    val playRes = Spotify.playTrackOnDevice(targetDeviceId, finalTrackUri)
                    if (playRes.isFailure) {
                        Timber.w("SpotifyConnect: playTrackOnDevice failed, checking transfer result: ${playRes.exceptionOrNull()?.message}")
                    }
                    playRes
                } else {
                    transferResult
                }

                result.fold(
                    onSuccess = {
                        _connectedDevice.value = resolvedDevice
                        _isConnected.value = true
                        withContext(Dispatchers.Main) {
                            onLocalPause?.invoke()
                            onSuccess?.invoke(resolvedDevice.name)
                        }
                        Timber.d("SpotifyConnect: successfully connected to ${resolvedDevice.name}")
                    },
                    onFailure = { err ->
                        Timber.e(err, "SpotifyConnect: transfer failed: ${err.message}")
                        val errorMsg = when {
                            err is Spotify.SpotifyException && err.statusCode == 403 ->
                                "Spotify requiere una cuenta Premium para controlar la reproducción en altavoces"
                            err is Spotify.SpotifyException && err.statusCode == 404 ->
                                "El dispositivo no respondió a tiempo. Prueba diciendo «Alexa, conecta Spotify»"
                            else ->
                                "No se pudo conectar a ${resolvedDevice.name}: ${err.message ?: "error desconocido"}"
                        }
                        withContext(Dispatchers.Main) {
                            onError?.invoke(errorMsg)
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "SpotifyConnect: connection error")
                withContext(Dispatchers.Main) {
                    onError?.invoke("Error de conexión: ${e.message}")
                }
            }
        }
    }

    fun disconnect(onResumeLocal: (() -> Unit)? = null) {
        scope.launch {
            try {
                Spotify.pausePlayback()
            } catch (e: Exception) {
                Timber.w(e, "SpotifyConnect: failed to pause remote playback")
            } finally {
                _connectedDevice.value = null
                _isConnected.value = false
                onResumeLocal?.invoke()
            }
        }
    }
}
