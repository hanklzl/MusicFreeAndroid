package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.mapper.toMusicItem
import com.zili.android.musicfreeandroid.data.mapper.toPlayQueueEntity
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
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
