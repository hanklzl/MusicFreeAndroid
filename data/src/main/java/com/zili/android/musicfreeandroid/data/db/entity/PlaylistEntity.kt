package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
