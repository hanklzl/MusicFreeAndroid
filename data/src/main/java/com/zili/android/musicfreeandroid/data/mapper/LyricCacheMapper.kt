package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity

data class LyricCache(
    val musicId: String,
    val musicPlatform: String,
    val remotePayload: RawLyricPayload?,
    val remoteSourceType: String?,
    val remoteSourcePlatform: String?,
    val remoteSourceMusicId: String?,
    val remoteSourceTitle: String?,
    val localRawLrc: String?,
    val localTranslation: String?,
    val associatedMusic: MusicItem?,
    val userOffsetMs: Long,
)

fun LyricCacheEntity.toModel(converters: Converters): LyricCache = LyricCache(
    musicId = musicId,
    musicPlatform = musicPlatform,
    remotePayload = if (remoteRawLrc != null || remoteRawLrcTxt != null || remoteTranslation != null) {
        RawLyricPayload(remoteRawLrc, remoteRawLrcTxt, remoteTranslation)
    } else {
        null
    },
    remoteSourceType = remoteSourceType,
    remoteSourcePlatform = remoteSourcePlatform,
    remoteSourceMusicId = remoteSourceMusicId,
    remoteSourceTitle = remoteSourceTitle,
    localRawLrc = localRawLrc,
    localTranslation = localTranslation,
    associatedMusic = runCatching { converters.jsonToMusicItem(associatedMusicJson) }.getOrNull(),
    userOffsetMs = userOffsetMs,
)
