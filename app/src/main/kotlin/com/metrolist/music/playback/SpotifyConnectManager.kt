/**
 * FrMusic4all Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.SpotifyTokenManager
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    fun scanDevices() {
        scope.launch {
            _isScanning.value = true
            try {
                val authed = SpotifyTokenManager.ensureAuthenticated()
                _isLoggedIn.value = authed
                if (!authed) {
                    _availableDevices.value = emptyList()
                    return@launch
                }

                Spotify.getAvailableDevices().fold(
                    onSuccess = { devices ->
                        _availableDevices.value = devices
                        val active = devices.firstOrNull { it.isActive }
                        if (active != null) {
                            _connectedDevice.value = active
                            _isConnected.value = true
                        }
                        Timber.d("SpotifyConnect: discovered ${devices.size} devices")
                    },
                    onFailure = { err ->
                        Timber.e(err, "SpotifyConnect: failed to get devices")
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
                val result = if (finalTrackUri != null) {
                    Timber.d("SpotifyConnect: playing $finalTrackUri on device ${device.name}")
                    Spotify.playTrackOnDevice(deviceId, finalTrackUri)
                } else {
                    Timber.d("SpotifyConnect: transferring playback to device ${device.name}")
                    Spotify.transferPlayback(deviceId, play = true)
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
