package com.hank.musicfree.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

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
