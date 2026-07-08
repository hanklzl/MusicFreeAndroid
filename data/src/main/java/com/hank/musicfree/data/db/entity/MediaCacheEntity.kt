package com.hank.musicfree.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index

/**
 * Per-track cached media sources keyed by (platform, id).
 * [sourcesJson] is a JSON object of shape:
 *   { "STANDARD": { "url": "...", "headers": {..}, "userAgent": "..." }, ... }
 * Quality keys are PlayQuality enum names (uppercase).
 */
@Entity(
    tableName = "media_cache",
    primaryKeys = ["platform", "id"],
    indices = [Index("updated_at")],
)
data class MediaCacheEntity(
    val platform: String,
    val id: String,
    val sourcesJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
)
