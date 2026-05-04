package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(tableName = "download_tasks", primaryKeys = ["id", "platform"])
data class DownloadTaskEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artwork: String?,
    val durationMs: Long,
    val targetQuality: String,            // "low"/"standard"/"high"/"super"
    val status: String,                   // PENDING / PREPARING / DOWNLOADING / FAILED
    val errorReason: String?,             // FailToFetchSource / NoWritePermission / Unknown / NotAllowToDownloadInCellular
    val resolvedUrl: String?,
    val resolvedHeadersJson: String?,
    val fileSize: Long?,
    val downloadedSize: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
