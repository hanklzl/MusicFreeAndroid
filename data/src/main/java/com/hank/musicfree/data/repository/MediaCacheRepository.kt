package com.hank.musicfree.data.repository

import androidx.collection.LruCache
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.cache.EmptyByteCacheStatusStore
import com.hank.musicfree.core.cache.SimpleCacheEvictor
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.db.dao.MediaCacheDao
import com.hank.musicfree.data.db.entity.MediaCacheEntity
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class CachedSource(
    val url: String,
    val headers: Map<String, String>?,
    val userAgent: String?,
)

@Singleton
class MediaCacheRepository private constructor(
    private val dao: MediaCacheDao,
    private val limitProvider: suspend () -> Long,
    private val onSimpleCacheEvict: (platform: String, id: String, quality: PlayQuality?) -> Unit = { _, _, _ -> },
    private val byteCacheStatusStore: ByteCacheStatusStore = EmptyByteCacheStatusStore,
) {
    // NOTE: @Inject is intentionally absent here.
    // MediaCacheRepository is provided via MediaCacheBindingModule in :app,
    // which wires the SimpleCacheEvictor callback (SimpleCacheHolder lives in :player,
    // which :data cannot depend on). See app/.../di/MediaCacheBindingModule.kt.
    constructor(
        dao: MediaCacheDao,
        appPreferences: AppPreferences,
    ) : this(dao, limitProvider = { appPreferences.maxMusicCacheSizeBytes.first() })

    constructor(dao: MediaCacheDao) : this(dao, limitProvider = DEFAULT_LIMIT_PROVIDER)

    // Secondary constructor for tests to inject a fake clock
    internal constructor(dao: MediaCacheDao, now: () -> Long) : this(dao, DEFAULT_LIMIT_PROVIDER) {
        this.nowFn = now
    }

    internal constructor(
        dao: MediaCacheDao,
        now: () -> Long,
        limitProvider: suspend () -> Long,
        byteCacheStatusStore: ByteCacheStatusStore = EmptyByteCacheStatusStore,
    ) : this(dao, limitProvider, byteCacheStatusStore = byteCacheStatusStore) {
        this.nowFn = now
    }

    // Internal constructor for tests that need to verify the evict callback
    internal constructor(
        dao: MediaCacheDao,
        now: () -> Long,
        limitProvider: suspend () -> Long,
        onSimpleCacheEvict: (platform: String, id: String, quality: PlayQuality?) -> Unit,
        byteCacheStatusStore: ByteCacheStatusStore = EmptyByteCacheStatusStore,
    ) : this(dao, limitProvider, onSimpleCacheEvict, byteCacheStatusStore) {
        this.nowFn = now
    }

    private var nowFn: () -> Long = System::currentTimeMillis
    private fun now() = nowFn()

    // In-memory LRU tier keyed by "${platform}@${id}" -> parsed sourcesJson.
    // Provides single-track hot-path lookup without DB round-trips. Sized to be
    // small enough to never dominate memory; DB is the source of truth.
    // Subclassed so capacity-driven evictions surface in logs — without this,
    // a hot song silently dropped out of memory looks like "cache disappeared"
    // when diagnosing reuse complaints.
    private val memory = object : LruCache<String, JSONObject>(MEMORY_LIMIT) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: JSONObject,
            newValue: JSONObject?,
        ) {
            // evicted=false means manual remove/put-overwrite; only capacity-driven
            // drops are worth surfacing here.
            if (!evicted) return
            MfLog.detail(
                category = LogCategory.DATA,
                event = "media_cache_memory_evict",
                fields = mapOf(
                    "key" to key,
                    "cacheSize" to size(),
                    "maxSize" to MEMORY_LIMIT,
                ),
            )
        }
    }

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
            pruneToLimit(limitProvider())
            if (dao.count() >= LIMIT) pruneOldest()
        }
    }

    /**
     * Delete all cached qualities for one song from DB and memory.
     *
     * Also calls [onSimpleCacheEvict] (with null quality = all) to remove the
     * corresponding Media3 SimpleCache spans, keeping the two cache layers in sync.
     */
    suspend fun deleteItem(platform: String, id: String) = mutex.withLock {
        val startedAt = System.nanoTime()
        try {
            dao.delete(platform, id)
            memory.remove(memoryKey(platform, id))
            MfLog.detail(
                category = LogCategory.DATA,
                event = "delete_media_cache_item",
                fields = mapOf(
                    "platform" to platform,
                    "itemId" to id,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            onSimpleCacheEvict(platform, id, null)
            byteCacheStatusStore.deleteBySong(platform, id)
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.DATA,
                event = "delete_media_cache_item",
                fields = mapOf(
                    "platform" to platform,
                    "itemId" to id,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "delete_media_cache_item",
                throwable = error,
                fields = mapOf(
                    "platform" to platform,
                    "itemId" to id,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "exception",
                ),
            )
            throw error
        }
    }

    /**
     * Remove a single quality key from one row. If no quality keys remain the row is
     * deleted entirely. Memory layer is synced to match.
     *
     * Also calls [onSimpleCacheEvict] to remove the corresponding Media3 SimpleCache
     * spans, keeping the two cache layers in sync.
     */
    suspend fun deleteEntry(platform: String, id: String, quality: PlayQuality) = mutex.withLock {
        val existing = dao.get(platform, id) ?: run {
            memory.remove(memoryKey(platform, id))
            MfLog.detail(
                category = LogCategory.DATA,
                event = "media_cache_delete_entry",
                fields = mapOf(
                    "platform" to platform,
                    "itemId" to id,
                    "quality" to quality.name.lowercase(),
                    "result" to "missing",
                    "rowDeleted" to false,
                ),
            )
            return@withLock
        }
        val obj = parseSourcesJson(existing.sourcesJson)
        if (obj == null || !obj.has(quality.name)) {
            if (obj != null) memory.put(memoryKey(platform, id), obj)
            MfLog.detail(
                category = LogCategory.DATA,
                event = "media_cache_delete_entry",
                fields = mapOf(
                    "platform" to platform,
                    "itemId" to id,
                    "quality" to quality.name.lowercase(),
                    "result" to "noop",
                    "rowDeleted" to false,
                ),
            )
            return@withLock
        }
        obj.remove(quality.name)
        val rowDeleted = obj.length() == 0
        if (rowDeleted) {
            dao.delete(platform, id)
            memory.remove(memoryKey(platform, id))
        } else {
            dao.upsert(MediaCacheEntity(platform, id, obj.toString(), now()))
            memory.put(memoryKey(platform, id), obj)
        }
        MfLog.detail(
            category = LogCategory.DATA,
            event = "media_cache_delete_entry",
            fields = mapOf(
                "platform" to platform,
                "itemId" to id,
                "quality" to quality.name.lowercase(),
                "result" to LogFields.Result.SUCCESS,
                "rowDeleted" to rowDeleted,
                "remainingQualities" to obj.length(),
            ),
        )
        onSimpleCacheEvict(platform, id, quality)
        byteCacheStatusStore.delete(ByteCacheKey(platform = platform, musicId = id, quality = quality))
    }

    /** Delete all rows for the platform from DB and from memory. */
    suspend fun deleteByPlatform(platform: String) = mutex.withLock {
        logDataWrite(
            operation = "delete_media_cache_by_platform",
            fields = mapOf("platform" to platform),
        ) {
            dao.deleteByPlatform(platform)
            val prefix = "$platform@"
            val keysToDrop = memory.snapshot().keys.filter { it.startsWith(prefix) }
            keysToDrop.forEach { memory.remove(it) }
            byteCacheStatusStore.deleteByPlatform(platform)
        }
    }

    /**
     * Returns the total byte footprint of all stored URL/header metadata entries.
     * Intended for use by [com.hank.musicfree.feature.settings.SettingsCacheCleaner]
     * to report freed bytes when clearing this layer.
     */
    suspend fun estimatedBytes(): Long = mutex.withLock { dao.totalSizeBytes() }

    /** Delete all media cache rows from DB and memory. */
    suspend fun clearAll() = mutex.withLock {
        logDataWrite(
            operation = "clear_media_cache",
            fields = emptyMap(),
        ) {
            dao.deleteAll()
            memory.evictAll()
            byteCacheStatusStore.deleteAll()
        }
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
            val totalBefore = dao.count()
            val removedEntries = dao.getOldestEntries().take(LIMIT / 2)
            dao.deleteOldest(LIMIT / 2)
            removedEntries.forEach { byteCacheStatusStore.deleteBySong(it.platform, it.id) }
            memory.evictAll()
            val totalAfter = dao.count()
            MfLog.detail(
                category = LogCategory.DATA,
                event = "media_cache_prune",
                fields = mapOf(
                    "count" to (totalBefore - totalAfter).coerceAtLeast(0),
                    "totalBefore" to totalBefore,
                    "totalAfter" to totalAfter,
                    "trigger" to "row_cap",
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

    private suspend fun pruneToLimit(limitBytes: Long) {
        val startedAt = System.nanoTime()
        val coercedLimit = limitBytes.coerceAtLeast(0L)
        try {
            var totalBytes = dao.totalSizeBytes()
            if (totalBytes <= coercedLimit) return

            var removedCount = 0
            var removedBytes = 0L
            val sampleEvicted = ArrayList<String>()
            for (entry in dao.getOldestEntries()) {
                if (totalBytes <= coercedLimit) break
                val entryBytes = entry.sourcesJson.toByteArray().size.toLong()
                dao.delete(entry.platform, entry.id)
                memory.remove(memoryKey(entry.platform, entry.id))
                byteCacheStatusStore.deleteBySong(entry.platform, entry.id)
                totalBytes -= entryBytes
                removedBytes += entryBytes
                removedCount += 1
                if (sampleEvicted.size < EVICTED_SAMPLE) {
                    sampleEvicted += "${entry.platform}@${entry.id}"
                }
            }

            MfLog.detail(
                category = LogCategory.DATA,
                event = "media_cache_trim",
                fields = mapOf(
                    "count" to removedCount,
                    "sizeBytes" to removedBytes,
                    "limitBytes" to coercedLimit,
                    "remainingBytes" to totalBytes.coerceAtLeast(0L),
                    "evictedItems" to sampleEvicted,
                    "trigger" to "byte_cap",
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.DATA,
                event = "media_cache_trim",
                fields = mapOf(
                    "limitBytes" to coercedLimit,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "media_cache_trim",
                throwable = error,
                fields = mapOf(
                    "limitBytes" to coercedLimit,
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
        private const val EVICTED_SAMPLE = 10
        const val DEFAULT_MAX_CACHE_SIZE_BYTES = 512L * 1024L * 1024L

        /**
         * Divisor used to derive the repository's byte quota from the user-configured
         * total cache budget. The repository stores URL/header metadata only; 10% of the
         * full audio-file budget is more than adequate for thousands of entries.
         */
        const val REPO_QUOTA_DIVISOR = 10L

        private val DEFAULT_LIMIT_PROVIDER: suspend () -> Long = { DEFAULT_MAX_CACHE_SIZE_BYTES / REPO_QUOTA_DIVISOR }

        /**
         * Factory that wires the SimpleCache eviction callback.
         *
         * Called from the `:app` Hilt module (which can see both `:data` and `:player`),
         * keeping the `:data → :player` dependency edge out of the graph.
         *
         * The repository holds URL/header metadata only; it uses a 10% sub-quota
         * ([REPO_QUOTA_DIVISOR]) of the user-configured total cache budget.
         */
        fun create(
            dao: MediaCacheDao,
            appPreferences: AppPreferences,
            evictor: SimpleCacheEvictor,
            byteCacheStatusStore: ByteCacheStatusStore = EmptyByteCacheStatusStore,
        ): MediaCacheRepository = MediaCacheRepository(
            dao = dao,
            limitProvider = {
                (appPreferences.maxMusicCacheSizeBytes.first() / REPO_QUOTA_DIVISOR).coerceAtLeast(1L)
            },
            onSimpleCacheEvict = { p, i, q -> evictor.evictForKey(p, i, q) },
            byteCacheStatusStore = byteCacheStatusStore,
        )
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
