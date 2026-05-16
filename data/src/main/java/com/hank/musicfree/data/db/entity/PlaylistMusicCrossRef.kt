package com.hank.musicfree.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_music",
    primaryKeys = ["playlistId", "musicId", "musicPlatform"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MusicItemEntity::class,
            parentColumns = ["id", "platform"],
            childColumns = ["musicId", "musicPlatform"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("musicId", "musicPlatform"),
    ],
)
data class PlaylistMusicCrossRef(
    val playlistId: String,
    val musicId: String,
    val musicPlatform: String,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0") val addedAt: Long = 0L,
)
