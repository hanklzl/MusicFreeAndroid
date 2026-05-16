package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RawLyricPayload
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.LyricCacheEntity

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
