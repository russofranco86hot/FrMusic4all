/**
 * FrMusic4all Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.SpotifyConnectManager
import com.metrolist.spotify.models.SpotifyDevice

@Composable
fun CastDevicePicker(
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    // Google Cast state
    val castHandler = playerConnection.service.castConnectionHandler
    val isGoogleCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val googleCastDeviceName by castHandler?.castDeviceName?.collectAsStateWithLifecycle() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Spotify Connect state
    val isSpotifyConnected by SpotifyConnectManager.isConnected.collectAsStateWithLifecycle()
    val connectedSpotifyDevice by SpotifyConnectManager.connectedDevice.collectAsStateWithLifecycle()
    val spotifyDevices by SpotifyConnectManager.availableDevices.collectAsStateWithLifecycle()
    val isSpotifyScanning by SpotifyConnectManager.isScanning.collectAsStateWithLifecycle()
    val isSpotifyLoggedIn by SpotifyConnectManager.isLoggedIn.collectAsStateWithLifecycle()

    // DLNA state
    val dlnaHandler = playerConnection.service.dlnaCastHandler
    val dlnaDevices by dlnaHandler?.discoveredDevices?.collectAsStateWithLifecycle() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    val isDlnaCasting by dlnaHandler?.isCasting?.collectAsStateWithLifecycle() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val selectedDlnaDevice by dlnaHandler?.selectedDevice?.collectAsStateWithLifecycle() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.metrolist.music.playback.DlnaDevice?>(null) }

    LaunchedEffect(Unit) {
        SpotifyConnectManager.scanDevices()
        dlnaHandler?.startDiscovery()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth(0.95f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(
                            if (isGoogleCasting || isSpotifyConnected || isDlnaCasting) R.drawable.cast_connected
                            else R.drawable.cast
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.connect_to_device),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (isSpotifyScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = {
                            SpotifyConnectManager.scanDevices()
                            dlnaHandler?.startDiscovery()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.replay),
                            contentDescription = stringResource(R.string.dlna_scanning),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
            ) {
                // Currently connected banner
                val activeDeviceName = when {
                    isGoogleCasting -> googleCastDeviceName ?: "Google Cast"
                    isSpotifyConnected -> connectedSpotifyDevice?.name ?: "Spotify Connect"
                    isDlnaCasting -> selectedDlnaDevice?.name ?: "DLNA Speaker"
                    else -> null
                }

                if (activeDeviceName != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.cast_connected),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.connected),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        )
                                        Text(
                                            text = activeDeviceName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        if (isGoogleCasting) castHandler?.disconnect()
                                        if (isSpotifyConnected) SpotifyConnectManager.disconnect { playerConnection.player.play() }
                                        if (isDlnaCasting) dlnaHandler?.disconnect()
                                    },
                                ) {
                                    Text(
                                        text = stringResource(R.string.disconnect),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // This phone item
                item {
                    val isLocalActive = !isGoogleCasting && !isSpotifyConnected && !isDlnaCasting
                    DeviceItemRow(
                        iconRes = R.drawable.music_note,
                        title = stringResource(R.string.this_phone),
                        subtitle = if (isLocalActive) stringResource(R.string.connected) else null,
                        isActive = isLocalActive,
                        onClick = {
                            if (isGoogleCasting) castHandler?.disconnect()
                            if (isSpotifyConnected) SpotifyConnectManager.disconnect { playerConnection.player.play() }
                            if (isDlnaCasting) dlnaHandler?.disconnect()
                            onDismissRequest()
                        },
                    )
                }

                // Google Cast section
                if (GoogleCastBridge.isAvailable) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DeviceItemRow(
                            iconRes = R.drawable.cast,
                            title = stringResource(R.string.google_cast_title),
                            subtitle = stringResource(R.string.google_cast_desc),
                            isActive = isGoogleCasting,
                            onClick = {
                                onDismissRequest()
                                GoogleCastBridge.showDialog(context)
                            },
                        )
                    }
                }

                // Spotify Connect section (Amazon Echo, Alexa groups, etc.)
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.spotify_connect_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }

                if (!isSpotifyLoggedIn) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.spotify_login_to_connect),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else if (spotifyDevices.isEmpty() && !isSpotifyScanning) {
                    item {
                        Text(
                            text = stringResource(R.string.dlna_no_devices),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    items(spotifyDevices) { device ->
                        val isCurrentActive = isSpotifyConnected && connectedSpotifyDevice?.id == device.id
                        val (iconRes, typeDesc) = getSpotifyDeviceDisplay(device)

                        DeviceItemRow(
                            iconRes = iconRes,
                            title = device.name,
                            subtitle = typeDesc,
                            isActive = isCurrentActive || device.isActive,
                            onClick = {
                                SpotifyConnectManager.connectToDevice(
                                    device = device,
                                    currentMedia = mediaMetadata,
                                    onLocalPause = { playerConnection.player.pause() },
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.playing_on, device.name),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onDismissRequest()
                            },
                        )
                    }
                }

                // DLNA section (if any local renderers found)
                if (dlnaDevices.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = stringResource(R.string.dlna_cast),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }

                    items(dlnaDevices) { device ->
                        val isCurrentActive = isDlnaCasting && selectedDlnaDevice?.id == device.id
                        DeviceItemRow(
                            iconRes = R.drawable.cast,
                            title = device.name,
                            subtitle = "DLNA Speaker",
                            isActive = isCurrentActive,
                            onClick = {
                                dlnaHandler?.connect(device)
                                mediaMetadata?.let { dlnaHandler?.loadMedia(it) }
                                playerConnection.player.pause()
                                onDismissRequest()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeviceItemRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private fun getSpotifyDeviceDisplay(device: SpotifyDevice): Pair<Int, String> {
    val lowerName = device.name.lowercase()
    val isEcho = lowerName.contains("echo") || lowerName.contains("alexa") || lowerName.contains("dot")

    return when {
        isEcho -> Pair(R.drawable.volume_up, "Amazon Echo • Alexa")
        device.type.equals("CastGroup", ignoreCase = true) -> Pair(R.drawable.group, "Speaker Group (Multi-Room)")
        device.type.equals("Computer", ignoreCase = true) -> Pair(R.drawable.spotify, "Computer • Spotify")
        device.type.equals("Smartphone", ignoreCase = true) -> Pair(R.drawable.music_note, "Phone • Spotify")
        device.type.equals("TV", ignoreCase = true) -> Pair(R.drawable.cast, "Smart TV")
        else -> Pair(R.drawable.volume_up, "Smart Speaker • Spotify Connect")
    }
}
