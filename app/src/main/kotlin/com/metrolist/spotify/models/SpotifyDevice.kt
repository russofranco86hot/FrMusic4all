package com.metrolist.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyDevicesResponse(
    val devices: List<SpotifyDevice> = emptyList(),
)

@Serializable
data class SpotifyDevice(
    val id: String? = null,
    val name: String,
    val type: String = "Speaker",
    @SerialName("is_active")
    val isActive: Boolean = false,
    @SerialName("is_private_session")
    val isPrivateSession: Boolean = false,
    @SerialName("is_restricted")
    val isRestricted: Boolean = false,
    @SerialName("volume_percent")
    val volumePercent: Int? = null,
    @SerialName("supports_volume")
    val supportsVolume: Boolean = false,
)
