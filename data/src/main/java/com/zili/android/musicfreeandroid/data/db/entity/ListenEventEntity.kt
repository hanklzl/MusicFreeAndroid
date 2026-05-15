package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listen_event",
    indices = [
        Index("playedAtMs"),
        Index(value = ["musicId", "platform"]),
    ],
)
data class ListenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playedAtMs: Long,
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val durationMs: Long,
    val playedSeconds: Int,
    val completed: Boolean,
    val language: String?,
    val genre: String?,
)
