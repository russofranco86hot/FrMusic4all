/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object RadioBrowserClient {
    private const val USER_AGENT = "FrMusic4all/1.2.0"
    private val MIRRORS = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun fetchString(endpointPath: String): String? = withContext(Dispatchers.IO) {
        for (mirror in MIRRORS) {
            val url = "$mirror$endpointPath"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext response.body?.string()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "RadioBrowser mirror failed: $mirror for $endpointPath")
            }
        }
        null
    }

    suspend fun getTopVoted(limit: Int = 60): List<RadioStation> = withContext(Dispatchers.IO) {
        val body = fetchString("/json/stations/topvote?limit=$limit&hidebroken=true") ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<RadioStation>>(body)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode top voted radio stations")
            emptyList()
        }
    }

    suspend fun getTopClicked(limit: Int = 60): List<RadioStation> = withContext(Dispatchers.IO) {
        val body = fetchString("/json/stations/topclick?limit=$limit&hidebroken=true") ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<RadioStation>>(body)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode top clicked radio stations")
            emptyList()
        }
    }

    suspend fun searchStations(
        query: String = "",
        countryCode: String = "",
        tag: String = "",
        limit: Int = 60,
    ): List<RadioStation> = withContext(Dispatchers.IO) {
        val queryParams = mutableListOf<String>()
        if (query.isNotBlank()) queryParams.add("name=" + URLEncoder.encode(query.trim(), "UTF-8"))
        if (countryCode.isNotBlank()) queryParams.add("countrycode=" + URLEncoder.encode(countryCode.trim(), "UTF-8"))
        if (tag.isNotBlank()) queryParams.add("tag=" + URLEncoder.encode(tag.trim(), "UTF-8"))
        queryParams.add("limit=$limit")
        queryParams.add("order=clickcount")
        queryParams.add("reverse=true")
        queryParams.add("hidebroken=true")

        val path = "/json/stations/search?" + queryParams.joinToString("&")
        val body = fetchString(path) ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<RadioStation>>(body)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode searched radio stations")
            emptyList()
        }
    }

    suspend fun getCountries(limit: Int = 80): List<RadioCountry> = withContext(Dispatchers.IO) {
        val body = fetchString("/json/countries?order=stationcount&reverse=true&limit=$limit") ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<RadioCountry>>(body)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode radio countries")
            emptyList()
        }
    }

    suspend fun getTags(limit: Int = 60): List<RadioTag> = withContext(Dispatchers.IO) {
        val body = fetchString("/json/tags?order=stationcount&reverse=true&limit=$limit") ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<RadioTag>>(body)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode radio tags")
            emptyList()
        }
    }

    suspend fun resolveClick(stationUuid: String): String? = withContext(Dispatchers.IO) {
        val body = fetchString("/json/url/$stationUuid") ?: return@withContext null
        try {
            val obj = json.decodeFromString<RadioClickResult>(body)
            if (obj.ok) obj.url else null
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse radio click result for $stationUuid")
            null
        }
    }

    @kotlinx.serialization.Serializable
    private data class RadioClickResult(
        val ok: Boolean = false,
        val url: String? = null,
    )
}
