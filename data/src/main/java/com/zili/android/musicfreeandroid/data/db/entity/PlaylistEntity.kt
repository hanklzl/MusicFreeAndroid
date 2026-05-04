package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverUri: String?,
    val description: String? = null,
    @ColumnInfo(defaultValue = "Manual") val sortMode: String = "Manual",
    val createdAt: Long,
    val updatedAt: Long,
)
