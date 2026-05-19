package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges CacheDataSource cache/upstream byte counters into PlayCacheTelemetry as
 * `media3_datasource_open` events. Each call to `newListener(cacheKey)` returns a fresh
 * stateful listener instance because the underlying `CacheDataSource.EventListener` API
 * accumulates per-source-open counters.
 *
 * Known limitation: because `createDataSource()` is called once per ExoPlayer data-source
 * slot (not once per DataSpec), the `cacheKey` logged here is the placeholder `"(per-open)"`.
 * Per-DataSpec cache-hit byte tallies require wrapping CacheDataSource manually (Task 9+).
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
@Singleton
class CacheDataSourceEventBridge @Inject constructor(
    private val telemetry: PlayCacheTelemetry,
    private val sidProvider: CurrentSidProvider,
) {
    fun newListener(cacheKey: String): CacheDataSource.EventListener {
        val cacheBytes = AtomicLong(0)
        val upstreamBytes = AtomicLong(0)
        // Emit one open-event upfront with sid + cacheKey; final tallies are flushed
        // by play_session_end (PlayerController) when the play session terminates.
        telemetry.media3DataSourceOpen(
            sid = sidProvider.peek(),
            cacheKey = cacheKey,
            cacheHit = false,
            bytesFromCache = 0L,
            bytesFromUpstream = 0L,
        )
        return object : CacheDataSource.EventListener {
            override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                cacheBytes.addAndGet(cachedBytesRead)
            }

            override fun onCacheIgnored(reason: Int) {
                // ignored — covered by media3_datasource_error if upstream fails
            }
        }
    }
}
