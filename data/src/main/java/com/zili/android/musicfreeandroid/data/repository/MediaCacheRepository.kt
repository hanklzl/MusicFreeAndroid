package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.dao.MediaCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.MediaCacheEntity
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

data class CachedSource(
    val url: String,
    val headers: Map<String, String>?,
    val userAgent: String?,
)

@Singleton
class MediaCacheRepository @Inject constructor(
    private val dao: MediaCacheDao,
) {
    // Secondary constructor for tests to inject a fake clock
    internal constructor(dao: MediaCacheDao, now: () -> Long) : this(dao) {
        this.nowFn = now
    }

    private var nowFn: () -> Long = System::currentTimeMillis
    private fun now() = nowFn()
    suspend fun get(item: MusicItem, quality: PlayQuality): CachedSource? {
        val row = dao.get(item.platform, item.id) ?: return null
        return try {
            val obj = JSONObject(row.sourcesJson)
            if (!obj.has(quality.name)) return null
            val q = obj.getJSONObject(quality.name)
            CachedSource(
                url = q.optString("url").takeIf { it.isNotEmpty() } ?: return null,
                headers = q.optJSONObject("headers")?.toStringMap(),
                userAgent = q.optString("userAgent").takeIf { it.isNotEmpty() },
            )
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "media_cache_parse_failed",
                throwable = error,
                fields = item.logFields() + mapOf(
                    "quality" to quality.name.lowercase(),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "invalid_cache_json",
                ),
            )
            null
        }
    }

    suspend fun put(item: MusicItem, quality: PlayQuality, source: MediaSourceResult) {
        logDataWrite(
            operation = "put_media_cache",
            fields = item.logFields() + mapOf(
                "quality" to quality.name.lowercase(),
                "url" to source.url,
                "host" to LogFields.host(source.url),
            ),
        ) {
            val existing = dao.get(item.platform, item.id)
            val json = if (existing != null) JSONObject(existing.sourcesJson) else JSONObject()
            val q = JSONObject().apply {
                put("url", source.url)
                source.headers?.let { put("headers", JSONObject(it as Map<*, *>)) }
                source.userAgent?.let { put("userAgent", it) }
            }
            json.put(quality.name, q)
            dao.upsert(MediaCacheEntity(item.platform, item.id, json.toString(), now()))
            if (dao.count() >= LIMIT) pruneOldest()
        }
    }

    private fun JSONObject.toStringMap(): Map<String, String> =
        keys().asSequence().associateWith { getString(it) }

    private suspend fun pruneOldest() {
        val startedAt = System.nanoTime()
        try {
            dao.deleteOldest(LIMIT / 2)
            MfLog.detail(
                category = LogCategory.DATA,
                event = "media_cache_prune",
                fields = mapOf(
                    "count" to LIMIT / 2,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "media_cache_prune",
                throwable = error,
                fields = mapOf(
                    "count" to LIMIT / 2,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "exception",
                ),
            )
            throw error
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

    private fun MusicItem.logFields(): Map<String, Any?> = mapOf(
        "itemId" to id,
        "itemName" to title,
        "platform" to platform,
    )

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    companion object {
        const val LIMIT = 800
    }
}
