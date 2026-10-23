/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.tv

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TvStreamingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val repository = TvChannelsRepository(context)

    private val _selectedCategory = MutableStateFlow(TvCategory.ALL)
    val selectedCategory: StateFlow<TvCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _liveSearchResults = MutableStateFlow<List<TvChannel>>(emptyList())
    val liveSearchResults: StateFlow<List<TvChannel>> = _liveSearchResults.asStateFlow()

    private var searchJob: Job? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val defaultChannels = repository.getDefaultChannels()

    val channels: StateFlow<List<TvChannel>> = combine(
        _selectedCategory,
        _searchQuery,
        repository.favoriteIds,
        repository.customChannels,
    ) { category, query, favIds, customList ->
        val all = customList + defaultChannels
        all
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .filter { channel ->
                val matchesCategory = when (category) {
                    TvCategory.ALL -> true
                    TvCategory.STREAMING -> channel.category == TvCategory.STREAMING
                    TvCategory.NEWS -> channel.category == TvCategory.NEWS
                    TvCategory.MUSIC -> channel.category == TvCategory.MUSIC
                }
                val matchesQuery = query.isBlank() ||
                        channel.name.contains(query, ignoreCase = true) ||
                        channel.description.contains(query, ignoreCase = true)

                matchesCategory && matchesQuery
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultChannels)

    fun selectCategory(category: TvCategory) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().length >= 3) {
            searchJob = viewModelScope.launch {
                delay(600)
                try {
                    val results = repository.searchLiveStreamsFromYouTube(query.trim())
                    _liveSearchResults.value = results
                } catch (e: Exception) {
                    Timber.w(e, "Error searching live streams")
                }
            }
        } else {
            _liveSearchResults.value = emptyList()
        }
    }

    fun addCustomChannel(name: String, streamOrHandle: String, category: TvCategory) {
        viewModelScope.launch {
            val trimmed = streamOrHandle.trim()
            val isHandle = trimmed.startsWith("@") || (!trimmed.startsWith("http") && !trimmed.contains("/"))
            val isDirectStream = trimmed.startsWith("http") && !trimmed.contains("youtube.com") && !trimmed.contains("youtu.be")
            val isYtUrl = trimmed.contains("youtube.com") || trimmed.contains("youtu.be")

            val channel = TvChannel(
                id = "custom_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                category = category,
                description = if (isDirectStream) "Transmisión personalizada" else "Canal de YouTube",
                youtubeHandle = if (isHandle) trimmed else null,
                directStreamUrl = if (isDirectStream || isYtUrl) trimmed else null,
                isCustom = true
            )
            repository.addCustomChannel(channel)
        }
    }

    fun removeCustomChannel(channelId: String) {
        viewModelScope.launch {
            repository.removeCustomChannel(channelId)
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(channelId)
        }
    }

    fun playChannel(channel: TvChannel, playerConnection: PlayerConnection) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val resolvedStream = repository.resolveStreamUri(channel)
                if (resolvedStream == null) {
                    _errorMessage.value = "El canal ${channel.name} no está transmitiendo en vivo en este momento."
                    Timber.w("TvStreamingViewModel: failed to resolve stream for ${channel.name}")
                    return@launch
                }

                val mediaId = if (resolvedStream.startsWith("http")) {
                    "tv:$resolvedStream"
                } else {
                    "tv:https://www.youtube.com/watch?v=$resolvedStream"
                }

                val metadata = MediaMetadata(
                    id = mediaId,
                    title = channel.name,
                    artists = listOf(MediaMetadata.Artist(name = channel.category.displayName, id = null)),
                    duration = -1,
                    thumbnailUrl = channel.logoUrl,
                )

                playerConnection.playQueue(
                    ListQueue(
                        title = "TV: ${channel.name}",
                        items = listOf(metadata.toMediaItem()),
                    )
                )
                Timber.d("TvStreamingViewModel: playing channel ${channel.name} with mediaId=$mediaId")
            } catch (e: Exception) {
                Timber.e(e, "TvStreamingViewModel: error playing channel ${channel.name}")
                _errorMessage.value = "Error al conectar con la transmisión de ${channel.name}: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
