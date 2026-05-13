package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val db: AppDatabase,
    private val musicDao: MusicDao,
    private val converters: Converters,
) {

    fun observeAll(): Flow<List<MusicItem>> =
        musicDao.observeAll().map { entities -> entities.map { it.toModel(converters) } }

    fun observeByPlatform(platform: String): Flow<List<MusicItem>> =
        musicDao.observeByPlatform(platform).map { entities -> entities.map { it.toModel(converters) } }

    fun observeLocalLibrary(): Flow<List<MusicItem>> =
        musicDao.observeLocalLibrary(LOCAL_PLATFORM).map { entities ->
            entities.map { it.toModel(converters) }
        }

    suspend fun getById(id: String, platform: String): MusicItem? =
        musicDao.getById(id, platform)?.toModel(converters)

    suspend fun insert(item: MusicItem) =
        logDataWrite(
            operation = "insert_music",
            fields = item.logFields(),
            resultFields = { mapOf("count" to 1) },
        ) {
            musicDao.insert(item.toEntity(converters))
        }

    suspend fun insertAll(items: List<MusicItem>) =
        logDataWrite(
            operation = "insert_music_all",
            fields = mapOf("count" to items.size),
            resultFields = { mapOf("count" to items.size) },
        ) {
            musicDao.insertAll(items.map { it.toEntity(converters) })
        }

    suspend fun replaceByPlatform(platform: String, items: List<MusicItem>) =
        logDataWrite(
            operation = "replace_music_by_platform",
            fields = mapOf(
                "platform" to platform,
                "count" to items.size,
            ),
            resultFields = { mapOf("count" to items.size) },
        ) {
            musicDao.replaceByPlatform(platform, items.map { it.toEntity(converters) })
        }

    suspend fun update(item: MusicItem) =
        logDataWrite(
            operation = "update_music",
            fields = item.logFields(),
            resultFields = { mapOf("count" to 1) },
        ) {
            musicDao.update(item.toEntity(converters))
        }

    suspend fun delete(item: MusicItem) =
        logDataWrite(
            operation = "delete_music",
            fields = item.logFields(),
            resultFields = { mapOf("count" to 1) },
        ) {
            musicDao.delete(item.toEntity(converters))
        }

    suspend fun commitDownloadedTrack(item: MusicItem, downloaded: DownloadedTrackEntity) =
        logDataWrite(
            operation = "commit_downloaded_track",
            fields = item.logFields() + mapOf(
                "quality" to downloaded.quality,
                "pathType" to "mediastore",
            ),
            resultFields = { mapOf("count" to 1) },
        ) {
            db.withTransaction {
                val existing = musicDao.getById(item.id, item.platform)?.toModel(converters)
                val merged = existing?.let { current ->
                    current.copy(
                        title = item.title.takeIf { it.isNotBlank() } ?: current.title,
                        artist = item.artist.takeIf { it.isNotBlank() } ?: current.artist,
                        album = item.album ?: current.album,
                        duration = item.duration.takeIf { it > 0L } ?: current.duration,
                        url = item.url ?: current.url,
                        artwork = item.artwork ?: current.artwork,
                        qualities = item.qualities ?: current.qualities,
                        raw = current.raw + item.raw,
                    )
                } ?: item
                val localItem = merged.copy(
                    localPath = downloaded.mediaStoreUri,
                    raw = merged.raw + mapOf(
                        "downloaded" to true,
                        "downloadQuality" to downloaded.quality,
                        "downloadedAt" to downloaded.downloadedAt,
                        "mediaStoreUri" to downloaded.mediaStoreUri,
                    ),
                )
                db.downloadedTrackDao().insert(downloaded)
                musicDao.upsert(localItem.toEntity(converters))
            }
        }

    suspend fun removeFromLocalLibrary(item: MusicItem) =
        logDataWrite(
            operation = "remove_from_local_library",
            fields = item.logFields(),
            resultFields = { mapOf("count" to 1) },
        ) {
            db.withTransaction {
                if (item.platform == LOCAL_PLATFORM) {
                    musicDao.delete(item.toEntity(converters))
                } else {
                    db.downloadedTrackDao().deleteByKey(item.id, item.platform)
                    val existing = musicDao.getById(item.id, item.platform)?.toModel(converters)
                    if (existing != null) {
                        musicDao.update(
                            existing.copy(
                                localPath = null,
                                raw = existing.raw - "downloaded" - "downloadQuality" - "downloadedAt" - "mediaStoreUri",
                            ).toEntity(converters)
                        )
                    }
                }
            }
        }

    suspend fun deleteByPlatform(platform: String) =
        logDataWrite(
            operation = "delete_music_by_platform",
            fields = mapOf("platform" to platform),
        ) {
            musicDao.deleteByPlatform(platform)
        }

    suspend fun count(): Int = musicDao.count()

    private suspend fun <T> logDataWrite(
        operation: String,
        fields: Map<String, Any?> = emptyMap(),
        resultFields: (T) -> Map<String, Any?> = { emptyMap() },
        block: suspend () -> T,
    ): T {
        val baseFields = mapOf("operation" to operation) + fields
        MfLog.detail(LogCategory.DATA, "data_write_start", baseFields)
        val startedAt = System.nanoTime()
        return try {
            val result = block()
            MfLog.detail(
                category = LogCategory.DATA,
                event = "data_write_success",
                fields = baseFields + resultFields(result) + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            result
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.DATA,
                event = "data_write_cancelled",
                fields = baseFields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "data_write_failed",
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

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val LOCAL_PLATFORM = "local"
    }
}
