package com.hank.musicfree.data.repository

import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.dao.StarredSheetDao
import com.hank.musicfree.data.mapper.toEntity
import com.hank.musicfree.data.mapper.toModel
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StarredSheetRepository @Inject constructor(
    private val starredSheetDao: StarredSheetDao,
    private val converters: Converters,
) {

    fun observeAll(): Flow<List<StarredSheet>> =
        starredSheetDao.observeAll().map { entities -> entities.map { it.toModel(converters) } }

    fun observeIsStarred(id: String, platform: String): Flow<Boolean> =
        starredSheetDao.observeExists(id = id, platform = platform)

    suspend fun upsert(sheet: StarredSheet) {
        logDataWrite(
            operation = "upsert_starred_sheet",
            fields = sheet.logFields(),
        ) {
            val now = System.currentTimeMillis()
            val existing = starredSheetDao.getByIdAndPlatform(
                id = sheet.id,
                platform = sheet.platform,
            )
            val createdAt = existing?.createdAt ?: now
            starredSheetDao.upsert(sheet.toEntity(createdAt = createdAt, updatedAt = now, converters = converters))
        }
    }

    suspend fun toggle(sheet: StarredSheet) {
        logDataWrite(
            operation = "toggle_starred_sheet",
            fields = sheet.logFields(),
        ) {
            val existing = starredSheetDao.getByIdAndPlatform(id = sheet.id, platform = sheet.platform)
            if (existing == null) upsert(sheet) else deleteByIdAndPlatform(id = sheet.id, platform = sheet.platform)
        }
    }

    suspend fun deleteByIdAndPlatform(id: String, platform: String) {
        logDataWrite(
            operation = "delete_starred_sheet",
            fields = mapOf(
                "sheetId" to id,
                "platform" to platform,
            ),
        ) {
            starredSheetDao.deleteByIdAndPlatform(id = id, platform = platform)
        }
    }

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

    private fun StarredSheet.logFields(): Map<String, Any?> = mapOf(
        "sheetId" to id,
        "itemName" to title,
        "platform" to platform,
    )

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
