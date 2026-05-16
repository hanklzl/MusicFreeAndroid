package com.hank.musicfree.core.model

/**
 * Core music item. The combination of [id] + [platform] uniquely identifies a track.
 * [duration] is in milliseconds (original RN version uses seconds — convert at boundaries).
 */
data class MusicItem(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val url: String?,
    val artwork: String?,
    val qualities: Map<PlayQuality, QualityInfo>?,
    val raw: Map<String, Any?> = emptyMap(),
    val addedAt: Long = 0L,
    val localPath: String? = null,
)
