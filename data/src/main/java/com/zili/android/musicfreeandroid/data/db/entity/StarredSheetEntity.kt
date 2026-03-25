package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "starred_sheets")
data class StarredSheetEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
