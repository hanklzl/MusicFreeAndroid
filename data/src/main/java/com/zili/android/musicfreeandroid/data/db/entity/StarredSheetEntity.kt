package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "starred_sheets",
    primaryKeys = ["id", "platform"],
)
data class StarredSheetEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
