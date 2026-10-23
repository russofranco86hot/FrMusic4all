/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.radio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class RadioTab {
    FAVORITES,
    TOP,
    COUNTRIES,
    GENRES,
    RECORDINGS,
}

enum class RadioSortOrder {
    MOST_LISTENED,
    MOST_VOTED,
    NAME,
    BITRATE,
}

@HiltViewModel
class RadioViewModel @Inject constructor(
    @ApplicationContext val context: Context,
) : ViewModel() {

    val favoritesManager = RadioFavoritesManager(context)
    val favorites: StateFlow<List<RadioStation>> = favoritesManager.favorites

    val recordingManager = RadioRecordingManager.getInstance(context)
    val recordings: StateFlow<List<RadioRecording>> = recordingManager.recordings

    private val _currentTab = MutableStateFlow(RadioTab.TOP)
    val currentTab: StateFlow<RadioTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    private val _countries = MutableStateFlow<List<RadioCountry>>(emptyList())
    val countries: StateFlow<List<RadioCountry>> = _countries.asStateFlow()

    private val _tags = MutableStateFlow<List<RadioTag>>(emptyList())
    val tags: StateFlow<List<RadioTag>> = _tags.asStateFlow()

    private val _selectedCountry = MutableStateFlow<RadioCountry?>(null)
    val selectedCountry: StateFlow<RadioCountry?> = _selectedCountry.asStateFlow()

    private val _selectedTag = MutableStateFlow<RadioTag?>(null)
    val selectedTag: StateFlow<RadioTag?> = _selectedTag.asStateFlow()

    private val _sortOrder = MutableStateFlow(RadioSortOrder.MOST_LISTENED)
    val sortOrder: StateFlow<RadioSortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: RadioSortOrder) {
        _sortOrder.value = order
        _stations.value = sortStationsList(_stations.value, order)
    }

    private fun sortStationsList(list: List<RadioStation>, order: RadioSortOrder): List<RadioStation> {
        return when (order) {
            RadioSortOrder.MOST_LISTENED -> list.sortedByDescending { it.clickcount }
            RadioSortOrder.MOST_VOTED -> list.sortedByDescending { it.votes }
            RadioSortOrder.NAME -> list.sortedBy { it.name.lowercase() }
            RadioSortOrder.BITRATE -> list.sortedByDescending { it.bitrate }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _exportImportMessage = MutableStateFlow<String?>(null)
    val exportImportMessage: StateFlow<String?> = _exportImportMessage.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        loadTopStations()
        loadCountries()
        loadTags()
    }

    fun setTab(tab: RadioTab) {
        _currentTab.value = tab
        if (tab == RadioTab.TOP && _stations.value.isEmpty()) {
            loadTopStations()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            if (_selectedCountry.value != null || _selectedTag.value != null) {
                applyFilter()
            } else {
                loadTopStations()
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // Debounce
            _isLoading.value = true
            try {
                val results = RadioBrowserClient.searchStations(
                    query = query,
                    countryCode = _selectedCountry.value?.isoCode ?: "",
                    tag = _selectedTag.value?.name ?: "",
                    limit = 80
                )
                _stations.value = sortStationsList(results, _sortOrder.value)
            } catch (e: Exception) {
                Timber.e(e, "Error searching radio stations")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTopStations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val topVotes = RadioBrowserClient.getTopVoted(50)
                val topClicks = RadioBrowserClient.getTopClicked(50)
                // Combine and distinct by UUID
                val combined = (topVotes + topClicks).distinctBy { it.stationuuid }
                _stations.value = sortStationsList(combined, _sortOrder.value)
            } catch (e: Exception) {
                Timber.e(e, "Error loading top stations")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadCountries() {
        viewModelScope.launch {
            try {
                _countries.value = RadioBrowserClient.getCountries(80)
            } catch (e: Exception) {
                Timber.e(e, "Error loading radio countries")
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                _tags.value = RadioBrowserClient.getTags(60)
            } catch (e: Exception) {
                Timber.e(e, "Error loading radio tags")
            }
        }
    }

    fun selectCountry(country: RadioCountry?) {
        _selectedCountry.value = country
        applyFilter()
    }

    fun selectTag(tag: RadioTag?) {
        _selectedTag.value = tag
        applyFilter()
    }

    private fun applyFilter() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = RadioBrowserClient.searchStations(
                    query = _searchQuery.value,
                    countryCode = _selectedCountry.value?.isoCode ?: "",
                    tag = _selectedTag.value?.name ?: "",
                    limit = 80
                )
                _stations.value = sortStationsList(results, _sortOrder.value)
            } catch (e: Exception) {
                Timber.e(e, "Error applying filter")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(station: RadioStation) {
        favoritesManager.toggleFavorite(station)
    }

    fun isFavorite(stationUuid: String): Boolean {
        return favoritesManager.isFavorite(stationUuid)
    }

    fun exportFavorites(uri: Uri, onSuccess: (Int) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val res = favoritesManager.exportToM3U(uri)
            res.onSuccess { count ->
                _exportImportMessage.value = "Exported $count favorite stations"
                onSuccess(count)
            }.onFailure {
                onError()
            }
        }
    }

    fun importFavorites(uri: Uri, onSuccess: (Int) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val res = favoritesManager.importFromM3U(uri)
            res.onSuccess { count ->
                _exportImportMessage.value = "Imported $count stations"
                onSuccess(count)
            }.onFailure {
                onError()
            }
        }
    }

    fun clearMessage() {
        _exportImportMessage.value = null
    }
}
