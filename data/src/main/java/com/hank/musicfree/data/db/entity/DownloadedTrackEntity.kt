package com.hank.musicfree.data.db.entity

import androidx.room.Entity

@Entity(tableName = "downloaded_tracks", primaryKeys = ["id", "platform"])
data class DownloadedTrackEntity(
    val id: String,
    val platform: String,
    val mediaStoreUri: String,
    val relativePath: String,
    val mimeType: String,
    val quality: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
)
