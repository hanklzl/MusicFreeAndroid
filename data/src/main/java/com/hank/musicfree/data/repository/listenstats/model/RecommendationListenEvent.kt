package com.hank.musicfree.data.repository.listenstats.model

/** Minimal listening signal exposed to recommendation code without leaking Room entities. */
data class RecommendationListenEvent(
    val playedAtMs: Long,
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val playedSeconds: Int,
    val completed: Boolean,
    val language: String?,
    val genre: String?,
)
