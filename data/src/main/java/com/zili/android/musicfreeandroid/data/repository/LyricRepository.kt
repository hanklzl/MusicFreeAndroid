package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.LyricCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity
import com.zili.android.musicfreeandroid.data.mapper.LyricCache
import com.zili.android.musicfreeandroid.data.mapper.toModel
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import kotlinx.coroutines.CancellationException
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
        logLyricWrite(
            operation = "save_remote_lyric",
            fields = music.logFields() + mapOf(
                "sourceType" to source.typeOrNull(),
                "sourcePlatform" to source.platformOrNull().orEmpty(),
                "sourceMusicId" to source.idOrNull().orEmpty(),
                "sizeBytes" to payloadSizeBytes(payload),
            ),
        ) {
            ensureRow(music)
            lyricCacheDao.saveRemoteLyric(
                platform = music.platform,
                id = music.id,
                remoteRawLrc = payload.rawLrc,
                remoteRawLrcTxt = payload.rawLrcTxt,
                remoteTranslation = payload.translation,
                remoteSourceType = source.typeOrNull(),
                remoteSourcePlatform = source.platformOrNull(),
                remoteSourceMusicId = source.idOrNull(),
                remoteSourceTitle = source.titleOrNull(),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun associateLyric(music: MusicItem, target: MusicItem) {
        logLyricWrite(
            operation = "associate_lyric",
            fields = music.logFields() + mapOf(
                "targetItemId" to target.id,
                "targetItemName" to target.title,
                "targetPlatform" to target.platform,
            ),
        ) {
            ensureRow(music)
            lyricCacheDao.setAssociation(
                platform = music.platform,
                id = music.id,
                associatedMusicJson = converters.musicItemToJson(target),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun clearAssociatedLyric(music: MusicItem) {
        logLyricWrite(
            operation = "clear_associated_lyric",
            fields = music.logFields(),
        ) {
            lyricCacheDao.clearAssociation(music.platform, music.id, System.currentTimeMillis())
        }
    }

    suspend fun importLocalLyric(music: MusicItem, rawText: String, kind: LocalLyricKind) {
        logLyricWrite(
            operation = "import_local_lyric",
            fields = music.logFields() + mapOf(
                "kind" to kind.name,
                "sizeBytes" to rawText.toByteArray().size,
            ),
        ) {
            ensureRow(music)
            when (kind) {
                LocalLyricKind.Raw -> lyricCacheDao.setLocalRawLyric(
                    platform = music.platform,
                    id = music.id,
                    raw = rawText,
                    updatedAt = System.currentTimeMillis(),
                )

                LocalLyricKind.Translation -> lyricCacheDao.setLocalTranslation(
                    platform = music.platform,
                    id = music.id,
                    translation = rawText,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun deleteLocalLyric(music: MusicItem) {
        logLyricWrite(
            operation = "delete_local_lyric",
            fields = music.logFields(),
        ) {
            lyricCacheDao.deleteLocalLyrics(music.platform, music.id, System.currentTimeMillis())
        }
    }

    suspend fun setLyricOffset(music: MusicItem, offsetMs: Long) {
        logLyricWrite(
            operation = "set_lyric_offset",
            fields = music.logFields() + mapOf("offsetMs" to offsetMs),
        ) {
            ensureRow(music)
            lyricCacheDao.setOffset(music.platform, music.id, offsetMs, System.currentTimeMillis())
        }
    }

    suspend fun deleteByPlatform(platform: String) {
        logLyricWrite(
            operation = "delete_lyric_cache_by_platform",
            fields = mapOf("platform" to platform),
        ) {
            lyricCacheDao.deleteByPlatform(platform)
        }
    }

    suspend fun clearAll() {
        logLyricWrite(
            operation = "clear_lyric_cache",
            fields = emptyMap(),
        ) {
            lyricCacheDao.deleteAll()
        }
    }

    private suspend fun ensureRow(music: MusicItem) {
        lyricCacheDao.insertIgnore(baseEntity(music))
    }

    private fun baseEntity(music: MusicItem): LyricCacheEntity = LyricCacheEntity(
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

    private suspend fun <T> logLyricWrite(
        operation: String,
        fields: Map<String, Any?>,
        block: suspend () -> T,
    ): T {
        val baseFields = mapOf("operation" to operation) + fields
        MfLog.detail(LogCategory.LYRICS, "lyrics_write_start", baseFields)
        val startedAt = System.nanoTime()
        return try {
            val result = block()
            MfLog.detail(
                category = LogCategory.LYRICS,
                event = "lyrics_write_success",
                fields = baseFields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            result
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.LYRICS,
                event = "lyrics_write_cancelled",
                fields = baseFields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.LYRICS,
                event = "lyrics_write_failed",
                throwable = error,
                fields = baseFields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "exception",
                ),
            )
            throw error
        }
    }

    private fun MusicItem.logFields(): Map<String, Any?> = mapOf(
        "itemId" to id,
        "itemName" to title,
        "platform" to platform,
    )

    private fun payloadSizeBytes(payload: RawLyricPayload): Int =
        listOfNotNull(payload.rawLrc, payload.rawLrcTxt, payload.translation)
            .sumOf { it.toByteArray().size }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
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

private fun LyricSourceInfo.typeOrNull(): String = when (this) {
    is LyricSourceInfo.Plugin -> "plugin"
    is LyricSourceInfo.AutoSearch -> "auto_search"
    is LyricSourceInfo.Associated -> "associated"
    LyricSourceInfo.Cache -> "cache"
    LyricSourceInfo.LocalRaw -> "local_raw"
    LyricSourceInfo.LocalTranslation -> "local_translation"
}
