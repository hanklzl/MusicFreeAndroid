package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_queue")
data class PlayQueueEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val musicId: String,
    val musicPlatform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val url: String?,
    val artwork: String?,
    val qualitiesJson: String?,
    val sortOrder: Int,
)
