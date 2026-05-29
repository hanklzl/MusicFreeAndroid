package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges cache bytes and transfer-level bytes into PlayCacheTelemetry for each opened
 * data source session.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
@Singleton
class CacheDataSourceEventBridge @Inject constructor(
    private val telemetry: PlayCacheTelemetry,
    private val sidProvider: CurrentSidProvider,
) {
    interface Session : CacheDataSource.EventListener {
        fun addBytesRead(byteCount: Long)
        fun closeOnce()
    }

    fun newSession(cacheKey: String, cacheBypassReason: String? = null): Session {
        val cachedBytes = AtomicLong(0)
        val totalBytes = AtomicLong(0)
        val closed = AtomicBoolean(false)
        val sid = sidProvider.peek()

        telemetry.media3DataSourceOpen(
            sid = sid,
            cacheKey = cacheKey,
            cacheHit = false,
            bytesFromCache = 0L,
            bytesFromUpstream = 0L,
        )

        if (cacheBypassReason != null) {
            telemetry.mediaCacheBypass(
                sid = sid,
                cacheKey = cacheKey,
                reason = cacheBypassReason,
            )
        }

        return object : Session {
            override fun addBytesRead(byteCount: Long) {
                if (byteCount > 0) {
                    totalBytes.addAndGet(byteCount)
                }
            }

            override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                if (cachedBytesRead > 0) {
                    cachedBytes.addAndGet(cachedBytesRead)
                }
            }

            override fun closeOnce() {
                if (closed.compareAndSet(false, true).not()) return
                val total = totalBytes.get()
                val cached = cachedBytes.get()
                telemetry.media3DataSourceClose(
                    sid = sid,
                    cacheKey = cacheKey,
                    cacheHit = cached > 0L,
                    bytesFromCache = cached,
                    bytesFromUpstream = (total - cached).coerceAtLeast(0),
                    cacheBypassReason = cacheBypassReason,
                )
            }

            override fun onCacheIgnored(reason: Int) = Unit
        }
    }
}
