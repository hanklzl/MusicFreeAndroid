package com.hank.musicfree.player.cache

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.cache.EmptyByteCacheStatusStore
import com.hank.musicfree.core.cache.SimpleCacheEvictor
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * SimpleCache 单例 holder，支持 `current` 懒加载和 `resetForClear` 重置。
 *
 * 初始化失败时进入 disabled 状态（永久返回 null），调用方应 fallback 到非缓存路径。
 *
 * 缓存目录优先 `getExternalFilesDir(null)/media-cache`，外部不可用时回退到内部 cacheDir。
 * 上限 512MB（LRU evictor），数据库走 `StandaloneDatabaseProvider`，不污染 Room。
 */
@Singleton
@AndroidXOptIn(markerClass = [UnstableApi::class])
class SimpleCacheHolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val playCacheTelemetry: PlayCacheTelemetry,
    private val byteCacheStatusStore: ByteCacheStatusStore = EmptyByteCacheStatusStore,
) : SimpleCacheEvictor {
    private val ref = AtomicReference<SimpleCache?>(null)
    private val initFailed = AtomicBoolean(false)
    private var pinningEvictor: PinningCacheEvictor? = null

    val current: SimpleCache?
        get() = ref.get() ?: synchronized(this) {
            if (initFailed.get()) return null
            ref.get() ?: tryCreate()?.also { ref.set(it) }
        }

    fun resetForClear(): SimpleCache? = synchronized(this) {
        ref.get()?.release()
        ref.set(null)
        cacheDir().deleteRecursively()
        deleteAllByteCacheStatuses()
        tryCreate()?.also { ref.set(it) }
    }

    fun cacheDirPath(): String = cacheDir().absolutePath
    fun usedBytes(): Long = current?.cacheSpace ?: 0L

    /**
     * Clears the audio-file cache and recreates the underlying [SimpleCache].
     * Unlike [resetForClear], this variant returns [Unit] so callers that cannot
     * reference the Media3 [SimpleCache] type (e.g. `:feature:settings`) can still
     * trigger a cache clear without requiring a direct Media3 dependency.
     */
    fun clearCache() {
        resetForClear()
    }

    /**
     * Remove all cached spans for a specific song (optionally filtered by quality) from
     * the underlying SimpleCache. Called by [MediaCacheRepository] whenever a DB-level
     * eviction happens, so the two cache layers stay in sync.
     *
     * @param platform  plugin platform identifier (e.g. "kg")
     * @param id        song ID within that platform
     * @param quality   specific quality to evict, or null to evict ALL qualities
     */
    override fun evictForKey(platform: String, id: String, quality: PlayQuality?) {
        runCatching {
            val cache = current ?: return
            val qualities = if (quality != null) listOf(quality) else PlayQuality.values().toList()
            var totalRemoved = 0
            var totalBytes = 0L
            for (q in qualities) {
                val key = "$platform:$id:${q.name.lowercase()}"
                val spans = cache.getCachedSpans(key)
                if (spans.isNotEmpty()) {
                    totalBytes += spans.sumOf { it.length }
                    cache.removeResource(key)
                    totalRemoved += 1
                }
            }
            // Also clean the "unknown" suffix (entries cached when quality was not yet established)
            val unknownKey = "$platform:$id:unknown"
            val unknownSpans = cache.getCachedSpans(unknownKey)
            if (unknownSpans.isNotEmpty()) {
                totalBytes += unknownSpans.sumOf { it.length }
                cache.removeResource(unknownKey)
                totalRemoved += 1
            }
            if (totalRemoved > 0) {
                playCacheTelemetry.cacheEvict(
                    scope = "stale_url",
                    count = totalRemoved,
                    freedBytes = totalBytes,
                )
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "media_cache_evict_for_key",
                    fields = mapOf(
                        "platform" to platform,
                        "id" to id,
                        "quality" to quality?.name?.lowercase(),
                        "removedQualityCount" to totalRemoved,
                        "freedBytes" to totalBytes,
                        "triggerSource" to "stale_url",
                    ),
                )
            }
            deleteByteCacheStatusForKey(platform = platform, id = id, quality = quality)
        }.onFailure { throwable ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "media_cache_evict_for_key_failed",
                throwable = throwable,
                fields = mapOf(
                    "platform" to platform,
                    "id" to id,
                    "quality" to quality?.name?.lowercase(),
                    "reason" to (throwable.javaClass.simpleName ?: "exception"),
                ),
            )
        }
    }

    /**
     * One-time migration from legacy cache keys (no quality suffix) to the new
     * quality-qualified scheme. Called once on application startup; subsequent
     * launches check the persisted schema version and are no-ops.
     *
     * Runs on the calling thread (Application.onCreate) — intentionally
     * synchronous so the first [current] access after onCreate already sees the
     * clean cache. Migration touches only metadata (removes spans), not downloads.
     */
    fun migrateOnceIfNeeded() {
        // TODO(perf): consider running this on Dispatchers.IO to avoid blocking onCreate
        runCatching {
            val cache = current ?: return
            val version = runBlocking { appPreferences.mediaCacheSchemaVersion.first() }
            if (version >= 1) return
            val startedAt = System.currentTimeMillis()
            val result = CacheSchemaMigrator.migrate(cache)
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "media_cache_schema_migration",
                fields = mapOf(
                    "removedCount" to result.removedCount,
                    "freedBytes" to result.freedBytes,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                ),
            )
            playCacheTelemetry.cacheEvict(
                scope = "migration",
                count = result.removedCount,
                freedBytes = result.freedBytes,
            )
            runBlocking { appPreferences.setMediaCacheSchemaVersion(1) }
        }.onFailure { throwable ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "media_cache_migration_failed",
                throwable = throwable,
                fields = mapOf("reason" to (throwable.javaClass.simpleName ?: "exception")),
            )
        }
    }

    /**
     * Respond to a user-initiated change to the cache byte budget.
     *
     * If the new budget is *larger* than the current used space nothing needs to happen —
     * LRU will naturally respect the new, larger limit on the next write.
     *
     * If the new budget is *smaller* than what is already used, SimpleCache has no public
     * "shrink" API, so we release and null-out the ref; [tryCreate] will re-read the latest
     * prefs (including the new, smaller limit) on the next [current] access.
     *
     * known-limitation: cache recreate drops all files; spec accepts this trade-off.
     */
    fun updateMaxBytes(newBytes: Long) {
        runCatching {
            val cache = current ?: return
            val used = cache.cacheSpace
            if (used <= newBytes) return
            val previousUsed = used
            synchronized(this) {
                ref.get()?.release()
                ref.set(null)
            }
            deleteAllByteCacheStatuses()
            playCacheTelemetry.cacheEvict(
                scope = "byte_cap",
                count = 1,
                freedBytes = (previousUsed - newBytes).coerceAtLeast(0L),
            )
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "media_cache_max_bytes_changed",
                fields = mapOf(
                    "newBytes" to newBytes,
                    "previousUsed" to previousUsed,
                    "freedBytes" to (previousUsed - newBytes).coerceAtLeast(0L),
                ),
            )
        }.onFailure { error ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "media_cache_update_max_bytes_failed",
                throwable = error,
                fields = mapOf("cacheDirPath" to cacheDirPath()),
            )
        }
    }

    /**
     * Updates the set of pinned keys so recently-played and starred tracks survive LRU eviction.
     *
     * Each base key ("<platform>:<id>") is expanded to all five quality-suffixed cache key
     * variants plus the `:unknown` fallback suffix. Pinned-size estimation follows to trigger
     * the 70% overflow guard in [PinningCacheEvictor.shouldSkip].
     */
    fun updatePinned(keys: Set<String>) {
        runCatching {
            val evictor = pinningEvictor ?: return
            val expanded = HashSet<String>(keys.size * 6)
            for (base in keys) {
                for (q in PlayQuality.values()) {
                    expanded += "$base:${q.name.lowercase()}"
                }
                expanded += "$base:unknown"
            }
            evictor.updatePinned(expanded)
            val cache = current
            if (cache != null) {
                val totalBytes = expanded.sumOf { k ->
                    cache.getCachedSpans(k).sumOf { it.length }
                }
                evictor.notePinnedSize(totalBytes)
            }
        }.onFailure { error ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "media_cache_update_pinned_failed",
                throwable = error,
                fields = mapOf("count" to keys.size),
            )
        }
    }

    private fun tryCreate(): SimpleCache? = runCatching {
        val configured = runBlocking {
            appPreferences.maxMusicCacheSizeBytes.first()
        }
        val cacheDir = cacheDir().apply { mkdirs() }
        val available = cacheDir.parentFile?.usableSpace ?: Long.MAX_VALUE
        val storageScope = if (cacheDir.absolutePath.contains(context.cacheDir.absolutePath)) {
            "internal"
        } else {
            "external"
        }
        val effective = if (available < LOWSPACE_THRESHOLD) {
            playCacheTelemetry.cacheLowspace(
                availableBytes = available,
                configuredBytes = configured,
                fallbackBytes = LOWSPACE_FALLBACK,
            )
            minOf(configured, LOWSPACE_FALLBACK)
        } else {
            configured
        }
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "media_cache_init",
            fields = mapOf(
                "configuredBytes" to configured,
                "effectiveCapBytes" to effective,
                "availableBytes" to available,
                "storageScope" to storageScope,
                "cacheDirPath" to cacheDir.absolutePath,
            ),
        )
        SimpleCache(
            cacheDir,
            PinningCacheEvictor(
                maxBytes = effective,
                onSpanKeyRemoved = ::deleteByteCacheStatusForSimpleCacheKey,
            ).also { pinningEvictor = it },
            StandaloneDatabaseProvider(context),
        )
    }.onFailure { error ->
        initFailed.set(true)
        MfLog.error(
            category = LogCategory.PLAYER,
            event = "media_cache_init_failed",
            throwable = error,
            fields = mapOf("cacheDirPath" to cacheDirPath()),
        )
    }.getOrNull()

    private fun cacheDir(): File =
        context.getExternalFilesDir(null)?.resolve("media-cache")
            ?: context.cacheDir.resolve("media-cache")

    private fun deleteByteCacheStatusForSimpleCacheKey(simpleCacheKey: String) {
        val firstSeparator = simpleCacheKey.indexOf(':')
        val lastSeparator = simpleCacheKey.lastIndexOf(':')
        if (firstSeparator <= 0 || lastSeparator <= firstSeparator) return
        val quality = PlayQuality.values().firstOrNull {
            it.name.equals(simpleCacheKey.substring(lastSeparator + 1), ignoreCase = true)
        } ?: return
        deleteByteCacheStatusForKey(
            platform = simpleCacheKey.substring(0, firstSeparator),
            id = simpleCacheKey.substring(firstSeparator + 1, lastSeparator),
            quality = quality,
        )
    }

    private fun deleteByteCacheStatusForKey(
        platform: String,
        id: String,
        quality: PlayQuality?,
    ) {
        runCatching {
            runBlocking {
                if (quality == null) {
                    byteCacheStatusStore.deleteBySong(platform, id)
                } else {
                    byteCacheStatusStore.delete(ByteCacheKey(platform, id, quality))
                }
            }
        }.onFailure { error ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "byte_cache_status_delete_failed",
                throwable = error,
                fields = mapOf(
                    "platform" to platform,
                    "itemId" to id,
                    "quality" to quality?.name?.lowercase(),
                    "reason" to (error.javaClass.simpleName ?: "exception"),
                ),
            )
        }
    }

    private fun deleteAllByteCacheStatuses() {
        runCatching {
            runBlocking {
                byteCacheStatusStore.deleteAll()
            }
        }.onFailure { error ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "byte_cache_status_delete_failed",
                throwable = error,
                fields = mapOf(
                    "scope" to "all",
                    "reason" to (error.javaClass.simpleName ?: "exception"),
                ),
            )
        }
    }

    companion object {
        /** Minimum free disk space (2 GB) below which the cache is capped at [LOWSPACE_FALLBACK]. */
        const val LOWSPACE_THRESHOLD = 2L * 1024 * 1024 * 1024

        /** Fallback cache cap (256 MB) used when free disk space is below [LOWSPACE_THRESHOLD]. */
        const val LOWSPACE_FALLBACK = 256L * 1024 * 1024

        @Deprecated("Use AppPreferences.maxMusicCacheSizeBytes")
        const val DEFAULT_BYTES = 512L * 1024 * 1024
    }
}
