package com.hank.musicfree.data.repository

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.dao.PlayQueueDao
import com.hank.musicfree.data.mapper.toMusicItem
import com.hank.musicfree.data.mapper.toPlayQueueEntity
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayQueueRepository @Inject constructor(
    private val playQueueDao: PlayQueueDao,
    private val converters: Converters,
) {

    fun observeQueue(): Flow<List<MusicItem>> =
        playQueueDao.observeAll().map { entities ->
            entities.map { it.toMusicItem(converters) }
        }

    /**
     * Most-recent queue items as "${platform}:${id}" keys, capped at [limit].
     * Used as a pinning signal so recently played songs survive cache eviction.
     */
    fun observeRecentKeys(limit: Int = 50): Flow<Set<String>> =
        observeQueue().map { items ->
            items.take(limit).mapTo(HashSet(items.size.coerceAtMost(limit))) {
                "${it.platform}:${it.id}"
            }
        }

    suspend fun getQueue(): List<MusicItem> =
        playQueueDao.getAll().map { it.toMusicItem(converters) }

    suspend fun saveQueue(items: List<MusicItem>) {
        logDataWrite(
            operation = "save_play_queue",
            fields = mapOf("count" to items.size),
        ) {
            val entities = items.mapIndexed { index, item ->
                item.toPlayQueueEntity(sortOrder = index, converters = converters)
            }
            playQueueDao.replaceAll(entities)
        }
    }

    suspend fun clearQueue() = logDataWrite(
        operation = "clear_play_queue",
        fields = emptyMap(),
    ) {
        playQueueDao.clearAll()
    }

    suspend fun count(): Int = playQueueDao.count()

    private suspend fun <T> logDataWrite(
        operation: String,
        fields: Map<String, Any?>,
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
                fields = baseFields + mapOf(
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

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
