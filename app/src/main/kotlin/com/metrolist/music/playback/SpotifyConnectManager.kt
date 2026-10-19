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

    private fun mergeAndPublishDevices(cloud: List<SpotifyDevice>, local: List<SpotifyDevice>) {
        val mergedMap = LinkedHashMap<String, SpotifyDevice>()

        // Add cloud devices first
        for (device in cloud) {
            val key = device.id ?: device.name
            mergedMap[key] = device
        }

        // Add or enrich with locally discovered devices (Echoes in standby, etc.)
        for (localDevice in local) {
            val key = localDevice.id ?: localDevice.name
            val existing = mergedMap[key]
            if (existing != null) {
                // If it already exists in cloud list, prefer existing or keep active state
                mergedMap[key] = existing.copy(
                    name = if (localDevice.name.contains("Echo", ignoreCase = true)) localDevice.name else existing.name,
                    type = localDevice.type,
                )
            } else {
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
    ) {
        val deviceId = device.id ?: return
        scope.launch {
            try {
                val authed = SpotifyTokenManager.ensureAuthenticated()
                if (!authed) return@launch

                // If device was discovered via ZeroConf, wake it up locally first
                SpotifyZeroConfDiscovery.wakeUpDevice(deviceId)
                delay(300)

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
                Timber.d("SpotifyConnect: transferring playback to device ${device.name} [id=$deviceId]")

                // Transfer playback to wake up and activate target device
                val transferResult = Spotify.transferPlayback(deviceId, play = (finalTrackUri == null))

                val result = if (finalTrackUri != null) {
                    delay(250)
                    Timber.d("SpotifyConnect: playing $finalTrackUri on device ${device.name}")
                    Spotify.playTrackOnDevice(deviceId, finalTrackUri)
                } else {
                    transferResult
                }

                result.fold(
                    onSuccess = {
                        _connectedDevice.value = device
                        _isConnected.value = true
                        onLocalPause?.invoke()
                        Timber.d("SpotifyConnect: successfully connected to ${device.name}")
                    },
                    onFailure = { err ->
                        Timber.e(err, "SpotifyConnect: transfer failed")
                        // If playTrackOnDevice failed, try fallback transferPlayback
                        if (finalTrackUri != null && transferResult.isSuccess) {
                            _connectedDevice.value = device
                            _isConnected.value = true
                            onLocalPause?.invoke()
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "SpotifyConnect: connection error")
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
