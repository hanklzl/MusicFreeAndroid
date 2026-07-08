package com.hank.musicfree.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

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
