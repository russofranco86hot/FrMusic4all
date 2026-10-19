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
import com.metrolist.music.constants.EnableDlnaCastKey
import com.metrolist.music.utils.rememberPreference

@Composable
fun DlnaCastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val playerConnection = LocalPlayerConnection.current
    val dlnaHandler = playerConnection?.service?.dlnaCastHandler
    val isCasting by dlnaHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val (enabled) = rememberPreference(EnableDlnaCastKey, defaultValue = true)
    var showPicker by remember { mutableStateOf(false) }

    if (enabled) {
        val iconTint = if (isCasting) MaterialTheme.colorScheme.primary else tintColor
        IconButton(
            onClick = {
                showPicker = true
                dlnaHandler?.startDiscovery()
            },
            modifier = modifier,
        ) {
            Icon(
                painter = painterResource(if (isCasting) R.drawable.cast_connected else R.drawable.cast),
                contentDescription = stringResource(R.string.dlna_cast),
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }

        if (showPicker) {
            DlnaDevicePicker(
                onDismissRequest = { showPicker = false },
            )
        }
    }
}
