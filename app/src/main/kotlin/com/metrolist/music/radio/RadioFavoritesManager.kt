/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.radio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

class RadioFavoritesManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val favoritesFile = File(context.filesDir, "radio_favorites.json")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _favorites = MutableStateFlow<List<RadioStation>>(emptyList())
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        scope.launch {
            mutex.withLock {
                if (favoritesFile.exists()) {
                    try {
                        val text = favoritesFile.readText()
                        val list = json.decodeFromString<List<RadioStation>>(text)
                        _favorites.value = list
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load radio favorites")
                    }
                }
            }
        }
    }

    private suspend fun saveFavoritesInternal(list: List<RadioStation>) {
        try {
            val text = json.encodeToString(list)
            favoritesFile.writeText(text)
            _favorites.value = list
        } catch (e: Exception) {
            Timber.e(e, "Failed to save radio favorites")
        }
    }

    fun isFavorite(uuid: String): Boolean {
        return _favorites.value.any { it.stationuuid == uuid }
    }

    fun toggleFavorite(station: RadioStation) {
        scope.launch {
            mutex.withLock {
                val current = _favorites.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.stationuuid == station.stationuuid }
                if (existingIndex >= 0) {
                    current.removeAt(existingIndex)
                } else {
                    current.add(0, station)
                }
                saveFavoritesInternal(current)
            }
        }
    }

    suspend fun exportToM3U(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val list = _favorites.value
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write("#EXTM3U\n")
                        for (station in list) {
                            val logo = station.favicon.orEmpty()
                            val country = station.country.orEmpty()
                            val cleanName = station.name.replace(",", " ")
                            writer.write("#EXTINF:-1 tvg-id=\"${station.stationuuid}\" tvg-name=\"$cleanName\" tvg-logo=\"$logo\" group-title=\"$country\",$cleanName\n")
                            writer.write("${station.streamUrl}\n")
                        }
                    }
                }
                Result.success(list.size)
            } catch (e: Exception) {
                Timber.e(e, "Error exporting radio favorites to M3U")
                Result.failure(e)
            }
        }
    }

    suspend fun importFromM3U(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val importedStations = mutableListOf<RadioStation>()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var currentName = ""
                        var currentId = ""
                        var currentLogo: String? = null
                        var currentGroup: String? = null

                        reader.forEachLine { rawLine ->
                            val line = rawLine.trim()
                            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                                // Extract tvg-id, tvg-name, tvg-logo, group-title
                                currentId = extractAttribute(line, "tvg-id") ?: UUID.randomUUID().toString()
                                val tvgName = extractAttribute(line, "tvg-name")
                                currentLogo = extractAttribute(line, "tvg-logo")
                                currentGroup = extractAttribute(line, "group-title")

                                // Name after comma
                                val commaIndex = line.lastIndexOf(',')
                                currentName = if (commaIndex != -1 && commaIndex < line.length - 1) {
                                    line.substring(commaIndex + 1).trim()
                                } else {
                                    tvgName ?: "Radio Station"
                                }
                            } else if (line.isNotBlank() && !line.startsWith("#")) {
                                val streamUrl = line
                                if (streamUrl.startsWith("http://", ignoreCase = true) ||
                                    streamUrl.startsWith("https://", ignoreCase = true)
                                ) {
                                    val station = RadioStation(
                                        stationuuid = if (currentId.isNotBlank()) currentId else UUID.randomUUID().toString(),
                                        name = if (currentName.isNotBlank()) currentName else "Radio Station",
                                        url = streamUrl,
                                        urlResolved = streamUrl,
                                        favicon = currentLogo,
                                        country = currentGroup,
                                    )
                                    importedStations.add(station)
                                }
                                // Reset parsing state for next station
                                currentName = ""
                                currentId = ""
                                currentLogo = null
                                currentGroup = null
                            }
                        }
                    }
                }

                if (importedStations.isNotEmpty()) {
                    val currentMap = _favorites.value.associateBy { it.stationuuid }.toMutableMap()
                    for (st in importedStations) {
                        currentMap[st.stationuuid] = st
                    }
                    saveFavoritesInternal(currentMap.values.toList())
                }

                Result.success(importedStations.size)
            } catch (e: Exception) {
                Timber.e(e, "Error importing radio favorites from M3U")
                Result.failure(e)
            }
        }
    }

    private fun extractAttribute(line: String, attribute: String): String? {
        val pattern = Regex("""$attribute="([^"]*)"""", RegexOption.IGNORE_CASE)
        val match = pattern.find(line)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }
}
