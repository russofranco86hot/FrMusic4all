/**
 * FrMusic4all Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.metrolist.spotify.models.SpotifyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class DiscoveredZeroConfDevice(
    val device: SpotifyDevice,
    val ip: String,
    val port: Int,
    val cpath: String,
    val activeUser: String?,
)

/**
 * Discovers local Spotify Connect devices on the local Wi-Fi network using mDNS (DNS-SD).
 * This is how Amazon Echo devices, Alexa speaker groups, and other Spotify Connect hardware
 * are discovered even when in sleep or standby mode.
 */
object SpotifyZeroConfDiscovery {
    private const val SERVICE_TYPE = "_spotify-connect._tcp"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredZeroConfDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, DiscoveredZeroConfDevice>> = _discoveredDevices.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var queueWorkerJob: Job? = null

    fun startDiscovery(context: Context) {
        val appContext = context.applicationContext
        nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            Timber.w("SpotifyZeroConf: NsdManager not available")
            return
        }

        // Acquire MulticastLock so the Android Wi-Fi driver does not filter mDNS packets
        try {
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("SpotifyZeroConfMulticastLock")?.apply {
                    setReferenceCounted(true)
                }
            }
            if (multicastLock?.isHeld != true) {
                multicastLock?.acquire()
                Timber.d("SpotifyZeroConf: MulticastLock acquired")
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyZeroConf: failed to acquire MulticastLock")
        }

        startQueueWorker()

        if (isDiscovering) {
            Timber.d("SpotifyZeroConf: discovery already in progress")
            return
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Timber.d("SpotifyZeroConf: discovery started for $regType")
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Timber.d("SpotifyZeroConf: found service: ${service.serviceName} (${service.serviceType})")
                if (service.serviceType.contains("_spotify-connect")) {
                    resolveQueue.trySend(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Timber.d("SpotifyZeroConf: lost service: ${service.serviceName}")
                val current = _discoveredDevices.value.toMutableMap()
                val removed = current.values.firstOrNull { it.device.name == service.serviceName }
                if (removed != null && removed.device.id != null) {
                    current.remove(removed.device.id)
                    _discoveredDevices.value = current
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.d("SpotifyZeroConf: discovery stopped for $serviceType")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("SpotifyZeroConf: start discovery failed: $errorCode")
                isDiscovering = false
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("SpotifyZeroConf: stop discovery failed: $errorCode")
                isDiscovering = false
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Timber.e(e, "SpotifyZeroConf: error starting service discovery")
            isDiscovering = false
        }
    }

    private fun startQueueWorker() {
        if (queueWorkerJob?.isActive == true) return
        queueWorkerJob = scope.launch {
            for (service in resolveQueue) {
                try {
                    val resolved = resolveServiceSafely(service)
                    if (resolved != null) {
                        inspectResolvedService(resolved)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "SpotifyZeroConf: error resolving service ${service.serviceName}")
                }
                delay(200) // Breather between sequential resolves to prevent Android NsdManager crashes
            }
        }
    }

    private suspend fun resolveServiceSafely(service: NsdServiceInfo): NsdServiceInfo? {
        val manager = nsdManager ?: return null
        return kotlinx.coroutines.withTimeoutOrNull(3000) {
            suspendCoroutine { cont ->
                try {
                    manager.resolveService(
                        service,
                        object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                cont.resume(resolvedInfo)
                            }

                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Timber.w("SpotifyZeroConf: resolve failed for ${serviceInfo.serviceName}: $errorCode")
                                cont.resume(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Timber.w(e, "SpotifyZeroConf: resolveService invocation error")
                    cont.resume(null)
                }
            }
        }
    }

    private suspend fun inspectResolvedService(resolvedInfo: NsdServiceInfo) = withContext(Dispatchers.IO) {
        val host = resolvedInfo.host?.hostAddress ?: return@withContext
        val port = resolvedInfo.port
        if (port <= 0) return@withContext

        // Read TXT attributes
        var cpath = "/"
        try {
            val attributes = resolvedInfo.attributes
            val cpathBytes = attributes["CPath"]
            if (cpathBytes != null) {
                cpath = String(cpathBytes, Charsets.UTF_8).trim()
                if (!cpath.startsWith("/")) cpath = "/$cpath"
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyZeroConf: error reading TXT attributes")
        }

        val urlStr = "http://$host:$port$cpath?action=getInfo"
        Timber.d("SpotifyZeroConf: querying $urlStr")

        val jsonStr = httpGet(urlStr, timeoutMs = 2500)
        if (jsonStr == null) {
            Timber.w("SpotifyZeroConf: no response from $urlStr")
            return@withContext
        }

        try {
            val json = JSONObject(jsonStr)
            val deviceId = json.optString("deviceID").takeIf { it.isNotBlank() } ?: return@withContext
            var remoteName = json.optString("remoteName").takeIf { it.isNotBlank() } ?: resolvedInfo.serviceName
            val brand = json.optString("brandDisplayName")
            val model = json.optString("modelDisplayName")
            val deviceType = json.optString("deviceType", "Speaker")
            val activeUser = json.optString("activeUser")

            val isEchoOrAlexa = brand.contains("Amazon", ignoreCase = true) ||
                    model.contains("Echo", ignoreCase = true) ||
                    remoteName.contains("Echo", ignoreCase = true) ||
                    remoteName.contains("Alexa", ignoreCase = true) ||
                    remoteName.contains("Dot", ignoreCase = true)

            if (isEchoOrAlexa && !remoteName.contains("Echo", ignoreCase = true) && model.isNotBlank()) {
                remoteName = "$remoteName ($model)"
            }

            val spotifyDevice = SpotifyDevice(
                id = deviceId,
                name = remoteName,
                type = if (isEchoOrAlexa) "Speaker" else deviceType,
                isActive = false,
                isRestricted = false,
                volumePercent = 50,
                supportsVolume = true,
            )

            val item = DiscoveredZeroConfDevice(
                device = spotifyDevice,
                ip = host,
                port = port,
                cpath = cpath,
                activeUser = activeUser,
            )

            val current = _discoveredDevices.value.toMutableMap()
            current[deviceId] = item
            _discoveredDevices.value = current
            Timber.i("SpotifyZeroConf: successfully added device: $remoteName [id=$deviceId] at $host:$port")
        } catch (e: Exception) {
            Timber.e(e, "SpotifyZeroConf: failed to parse getInfo JSON: $jsonStr")
        }
    }

    suspend fun wakeUpDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val item = _discoveredDevices.value[deviceId] ?: return@withContext
        val urlStr = "http://${item.ip}:${item.port}${item.cpath}?action=getInfo"
        Timber.d("SpotifyZeroConf: waking up device via $urlStr")
        httpGet(urlStr, timeoutMs = 1500)
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", "FrMusic4all/1.1.0")
            }
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun stopDiscovery() {
        try {
            if (isDiscovering && discoveryListener != null) {
                nsdManager?.stopServiceDiscovery(discoveryListener)
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyZeroConf: failed to stop service discovery")
        } finally {
            isDiscovering = false
            discoveryListener = null
        }

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Timber.d("SpotifyZeroConf: MulticastLock released")
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyZeroConf: error releasing MulticastLock")
        }
    }
}
