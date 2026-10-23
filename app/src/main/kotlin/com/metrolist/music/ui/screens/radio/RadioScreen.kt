/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.ui.screens.radio

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.metrolist.music.radio.RadioSortOrder
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.metrolist.music.LocalIsWatch
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.radio.RadioCountry
import com.metrolist.music.radio.RadioStation
import com.metrolist.music.radio.RadioTab
import com.metrolist.music.radio.RadioTag
import com.metrolist.music.radio.RadioViewModel
import com.metrolist.music.utils.rememberPreference

@Composable
fun RadioScreen(
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val isWatch = LocalIsWatch.current
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val backgroundColor = if (pureBlack || isWatch) Color.Black else MaterialTheme.colorScheme.background

    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val countries by viewModel.countries.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val currentMetadata by (playerConnection?.mediaMetadata ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val isPlaying by (playerConnection?.isPlaying ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    val recordings by viewModel.recordings.collectAsState()
    val isRecording by viewModel.recordingManager.isRecording.collectAsState()
    val recordingSeconds by viewModel.recordingManager.recordingSeconds.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    // M3U Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFavorites(
                uri = uri,
                onSuccess = { count ->
                    Toast.makeText(context, context.getString(R.string.radio_export_success) + " ($count)", Toast.LENGTH_SHORT).show()
                },
                onError = {
                    Toast.makeText(context, context.getString(R.string.radio_export_failed), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // M3U Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFavorites(
                uri = uri,
                onSuccess = { count ->
                    Toast.makeText(context, context.getString(R.string.radio_import_success, count), Toast.LENGTH_SHORT).show()
                },
                onError = {
                    Toast.makeText(context, context.getString(R.string.radio_import_failed), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val playStation: (RadioStation) -> Unit = { station ->
        val stationMediaId = "radio:${station.streamUrl}"
        if (currentMetadata?.id == stationMediaId) {
            playerConnection?.togglePlayPause()
        } else {
            playerConnection?.playQueue(
                ListQueue(
                    title = station.name,
                    items = listOf(station.toMediaItem()),
                    position = androidx.media3.common.C.TIME_UNSET,
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (!isWatch) {
            // Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.radio),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.radio_search_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                    )
                )
            }

            // Active filter indicator if any
            AnimatedVisibility(visible = selectedCountry != null || selectedTag != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    selectedCountry?.let { country ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.selectCountry(null) },
                            label = { Text(country.name) },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Remove filter",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    selectedTag?.let { tag ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.selectTag(null) },
                            label = { Text("#${tag.name}") },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Remove filter",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Live Recording Bar (when radio is currently playing)
        if (currentMetadata?.id?.startsWith("radio:") == true) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRecording) Color.Red.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isWatch) 6.dp else 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color.Red else MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRecording) {
                                val m = recordingSeconds / 60
                                val s = recordingSeconds % 60
                                "🔴 Grabando (%02d:%02d)".format(m, s)
                            } else {
                                "Radio en vivo: ${currentMetadata?.title ?: ""}"
                            },
                            style = if (isWatch) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            if (isRecording) {
                                val saved = viewModel.recordingManager.stopRecording()
                                if (saved != null) {
                                    Toast.makeText(context, context.getString(R.string.radio_recording_saved, saved.title), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val streamUrl = currentMetadata?.id?.removePrefix("radio:") ?: ""
                                viewModel.recordingManager.startRecording(currentMetadata?.title ?: "Radio", streamUrl)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isRecording) stringResource(R.string.radio_stop_recording) else stringResource(R.string.radio_record),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = if (isWatch) 6.dp else 16.dp,
            indicator = { tabPositions ->
                if (currentTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            divider = {}
        ) {
            Tab(
                selected = currentTab == RadioTab.FAVORITES,
                onClick = { viewModel.setTab(RadioTab.FAVORITES) },
                text = {
                    Text(
                        text = stringResource(R.string.radio_tab_favorites) + if (favorites.isNotEmpty()) " (${favorites.size})" else "",
                        fontSize = if (isWatch) 10.sp else 14.sp,
                        fontWeight = if (currentTab == RadioTab.FAVORITES) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = currentTab == RadioTab.TOP,
                onClick = { viewModel.setTab(RadioTab.TOP) },
                text = {
                    Text(
                        text = stringResource(R.string.radio_tab_top),
                        fontSize = if (isWatch) 10.sp else 14.sp,
                        fontWeight = if (currentTab == RadioTab.TOP) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = currentTab == RadioTab.RECORDINGS,
                onClick = { viewModel.setTab(RadioTab.RECORDINGS) },
                text = {
                    val count = recordings.size
                    Text(
                        text = stringResource(R.string.radio_tab_recordings) + if (count > 0) " ($count)" else "",
                        fontSize = if (isWatch) 10.sp else 14.sp,
                        fontWeight = if (currentTab == RadioTab.RECORDINGS) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            if (!isWatch) {
                Tab(
                    selected = currentTab == RadioTab.COUNTRIES,
                    onClick = { viewModel.setTab(RadioTab.COUNTRIES) },
                    text = {
                        Text(
                            text = stringResource(R.string.radio_tab_countries),
                            fontWeight = if (currentTab == RadioTab.COUNTRIES) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = currentTab == RadioTab.GENRES,
                    onClick = { viewModel.setTab(RadioTab.GENRES) },
                    text = {
                        Text(
                            text = stringResource(R.string.radio_tab_genres),
                            fontWeight = if (currentTab == RadioTab.GENRES) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentTab) {
                RadioTab.RECORDINGS -> {
                    RecordingsTabContent(
                        recordings = recordings,
                        onPlayRecording = { rec ->
                            if (playerConnection != null) {
                                viewModel.recordingManager.playRecording(rec, playerConnection)
                            }
                        },
                        onDeleteRecording = { rec ->
                            viewModel.recordingManager.deleteRecording(rec)
                        }
                    )
                }

                RadioTab.FAVORITES -> {
                    FavoritesTabContent(
                        favorites = favorites,
                        currentMediaId = currentMetadata?.id,
                        isPlaying = isPlaying,
                        onPlayStation = playStation,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onExportM3U = {
                            exportLauncher.launch("radio_favorites.m3u")
                        },
                        onImportM3U = {
                            importLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "text/plain", "*/*"))
                        }
                    )
                }

                RadioTab.TOP -> {
                    StationsListContent(
                        stations = stations,
                        isLoading = isLoading,
                        sortOrder = sortOrder,
                        onSortOrderChanged = viewModel::setSortOrder,
                        currentMediaId = currentMetadata?.id,
                        isPlaying = isPlaying,
                        onPlayStation = playStation,
                        onToggleFavorite = viewModel::toggleFavorite,
                        isFavorite = viewModel::isFavorite
                    )
                }

                RadioTab.COUNTRIES -> {
                    CountriesTabContent(
                        countries = countries,
                        onSelectCountry = { country ->
                            viewModel.selectCountry(country)
                            viewModel.setTab(RadioTab.TOP)
                        }
                    )
                }

                RadioTab.GENRES -> {
                    GenresTabContent(
                        tags = tags,
                        onSelectTag = { tag ->
                            viewModel.selectTag(tag)
                            viewModel.setTab(RadioTab.TOP)
                        }
                    )
                }
            }

            if (isLoading && currentTab != RadioTab.FAVORITES) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FavoritesTabContent(
    favorites: List<RadioStation>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlayStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onExportM3U: () -> Unit,
    onImportM3U: () -> Unit,
) {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val isWatch = LocalIsWatch.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = if (isWatch) PaddingValues(top = 8.dp, bottom = 48.dp, start = 4.dp, end = 4.dp) else PaddingValues(
            top = 12.dp,
            bottom = insets.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (isWatch) 6.dp else 8.dp)
    ) {
        if (!isWatch) {
            // Export / Import buttons row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportM3U,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.upload),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.radio_export_favorites), fontSize = 13.sp)
                    }

                    Button(
                        onClick = onImportM3U,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.radio_import_favorites), fontSize = 13.sp)
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.radio_empty_favorites),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.radio_empty_favorites_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(favorites, key = { it.stationuuid }) { station ->
                val isCurrent = currentMediaId == "radio:${station.streamUrl}"
                StationItemCard(
                    station = station,
                    isCurrentPlaying = isCurrent && isPlaying,
                    isCurrentSelected = isCurrent,
                    isFavorite = true,
                    onClick = { onPlayStation(station) },
                    onToggleFavorite = { onToggleFavorite(station) }
                )
            }
        }
    }
}

@Composable
private fun StationsListContent(
    stations: List<RadioStation>,
    isLoading: Boolean,
    sortOrder: RadioSortOrder = RadioSortOrder.MOST_LISTENED,
    onSortOrderChanged: (RadioSortOrder) -> Unit = {},
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlayStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
) {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    if (stations.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.radio_empty_stations),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val isWatch = LocalIsWatch.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = if (isWatch) PaddingValues(top = 8.dp, bottom = 48.dp, start = 4.dp, end = 4.dp) else PaddingValues(
            top = 8.dp,
            bottom = insets.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (isWatch) 6.dp else 8.dp)
    ) {
        item(key = "sort_chips") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = sortOrder == RadioSortOrder.MOST_LISTENED,
                        onClick = { onSortOrderChanged(RadioSortOrder.MOST_LISTENED) },
                        label = { Text(stringResource(R.string.radio_sort_most_listened)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = sortOrder == RadioSortOrder.MOST_VOTED,
                        onClick = { onSortOrderChanged(RadioSortOrder.MOST_VOTED) },
                        label = { Text(stringResource(R.string.radio_sort_most_voted)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = sortOrder == RadioSortOrder.NAME,
                        onClick = { onSortOrderChanged(RadioSortOrder.NAME) },
                        label = { Text(stringResource(R.string.radio_sort_name)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = sortOrder == RadioSortOrder.BITRATE,
                        onClick = { onSortOrderChanged(RadioSortOrder.BITRATE) },
                        label = { Text(stringResource(R.string.radio_sort_bitrate)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        items(stations, key = { it.stationuuid }) { station ->
            val isCurrent = currentMediaId == "radio:${station.streamUrl}"
            StationItemCard(
                station = station,
                isCurrentPlaying = isCurrent && isPlaying,
                isCurrentSelected = isCurrent,
                isFavorite = isFavorite(station.stationuuid),
                onClick = { onPlayStation(station) },
                onToggleFavorite = { onToggleFavorite(station) }
            )
        }
    }
}

@Composable
private fun StationItemCard(
    station: RadioStation,
    isCurrentPlaying: Boolean,
    isCurrentSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val isWatch = LocalIsWatch.current
    val cardBackground = if (isCurrentSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else if (pureBlack || isWatch) {
        Color(0xFF141414)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isWatch) 10.dp else 16.dp))
            .background(cardBackground)
            .clickable(onClick = onClick)
            .padding(if (isWatch) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favicon thumbnail
        Box(
            modifier = Modifier
                .size(if (isWatch) 34.dp else 52.dp)
                .clip(RoundedCornerShape(if (isWatch) 8.dp else 12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!station.favicon.isNullOrBlank()) {
                AsyncImage(
                    model = station.favicon,
                    contentDescription = station.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.radio),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isWatch) 20.dp else 28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isWatch) 8.dp else 14.dp))

        // Station Details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = station.name.trim(),
                style = if (isWatch) MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ) else MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val subtitleParts = mutableListOf<String>()
            station.country?.takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }
            station.codec?.takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }
            if (station.bitrate > 0) subtitleParts.add("${station.bitrate} kbps")

            Text(
                text = subtitleParts.joinToString(" • ").ifEmpty { "Live Radio" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Action Buttons: Favorite + Play State
        if (!isWatch) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    painter = painterResource(if (isFavorite) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .size(if (isWatch) 30.dp else 36.dp)
        ) {
            Icon(
                painter = painterResource(if (isCurrentPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                tint = if (isCurrentSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isWatch) 16.dp else 20.dp)
            )
        }
    }
}

@Composable
private fun CountriesTabContent(
    countries: List<RadioCountry>,
    onSelectCountry: (RadioCountry) -> Unit,
) {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = insets.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(countries, key = { it.isoCode }) { country ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectCountry(country) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = country.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${country.stationCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GenresTabContent(
    tags: List<RadioTag>,
    onSelectTag: (RadioTag) -> Unit,
) {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = insets.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(tags, key = { it.name }) { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectTag(tag) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${tag.name}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${tag.stationCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecordingsTabContent(
    recordings: List<com.metrolist.music.radio.RadioRecording>,
    onPlayRecording: (com.metrolist.music.radio.RadioRecording) -> Unit,
    onDeleteRecording: (com.metrolist.music.radio.RadioRecording) -> Unit,
) {
    val isWatch = LocalIsWatch.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    if (recordings.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    modifier = Modifier.size(if (isWatch) 32.dp else 56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.radio_no_recordings),
                    style = if (isWatch) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = if (isWatch) PaddingValues(top = 8.dp, bottom = 48.dp, start = 4.dp, end = 4.dp) else PaddingValues(
            top = 8.dp,
            bottom = insets.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (isWatch) 6.dp else 8.dp)
    ) {
        items(recordings, key = { it.id }) { rec ->
            var showDeleteDialog by remember { mutableStateOf(false) }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.radio_delete_recording_confirm)) },
                    text = { Text(rec.title) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteRecording(rec)
                                showDeleteDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(if (isWatch) 10.dp else 16.dp),
                color = if (isWatch) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayRecording(rec) }
            ) {
                Row(
                    modifier = Modifier.padding(if (isWatch) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isWatch) 32.dp else 44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (isWatch) 16.dp else 22.dp)
                        )
                    }
                    Spacer(Modifier.width(if (isWatch) 8.dp else 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rec.title,
                            style = if (isWatch) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val mins = rec.durationSeconds / 60
                        val secs = rec.durationSeconds % 60
                        val mb = rec.fileSizeBytes / (1024 * 1024.0)
                        Text(
                            text = "%02d:%02d • %.1f MB".format(mins, secs, mb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(if (isWatch) 28.dp else 36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(if (isWatch) 16.dp else 20.dp)
                        )
                    }
                }
            }
        }
    }
}
