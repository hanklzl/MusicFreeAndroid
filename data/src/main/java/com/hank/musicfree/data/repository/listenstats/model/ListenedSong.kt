package com.hank.musicfree.data.repository.listenstats.model

data class ListenedSong(
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val playCount: Int,
    val totalSec: Long,
)

data class DetailFilter(val mode: DetailMode, val filterValue: String? = null)
