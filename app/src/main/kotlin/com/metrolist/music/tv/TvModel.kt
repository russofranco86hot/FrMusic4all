/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.tv

data class TvChannel(
    val id: String,
    val name: String,
    val category: TvCategory,
    val description: String,
    val youtubeHandle: String? = null,
    val channelId: String? = null,
    val directStreamUrl: String? = null,
    val logoUrl: String,
    val isFavorite: Boolean = false,
)

enum class TvCategory(val displayName: String) {
    STREAMING("Streaming"),
    NEWS("Noticias & Aire"),
    MUSIC("Música"),
    ALL("Todos"),
}
