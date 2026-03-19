package com.zili.android.musicfreeandroid.data.db.entity

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
)
