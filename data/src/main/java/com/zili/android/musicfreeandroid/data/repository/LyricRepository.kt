package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.LyricCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity
import com.zili.android.musicfreeandroid.data.mapper.LyricCache
import com.zili.android.musicfreeandroid.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class LocalLyricKind { Raw, Translation }

@Singleton
class LyricRepository @Inject constructor(
    private val lyricCacheDao: LyricCacheDao,
    private val converters: Converters,
) {
    fun observeCache(music: MusicItem): Flow<LyricCache?> =
        lyricCacheDao.observeByKey(music.platform, music.id).map { it?.toModel(converters) }

    suspend fun getCache(music: MusicItem): LyricCache? =
        lyricCacheDao.getByKey(music.platform, music.id)?.toModel(converters)

    suspend fun saveRemoteLyric(music: MusicItem, source: LyricSourceInfo, payload: RawLyricPayload) {
        val current = lyricCacheDao.getByKey(music.platform, music.id)
        lyricCacheDao.upsert(
            baseEntity(music, current).copy(
                remoteRawLrc = payload.rawLrc,
                remoteRawLrcTxt = payload.rawLrcTxt,
                remoteTranslation = payload.translation,
                remoteSourceType = source::class.simpleName,
                remoteSourcePlatform = source.platformOrNull(),
                remoteSourceMusicId = source.idOrNull(),
                remoteSourceTitle = source.titleOrNull(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun associateLyric(music: MusicItem, target: MusicItem) {
        val current = lyricCacheDao.getByKey(music.platform, music.id)
        lyricCacheDao.upsert(
            baseEntity(music, current).copy(
                associatedMusicJson = converters.musicItemToJson(target),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearAssociatedLyric(music: MusicItem) {
        ensureRow(music)
        lyricCacheDao.clearAssociation(music.platform, music.id, System.currentTimeMillis())
    }

    suspend fun importLocalLyric(music: MusicItem, rawText: String, kind: LocalLyricKind) {
        val current = lyricCacheDao.getByKey(music.platform, music.id)
        val base = baseEntity(music, current)
        lyricCacheDao.upsert(
            when (kind) {
                LocalLyricKind.Raw -> base.copy(localRawLrc = rawText, updatedAt = System.currentTimeMillis())
                LocalLyricKind.Translation -> base.copy(localTranslation = rawText, updatedAt = System.currentTimeMillis())
            },
        )
    }

    suspend fun deleteLocalLyric(music: MusicItem) {
        ensureRow(music)
        lyricCacheDao.deleteLocalLyrics(music.platform, music.id, System.currentTimeMillis())
    }

    suspend fun setLyricOffset(music: MusicItem, offsetMs: Long) {
        ensureRow(music)
        lyricCacheDao.setOffset(music.platform, music.id, offsetMs, System.currentTimeMillis())
    }

    private suspend fun ensureRow(music: MusicItem) {
        if (lyricCacheDao.getByKey(music.platform, music.id) == null) {
            lyricCacheDao.upsert(baseEntity(music, null))
        }
    }

    private fun baseEntity(music: MusicItem, current: LyricCacheEntity?): LyricCacheEntity =
        current ?: LyricCacheEntity(
            musicId = music.id,
            musicPlatform = music.platform,
            remoteRawLrc = null,
            remoteRawLrcTxt = null,
            remoteTranslation = null,
            remoteSourceType = null,
            remoteSourcePlatform = null,
            remoteSourceMusicId = null,
            remoteSourceTitle = null,
            localRawLrc = null,
            localTranslation = null,
            associatedMusicJson = null,
            userOffsetMs = 0L,
            updatedAt = System.currentTimeMillis(),
        )
}

private fun LyricSourceInfo.platformOrNull(): String? = when (this) {
    is LyricSourceInfo.Plugin -> platform
    is LyricSourceInfo.AutoSearch -> platform
    is LyricSourceInfo.Associated -> platform
    LyricSourceInfo.Cache -> null
    LyricSourceInfo.LocalRaw -> null
    LyricSourceInfo.LocalTranslation -> null
}

private fun LyricSourceInfo.idOrNull(): String? = when (this) {
    is LyricSourceInfo.AutoSearch -> id
    is LyricSourceInfo.Associated -> id
    else -> null
}

private fun LyricSourceInfo.titleOrNull(): String? = when (this) {
    is LyricSourceInfo.AutoSearch -> title
    is LyricSourceInfo.Associated -> title
    else -> null
}
