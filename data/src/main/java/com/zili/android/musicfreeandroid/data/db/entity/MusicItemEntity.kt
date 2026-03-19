package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(tableName = "music_items", primaryKeys = ["id", "platform"])
data class MusicItemEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val url: String?,
    val artwork: String?,
    val qualitiesJson: String?,
)
