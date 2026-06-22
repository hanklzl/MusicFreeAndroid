package com.hank.musicfree.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "byte_cache_status",
    primaryKeys = ["platform", "music_id", "quality"],
    indices = [Index("updated_at")],
)
data class ByteCacheStatusEntity(
    val platform: String,
    @ColumnInfo(name = "music_id") val musicId: String,
    val quality: String,
    val status: String,
    @ColumnInfo(name = "cached_bytes", defaultValue = "0") val cachedBytes: Long,
    @ColumnInfo(name = "content_length") val contentLength: Long?,
    @ColumnInfo(name = "validation_method") val validationMethod: String,
    @ColumnInfo(name = "source_fingerprint") val sourceFingerprint: String?,
    @ColumnInfo(name = "invalid_reason") val invalidReason: String?,
    @ColumnInfo(name = "verified_at") val verifiedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
