package com.zili.android.musicfreeandroid.data.repository

import androidx.collection.LruCache
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // In-memory LRU tier keyed by "${platform}@${id}" -> parsed sourcesJson.
    // Provides single-track hot-path lookup without DB round-trips. Sized to be
    // small enough to never dominate memory; DB is the source of truth.
    private val memory = LruCache<String, JSONObject>(MEMORY_LIMIT)

    /**
     * Serializes every public suspend op so that the DB + memory steps are observed
     * atomically by other coroutines. Without this, a concurrent `get` could read
     * stale memory after a `deleteEntry` has already cleared the DB row but before
     * the memory invalidation lands.
     */
    private val mutex = Mutex()

    /** Test-only flag: whether the most recent successful [get] was served from memory. */
    @Volatile
    internal var lastHitFromMemory: Boolean = false
        private set

    suspend fun get(item: MusicItem, quality: PlayQuality): CachedSource? = mutex.withLock {
        val key = memoryKey(item.platform, item.id)
        memory.get(key)?.let { obj ->
            val source = readQuality(obj, quality)
            if (source != null) {
                lastHitFromMemory = true
                return@withLock source
            }
            // Memory entry exists but doesn't carry this quality — fall through to DB
        }
        val row = dao.get(item.platform, item.id)
        if (row == null) {
            lastHitFromMemory = false
            return@withLock null
        }
        val obj = parseSourcesJson(row.sourcesJson, item, quality)
        if (obj == null) {
            lastHitFromMemory = false
            return@withLock null
        }
        // Seed memory layer for next read
        memory.put(key, obj)
        val source = readQuality(obj, quality)
        lastHitFromMemory = false
        source
    }

    suspend fun put(item: MusicItem, quality: PlayQuality, source: MediaSourceResult) = mutex.withLock {
        logDataWrite(
            operation = "put_media_cache",
            fields = item.logFields() + mapOf(
                "quality" to quality.name.lowercase(),
                "url" to source.url,
                "host" to LogFields.host(source.url),
            ),
        ) {
            val existing = dao.get(item.platform, item.id)
            // Defensive: treat a malformed existing row as if it didn't exist, so a single
            // corrupt cache entry doesn't propagate as a playback exception (Important #5).
            val json = existing?.let { parseSourcesJson(it.sourcesJson, item, quality) } ?: JSONObject()
            val q = JSONObject().apply {
                put("url", source.url)
                source.headers?.let { put("headers", JSONObject(it as Map<*, *>)) }
                source.userAgent?.let { put("userAgent", it) }
            }
            json.put(quality.name, q)
            dao.upsert(MediaCacheEntity(item.platform, item.id, json.toString(), now()))
            // Invalidate memory so the next read re-seeds from authoritative DB state
            memory.remove(memoryKey(item.platform, item.id))
            if (dao.count() >= LIMIT) pruneOldest()
        }
    }

    /**
     * Remove a single quality key from one row. If no quality keys remain the row is
     * deleted entirely. Memory layer is synced to match.
     */
    suspend fun deleteEntry(platform: String, id: String, quality: PlayQuality) = mutex.withLock {
        val existing = dao.get(platform, id) ?: run {
            memory.remove(memoryKey(platform, id))
            return@withLock
        }
        val obj = parseSourcesJson(existing.sourcesJson)
        if (obj == null || !obj.has(quality.name)) {
            // Nothing to strip; ensure memory mirrors DB
            if (obj != null) memory.put(memoryKey(platform, id), obj)
            return@withLock
        }
        obj.remove(quality.name)
        if (obj.length() == 0) {
            dao.delete(platform, id)
            memory.remove(memoryKey(platform, id))
        } else {
            dao.upsert(MediaCacheEntity(platform, id, obj.toString(), now()))
            memory.put(memoryKey(platform, id), obj)
        }
    }

    /** Delete all rows for the platform from DB and from memory. */
    suspend fun deleteByPlatform(platform: String) = mutex.withLock {
        dao.deleteByPlatform(platform)
        val prefix = "$platform@"
        val keysToDrop = memory.snapshot().keys.filter { it.startsWith(prefix) }
        keysToDrop.forEach { memory.remove(it) }
    }

    /**
     * Parse the stored sources JSON. Returns null on malformed payloads and emits a
     * structured `media_cache_parse_failed` log so corrupt rows are visible without
     * propagating an exception to the caller.
     */
    private fun parseSourcesJson(
        sourcesJson: String,
        item: MusicItem? = null,
        quality: PlayQuality? = null,
    ): JSONObject? = try {
        JSONObject(sourcesJson)
    } catch (error: Throwable) {
        MfLog.error(
            category = LogCategory.DATA,
            event = "media_cache_parse_failed",
            throwable = error,
            fields = (item?.logFields() ?: emptyMap()) + mapOf(
                "quality" to quality?.name?.lowercase(),
                "result" to LogFields.Result.FAILURE,
                "reason" to "invalid_cache_json",
            ),
        )
        null
    }

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
        const val MEMORY_LIMIT = 200
    }

    private fun readQuality(obj: JSONObject, quality: PlayQuality): CachedSource? {
        if (!obj.has(quality.name)) return null
        return runCatching {
            val q = obj.getJSONObject(quality.name)
            val url = q.optString("url").takeIf { it.isNotEmpty() } ?: return@runCatching null
            CachedSource(
                url = url,
                headers = q.optJSONObject("headers")?.toStringMap(),
                userAgent = q.optString("userAgent").takeIf { it.isNotEmpty() },
            )
        }.getOrNull()
    }

    private fun memoryKey(platform: String, id: String): String = "$platform@$id"

    private fun JSONObject.toStringMap(): Map<String, String> =
        keys().asSequence().associateWith { getString(it) }
}
