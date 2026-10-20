/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.playback

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

data class ConnectedAudioDevice(
    val id: Int,
    val name: String,
    val typeDescription: String,
    val isWatch: Boolean,
    val isEcho: Boolean = false,
    val isBluetooth: Boolean = true,
    val isCurrentOutput: Boolean = false,
    val isConnected: Boolean = true,
)

object BluetoothDeviceManager {
    private val _connectedDevices = MutableStateFlow<List<ConnectedAudioDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedAudioDevice>> = _connectedDevices.asStateFlow()

    private var audioManager: AudioManager? = null
    private var callbackRegistered = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    fun init(context: Context) {
        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am

        if (!callbackRegistered && am != null) {
            am.registerAudioDeviceCallback(audioDeviceCallback, null)
            callbackRegistered = true
        }
        refreshDevices()
    }

    fun refreshDevices() {
        val am = audioManager ?: return
        val deviceList = mutableListOf<ConnectedAudioDevice>()

        try {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in outputs) {
                val isBt = when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
                    else -> false
                }

                if (!isBt) continue

                val rawName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    device.productName?.toString().orEmpty()
                } else {
                    "Dispositivo Bluetooth"
                }

                val displayName = rawName.ifBlank { "Dispositivo de audio Bluetooth" }
                val isWatch = displayName.contains("Watch", ignoreCase = true) ||
                        displayName.contains("Gear", ignoreCase = true) ||
                        displayName.contains("Reloj", ignoreCase = true) ||
                        displayName.contains("Pixel Watch", ignoreCase = true) ||
                        displayName.contains("Galaxy Watch", ignoreCase = true)

                val isEcho = displayName.contains("Echo", ignoreCase = true) ||
                        displayName.contains("Alexa", ignoreCase = true) ||
                        displayName.contains("Dot", ignoreCase = true)

                val typeDesc = when {
                    isWatch -> "Smartwatch / Reloj Wear OS"
                    isEcho -> "Amazon Echo (Bluetooth)"
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Parlante Bluetooth"
                    else -> "Audio Bluetooth"
                }

                deviceList.add(
                    ConnectedAudioDevice(
                        id = device.id,
                        name = displayName,
                        typeDescription = typeDesc,
                        isWatch = isWatch,
                        isEcho = isEcho,
                        isBluetooth = true,
                        isCurrentOutput = false,
                        isConnected = true,
                    )
                )
            }

            // Also inspect bonded (paired) Bluetooth devices for Amazon Echo or Smartwatches in standby
            try {
                val btAdapter = BluetoothAdapter.getDefaultAdapter()
                val bonded = btAdapter?.bondedDevices
                if (bonded != null) {
                    for (btDev in bonded) {
                        val name = btDev.name.orEmpty()
                        if (name.isBlank()) continue

                        val isEcho = name.contains("Echo", ignoreCase = true) ||
                                name.contains("Alexa", ignoreCase = true) ||
                                name.contains("Dot", ignoreCase = true) ||
                                name.contains("Show", ignoreCase = true) ||
                                name.contains("Pop", ignoreCase = true)

                        val isWatch = name.contains("Watch", ignoreCase = true) ||
                                name.contains("Gear", ignoreCase = true) ||
                                name.contains("Reloj", ignoreCase = true)

                        if (!isEcho && !isWatch) continue

                        // Check if already in deviceList
                        if (deviceList.none { it.name.equals(name, ignoreCase = true) }) {
                            deviceList.add(
                                ConnectedAudioDevice(
                                    id = btDev.address.hashCode(),
                                    name = name,
                                    typeDescription = if (isEcho) "Amazon Echo (En reposo - Toca para conectar)" else "Smartwatch (En reposo)",
                                    isWatch = isWatch,
                                    isEcho = isEcho,
                                    isBluetooth = true,
                                    isCurrentOutput = false,
                                    isConnected = false,
                                )
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                Timber.w(e, "BluetoothDeviceManager: missing BLUETOOTH_CONNECT permission to list bonded devices")
            }
        } catch (e: Exception) {
            Timber.e(e, "BluetoothDeviceManager: error enumerating audio devices")
        }

        _connectedDevices.value = deviceList
    }

    /**
     * Opens the native Android System Media Output Panel (Android 11+).
     * This allows 1-tap switching to connected Smartwatches (Wear OS / Galaxy Watch),
     * Bluetooth speakers, Amazon Echo paired via Bluetooth, and Google Cast devices.
     */
    fun openMediaOutputPanel(context: Context): Boolean {
        // Android 11+ Media Output Switcher panel
        val intent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
            putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.w(e, "Media Output Panel not available, falling back to Bluetooth settings")
            try {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                true
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to open Bluetooth settings")
                false
            }
        }
    }
}
