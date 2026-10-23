/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.ui.screens.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalIsWatch
import com.metrolist.music.R
import com.metrolist.music.tv.TvCategory
import com.metrolist.music.tv.TvChannel
import com.metrolist.music.tv.TvStreamingViewModel

@Composable
fun TvStreamingScreen(
    modifier: Modifier = Modifier,
    viewModel: TvStreamingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val liveSearchResults by viewModel.liveSearchResults.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val currentMediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    if (showAddDialog) {
        var newChannelName by remember { mutableStateOf("") }
        var newChannelUrl by remember { mutableStateOf("") }
        var selectedCat by remember { mutableStateOf(TvCategory.STREAMING) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.tv_add_channel_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        label = { Text(stringResource(R.string.tv_channel_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newChannelUrl,
                        onValueChange = { newChannelUrl = it },
                        label = { Text(stringResource(R.string.tv_channel_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf(TvCategory.STREAMING, TvCategory.NEWS, TvCategory.MUSIC).forEach { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newChannelName.isNotBlank() && newChannelUrl.isNotBlank()) {
                            viewModel.addCustomChannel(newChannelName, newChannelUrl, selectedCat)
                            showAddDialog = false
                        }
                    },
                    enabled = newChannelName.isNotBlank() && newChannelUrl.isNotBlank(),
                ) {
                    Text(stringResource(R.string.tv_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val isWatch = LocalIsWatch.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (isWatch) Modifier.background(Color.Black) else Modifier.statusBarsPadding())
            .padding(horizontal = if (isWatch) 8.dp else 16.dp),
    ) {
        if (!isWatch) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tv),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.tv_streaming_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = stringResource(R.string.tv_add_channel),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.tv_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )

            // Categories Chips
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val categories = listOf(
                    TvCategory.ALL to "Todos",
                    TvCategory.STREAMING to "Streaming (Luzu, Olga…)",
                    TvCategory.NEWS to "Noticias & Aire",
                )
                items(categories) { (cat, label) ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        }

        // Channels List
        LazyColumn(
            contentPadding = if (isWatch) PaddingValues(top = 26.dp, bottom = 48.dp, start = 4.dp, end = 4.dp) else PaddingValues(vertical = 8.dp, horizontal = 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (isWatch) 6.dp else 10.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (liveSearchResults.isNotEmpty()) {
                item(key = "header_live_search") {
                    Text(
                        text = stringResource(R.string.tv_youtube_live_results),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(liveSearchResults, key = { it.id }) { liveChannel ->
                    TvChannelCard(
                        channel = liveChannel,
                        isPlaying = currentMediaMetadata?.title == liveChannel.name,
                        onPlay = { viewModel.playChannel(liveChannel, playerConnection) },
                        onToggleFavorite = { viewModel.toggleFavorite(liveChannel.id) },
                    )
                }
                item(key = "divider_live_search") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(channels, key = { it.id }) { channel ->
                val isCurrentlyPlaying = currentMediaMetadata?.title == channel.name

                TvChannelCard(
                    channel = channel,
                    isPlaying = isCurrentlyPlaying,
                    onPlay = { viewModel.playChannel(channel, playerConnection) },
                    onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                    onDelete = if (channel.isCustom) { { viewModel.removeCustomChannel(channel.id) } } else null,
                )
            }
        }
    }
}

@Composable
private fun TvChannelCard(
    channel: TvChannel,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isWatch = LocalIsWatch.current
    Card(
        shape = RoundedCornerShape(if (isWatch) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else if (isWatch) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isWatch) 6.dp else 12.dp),
        ) {
            // Channel Logo / Avatar
            Box(
                modifier = Modifier
                    .size(if (isWatch) 36.dp else 54.dp)
                    .clip(RoundedCornerShape(if (isWatch) 8.dp else 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Channel Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Live Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE53935),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EN VIVO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = channel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!isWatch) {
                Spacer(modifier = Modifier.width(8.dp))

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.tv_delete_channel),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Favorite Icon
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(if (channel.isFavorite) R.drawable.favorite else R.drawable.favorite_border),
                        contentDescription = null,
                        tint = if (channel.isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
