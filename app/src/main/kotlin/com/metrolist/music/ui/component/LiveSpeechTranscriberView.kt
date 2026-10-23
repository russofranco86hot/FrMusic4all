/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.ui.component

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.lyrics.LiveSpeechTranscriber

@Composable
fun LiveSpeechTranscriberView(
    modifier: Modifier = Modifier,
    isRadioOrLive: Boolean = false,
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val isListening by LiveSpeechTranscriber.isListening.collectAsStateWithLifecycle()
    val lines by LiveSpeechTranscriber.transcribedLines.collectAsStateWithLifecycle()
    val partial by LiveSpeechTranscriber.currentPartialText.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // Auto-scroll to bottom as new speech arrives
    LaunchedEffect(lines.size, partial) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            LiveSpeechTranscriber.stop()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            LiveSpeechTranscriber.start(context)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.live_transcription_permission_needed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart && !isListening) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                LiveSpeechTranscriber.start(context)
            }
        }
    }

    val toggleListening = {
        if (isListening) {
            LiveSpeechTranscriber.stop()
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPerm) {
                LiveSpeechTranscriber.start(context)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!isListening && lines.isEmpty() && partial.isBlank()) {
            // Idle empty state
            Spacer(modifier = Modifier.weight(0.3f))
            Icon(
                painter = painterResource(R.drawable.ic_mic_wave),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isRadioOrLive) "Transcriptor en vivo para Radio y Streaming" else stringResource(R.string.lyrics_not_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Activa el reconocimiento de voz para escuchar la transmisión y escribir en tiempo real lo que están diciendo o cantando.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            ElevatedButton(
                onClick = toggleListening,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic_wave),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.live_transcription_start),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.weight(0.7f))
        } else {
            // Active listening or transcription displayed
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .then(if (isListening) Modifier.scale(pulseScale) else Modifier)
                            .background(
                                color = if (isListening) Color(0xFFE53935) else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isListening) stringResource(R.string.lyrics_transcribing_live) else "Transcripción pausada",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                Row {
                    if (lines.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { LiveSpeechTranscriber.clear() },
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Text("Limpiar", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    FilledTonalButton(
                        onClick = toggleListening,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = if (isListening) stringResource(R.string.live_transcription_stop) else "Reanudar",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(lines) { line ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 28.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                if (partial.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "… $partial",
                                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 28.sp),
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
