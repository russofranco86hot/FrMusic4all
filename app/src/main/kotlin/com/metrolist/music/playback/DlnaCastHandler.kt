package com.metrolist.music.playback

import android.content.Context
import android.net.wifi.WifiManager
import com.metrolist.music.models.MediaMetadata as AppMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jupnp.DefaultUpnpServiceConfiguration
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceImpl
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.message.header.STAllHeader
import org.jupnp.model.message.header.UDAServiceTypeHeader
import org.jupnp.model.meta.Device
import org.jupnp.model.meta.LocalDevice
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.Service
import org.jupnp.model.types.UDAServiceType
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import org.jupnp.support.avtransport.callback.GetPositionInfo
import org.jupnp.support.avtransport.callback.GetTransportInfo
import org.jupnp.support.avtransport.callback.Pause
import org.jupnp.support.avtransport.callback.Play
import org.jupnp.support.avtransport.callback.Seek
import org.jupnp.support.avtransport.callback.SetAVTransportURI
import org.jupnp.support.avtransport.callback.Stop
import org.jupnp.support.model.PositionInfo
import org.jupnp.support.model.SeekMode
import org.jupnp.support.model.TransportInfo
import org.jupnp.support.model.TransportState
import org.jupnp.support.renderingcontrol.callback.SetVolume
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToInt

data class DlnaDevice(
    val id: String,
    val name: String,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val device: Device<*, *, *>,
)

class DlnaCastHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val musicService: MusicService,
) {
    private val _discoveredDevices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DlnaDevice>> = _discoveredDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val _castPosition = MutableStateFlow(0L)
    val castPosition: StateFlow<Long> = _castPosition.asStateFlow()

    private val _castDuration = MutableStateFlow(0L)
    val castDuration: StateFlow<Long> = _castDuration.asStateFlow()

    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying.asStateFlow()

    private val _castIsBuffering = MutableStateFlow(false)
    val castIsBuffering: StateFlow<Boolean> = _castIsBuffering.asStateFlow()

    private val _castVolume = MutableStateFlow(1f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

    @Volatile
    var isSyncingFromCast = false
        private set

    private var upnpService: UpnpService? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var scanTimeoutJob: Job? = null
    private var positionJob: Job? = null
    private var loadJob: Job? = null

    private val currentAvTransportService: Service<*, *>?
        get() = _selectedDevice.value?.device?.findService(UDAServiceType("AVTransport"))

    private val currentRenderingControlService: Service<*, *>?
        get() = _selectedDevice.value?.device?.findService(UDAServiceType("RenderingControl"))

    private val registryListener =
        object : DefaultRegistryListener() {
            override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
                checkAndAddDevice(device)
            }

            override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
                removeDevice(device)
            }

            override fun localDeviceAdded(registry: Registry, device: LocalDevice) {
                checkAndAddDevice(device)
            }

            override fun localDeviceRemoved(registry: Registry, device: LocalDevice) {
                removeDevice(device)
            }
        }

    fun initialize(): Boolean {
        if (upnpService != null) return true
        return runCatching {
            acquireMulticastLock()
            val service = UpnpServiceImpl(DefaultUpnpServiceConfiguration())
            service.registry.addListener(registryListener)
            upnpService = service
            startDiscovery()
            Timber.i("DlnaCastHandler initialized successfully")
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to initialize DLNA Cast Handler")
            false
        }
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                val wifiManager =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager != null) {
                    multicastLock =
                        wifiManager.createMulticastLock("MetrolistDlnaMulticastLock").apply {
                            setReferenceCounted(true)
                            acquire()
                        }
                }
            } else if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire multicast lock")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to release multicast lock")
        }
    }

    fun startDiscovery() {
        val service = upnpService ?: run {
            if (!initialize()) return
            upnpService ?: return
        }

        _isScanning.value = true
        acquireMulticastLock()

        // Sync already registered devices from registry
        for (device in service.registry.devices) {
            checkAndAddDevice(device)
        }

        scanTimeoutJob?.cancel()
        scanTimeoutJob =
            scope.launch {
                runCatching {
                    service.controlPoint.search(UDAServiceTypeHeader(UDAServiceType("AVTransport")))
                    service.controlPoint.search(STAllHeader())
                    service.controlPoint.search()
                }.onFailure { error ->
                    Timber.w(error, "Failed to send UPnP search query")
                }
                delay(12_000)
                _isScanning.value = false
            }
    }

    fun stopDiscovery() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        _isScanning.value = false
    }

    private fun checkAndAddDevice(device: Device<*, *, *>) {
        val avTransport = device.findService(UDAServiceType("AVTransport"))
        val isRenderer =
            avTransport != null ||
                device.type.type.equals("MediaRenderer", ignoreCase = true)

        if (isRenderer) {
            val udn = device.identity.udn.identifierString
            val name =
                device.details?.friendlyName?.takeIf { it.isNotBlank() }
                    ?: device.displayString
            val manufacturer = device.details?.manufacturerDetails?.manufacturer
            val modelName = device.details?.modelDetails?.modelName

            val dlnaDevice =
                DlnaDevice(
                    id = udn,
                    name = name,
                    manufacturer = manufacturer,
                    modelName = modelName,
                    device = device,
                )

            val current = _discoveredDevices.value.toMutableList()
            val index = current.indexOfFirst { it.id == udn }
            if (index >= 0) {
                current[index] = dlnaDevice
            } else {
                current.add(dlnaDevice)
            }
            _discoveredDevices.value = current
            Timber.d("Discovered DLNA renderer: $name ($udn)")
        }
    }

    private fun removeDevice(device: Device<*, *, *>) {
        val udn = device.identity.udn.identifierString
        val current = _discoveredDevices.value.toMutableList()
        if (current.removeAll { it.id == udn }) {
            _discoveredDevices.value = current
            Timber.d("Removed DLNA renderer: $udn")
        }
        if (_selectedDevice.value?.id == udn) {
            disconnect()
        }
    }

    fun connect(dlnaDevice: DlnaDevice) {
        _selectedDevice.value = dlnaDevice
        _castDeviceName.value = dlnaDevice.name
        _isCasting.value = true
        _castIsPlaying.value = false
        _castIsBuffering.value = true

        musicService.player.pause()
        startPositionUpdates()
        loadCurrentMedia()
        Timber.i("Connected to DLNA device: ${dlnaDevice.name}")
    }

    fun disconnect() {
        if (!_isCasting.value) return
        stop()

        val pos = _castPosition.value
        if (pos > 0) {
            musicService.player.seekTo(pos)
        }

        _isCasting.value = false
        _selectedDevice.value = null
        _castDeviceName.value = null
        _castIsPlaying.value = false
        _castIsBuffering.value = false
        _castPosition.value = 0L
        _castDuration.value = 0L
        stopPositionUpdates()
        musicService.player.pause()
        Timber.i("Disconnected from DLNA device")
    }

    fun loadCurrentMedia() {
        musicService.currentMediaMetadata.value?.let(::loadMedia)
    }

    fun loadMedia(metadata: AppMediaMetadata) {
        if (!_isCasting.value) return
        val service = currentAvTransportService ?: return
        val controlPoint = upnpService?.controlPoint ?: return

        loadJob?.cancel()
        loadJob =
            scope.launch {
                _castIsBuffering.value = true
                val streamUrl = musicService.getStreamUrl(metadata.id)
                if (streamUrl.isNullOrBlank()) {
                    Timber.w("Failed to get stream URL for DLNA casting: ${metadata.id}")
                    _castIsBuffering.value = false
                    return@launch
                }

                _castDuration.value =
                    metadata.duration.takeIf { it > 0 }?.toLong()?.times(1000L) ?: 0L

                val didlMetadata = createDidlMetadata(metadata, streamUrl)
                val setUriCallback =
                    object : SetAVTransportURI(service, streamUrl, didlMetadata) {
                        override fun success(invocation: ActionInvocation<*>) {
                            Timber.d("SetAVTransportURI success for ${metadata.title}")
                            play()
                            _castIsBuffering.value = false
                            _castIsPlaying.value = true
                        }

                        override fun failure(
                            invocation: ActionInvocation<*>,
                            operation: UpnpResponse?,
                            defaultMsg: String?,
                        ) {
                            Timber.w("SetAVTransportURI with metadata failed ($defaultMsg), retrying without metadata")
                            val fallbackCallback =
                                object : SetAVTransportURI(service, streamUrl, null) {
                                    override fun success(inv: ActionInvocation<*>) {
                                        play()
                                        _castIsBuffering.value = false
                                        _castIsPlaying.value = true
                                    }

                                    override fun failure(
                                        inv: ActionInvocation<*>,
                                        op: UpnpResponse?,
                                        msg: String?,
                                    ) {
                                        Timber.e("SetAVTransportURI without metadata failed: $msg")
                                        _castIsBuffering.value = false
                                    }
                                }
                            controlPoint.execute(fallbackCallback)
                        }
                    }

                controlPoint.execute(setUriCallback)
                musicService.player.pause()
            }
    }

    fun play() {
        val service = currentAvTransportService ?: return
        val controlPoint = upnpService?.controlPoint ?: return
        val playCallback =
            object : Play(service) {
                override fun success(invocation: ActionInvocation<*>) {
                    _castIsPlaying.value = true
                    _castIsBuffering.value = false
                }

                override fun failure(
                    invocation: ActionInvocation<*>,
                    operation: UpnpResponse?,
                    defaultMsg: String?,
                ) {
                    Timber.e("DLNA Play failed: $defaultMsg")
                }
            }
        controlPoint.execute(playCallback)
    }

    fun pause() {
        val service = currentAvTransportService ?: return
        val controlPoint = upnpService?.controlPoint ?: return
        val pauseCallback =
            object : Pause(service) {
                override fun success(invocation: ActionInvocation<*>) {
                    _castIsPlaying.value = false
                }

                override fun failure(
                    invocation: ActionInvocation<*>,
                    operation: UpnpResponse?,
                    defaultMsg: String?,
                ) {
                    Timber.e("DLNA Pause failed: $defaultMsg")
                }
            }
        controlPoint.execute(pauseCallback)
    }

    fun stop() {
        val service = currentAvTransportService ?: return
        val controlPoint = upnpService?.controlPoint ?: return
        val stopCallback =
            object : Stop(service) {
                override fun success(invocation: ActionInvocation<*>) {
                    _castIsPlaying.value = false
                }

                override fun failure(
                    invocation: ActionInvocation<*>,
                    operation: UpnpResponse?,
                    defaultMsg: String?,
                ) {
                    Timber.e("DLNA Stop failed: $defaultMsg")
                }
            }
        controlPoint.execute(stopCallback)
    }

    fun seekTo(positionMs: Long) {
        val service = currentAvTransportService ?: return
        val controlPoint = upnpService?.controlPoint ?: return
        val targetTime = formatTime(positionMs)
        val seekCallback =
            object : Seek(service, SeekMode.REL_TIME, targetTime) {
                override fun success(invocation: ActionInvocation<*>) {
                    _castPosition.value = positionMs
                }

                override fun failure(
                    invocation: ActionInvocation<*>,
                    operation: UpnpResponse?,
                    defaultMsg: String?,
                ) {
                    Timber.e("DLNA Seek failed: $defaultMsg")
                }
            }
        controlPoint.execute(seekCallback)
    }

    fun setVolume(volume: Float) {
        val service = currentRenderingControlService ?: return
        val controlPoint = upnpService?.controlPoint ?: return
        val volumeInt = (volume.coerceIn(0f, 1f) * 100).roundToInt().toLong()
        val setVolumeCallback =
            object : SetVolume(service, volumeInt) {
                override fun success(invocation: ActionInvocation<*>) {
                    _castVolume.value = volume
                }

                override fun failure(
                    invocation: ActionInvocation<*>,
                    operation: UpnpResponse?,
                    defaultMsg: String?,
                ) {
                    Timber.e("DLNA SetVolume failed: $defaultMsg")
                }
            }
        controlPoint.execute(setVolumeCallback)
    }

    fun skipToNext() {
        if (musicService.player.hasNextMediaItem()) {
            musicService.player.pause()
            musicService.player.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        if (musicService.player.hasPreviousMediaItem()) {
            musicService.player.pause()
            musicService.player.seekToPreviousMediaItem()
        }
    }

    fun navigateToMediaIfInQueue(mediaId: String): Boolean {
        // DLNA devices play items individually; return false so loadMedia handles it
        return false
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob =
            scope.launch {
                while (isActive && _isCasting.value) {
                    val service = currentAvTransportService
                    val controlPoint = upnpService?.controlPoint
                    if (service != null && controlPoint != null) {
                        val posCallback =
                            object : GetPositionInfo(service) {
                                override fun received(
                                    invocation: ActionInvocation<*>,
                                    info: PositionInfo,
                                ) {
                                    val posMs = parseTimeToMillis(info.relTime)
                                    val durMs = parseTimeToMillis(info.trackDuration)
                                    if (posMs >= 0) _castPosition.value = posMs
                                    if (durMs > 0) _castDuration.value = durMs
                                }

                                override fun failure(
                                    invocation: ActionInvocation<*>,
                                    operation: UpnpResponse?,
                                    defaultMsg: String?,
                                ) {
                                }
                            }
                        controlPoint.execute(posCallback)

                        val transportCallback =
                            object : GetTransportInfo(service) {
                                override fun received(
                                    invocation: ActionInvocation<*>,
                                    transportInfo: TransportInfo,
                                ) {
                                    val state = transportInfo.currentTransportState
                                    val isPlaying = state == TransportState.PLAYING
                                    _castIsPlaying.value = isPlaying
                                    _castIsBuffering.value = state == TransportState.TRANSITIONING

                                    // When playback ends naturally, advance queue
                                    if (state == TransportState.STOPPED && _castPosition.value > 0 &&
                                        _castDuration.value > 0 &&
                                        (_castDuration.value - _castPosition.value) <= 3000
                                    ) {
                                        skipToNext()
                                    }
                                }

                                override fun failure(
                                    invocation: ActionInvocation<*>,
                                    operation: UpnpResponse?,
                                    defaultMsg: String?,
                                ) {
                                }
                            }
                        controlPoint.execute(transportCallback)
                    }
                    delay(1000)
                }
            }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun createDidlMetadata(metadata: AppMediaMetadata, streamUrl: String): String {
        val escapedTitle = escapeXml(metadata.title)
        val artist = metadata.artists.joinToString(", ") { it.name }
        val escapedArtist = escapeXml(artist)
        val escapedAlbum = escapeXml(metadata.album?.title ?: "")
        val escapedUrl = escapeXml(streamUrl)
        val escapedThumb = escapeXml(metadata.thumbnailUrl ?: "")

        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"1\" parentID=\"0\" restricted=\"1\">" +
            "<dc:title>$escapedTitle</dc:title>" +
            "<dc:creator>$escapedArtist</dc:creator>" +
            "<upnp:artist>$escapedArtist</upnp:artist>" +
            "<upnp:album>$escapedAlbum</upnp:album>" +
            "<upnp:albumArtURI>$escapedThumb</upnp:albumArtURI>" +
            "<upnp:class>object.item.audioItem.musicTrack</upnp:class>" +
            "<res protocolInfo=\"http-get:*:audio/mp4:*\">$escapedUrl</res>" +
            "</item>" +
            "</DIDL-Lite>"
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun parseTimeToMillis(timeStr: String?): Long {
        if (timeStr.isNullOrBlank() || timeStr == "NOT_IMPLEMENTED") return -1L
        val parts = timeStr.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val seconds = secParts[0].toLong()
                    val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (hours * 3600 + minutes * 60 + seconds) * 1000 + ms
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toLong()
                    (minutes * 60 + seconds) * 1000
                }
                else -> -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    fun release() {
        loadJob?.cancel()
        scanTimeoutJob?.cancel()
        stopPositionUpdates()
        disconnect()
        runCatching {
            upnpService?.registry?.removeListener(registryListener)
            upnpService?.shutdown()
        }
        upnpService = null
        releaseMulticastLock()
        _discoveredDevices.value = emptyList()
    }
}
