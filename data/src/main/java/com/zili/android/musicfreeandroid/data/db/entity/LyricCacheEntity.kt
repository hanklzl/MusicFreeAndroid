package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(tableName = "lyric_cache", primaryKeys = ["musicId", "musicPlatform"])
data class LyricCacheEntity(
    val musicId: String,
    val musicPlatform: String,
    val remoteRawLrc: String?,
    val remoteRawLrcTxt: String?,
    val remoteTranslation: String?,
    val remoteSourceType: String?,
    val remoteSourcePlatform: String?,
    val remoteSourceMusicId: String?,
    val remoteSourceTitle: String?,
    val localRawLrc: String?,
    val localTranslation: String?,
    val associatedMusicJson: String?,
    val userOffsetMs: Long,
    val updatedAt: Long,
)
