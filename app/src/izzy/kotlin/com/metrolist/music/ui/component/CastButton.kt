package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.SpotifyConnectManager

@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val playerConnection = LocalPlayerConnection.current
    val isSpotifyConnected by SpotifyConnectManager.isConnected.collectAsStateWithLifecycle()
    val dlnaHandler = playerConnection?.service?.dlnaCastHandler
    val isDlnaCasting by dlnaHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val isAnyCasting = isSpotifyConnected || isDlnaCasting
    var showPicker by remember { mutableStateOf(false) }

    val iconTint = if (isAnyCasting) MaterialTheme.colorScheme.primary else tintColor

    IconButton(
        onClick = { showPicker = true },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            painter = painterResource(if (isAnyCasting) R.drawable.cast_connected else R.drawable.cast),
            contentDescription = stringResource(R.string.connect_to_device),
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
    }

    if (showPicker) {
        CastDevicePicker(
            onDismissRequest = { showPicker = false },
        )
    }
}
