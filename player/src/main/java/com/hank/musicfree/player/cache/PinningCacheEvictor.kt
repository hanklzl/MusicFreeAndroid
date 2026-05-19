package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor

/**
 * Wraps [LeastRecentlyUsedCacheEvictor] and excludes user-pinned keys from eviction.
 *
 * Overflow guard: if pinned content exceeds 70% of [maxBytes], pinning is suspended
 * (falls back to plain LRU) so the cache stays usable.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
class PinningCacheEvictor(private val maxBytes: Long) : CacheEvictor {

    private val delegate = LeastRecentlyUsedCacheEvictor(maxBytes)
    @Volatile private var pinned: Set<String> = emptySet()
    @Volatile private var pinnedSizeBytes: Long = 0L

    fun updatePinned(keys: Set<String>) { pinned = keys }
    fun notePinnedSize(bytes: Long) { pinnedSizeBytes = bytes }

    /** Test seam: whether [key] would currently be protected from eviction. */
    fun shouldSkip(key: String): Boolean {
        if (pinnedSizeBytes * 100L > maxBytes * 70L) return false
        return key in pinned
    }

    override fun requiresCacheSpanTouches(): Boolean = delegate.requiresCacheSpanTouches()
    override fun onCacheInitialized() = delegate.onCacheInitialized()
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        val toFree = (cache.cacheSpace + length - maxBytes).coerceAtLeast(0L)
        if (toFree <= 0L) return
        evictSkippingPinned(cache, toFree)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) = delegate.onSpanAdded(cache, span)
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) = delegate.onSpanRemoved(cache, span)
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) =
        delegate.onSpanTouched(cache, oldSpan, newSpan)

    private fun evictSkippingPinned(cache: Cache, bytesNeeded: Long) {
        var freed = 0L
        val keys = cache.keys.sortedBy { key ->
            cache.getCachedSpans(key).minOfOrNull { it.lastTouchTimestamp } ?: Long.MAX_VALUE
        }
        for (key in keys) {
            if (freed >= bytesNeeded) break
            if (shouldSkip(key)) continue
            val spans = cache.getCachedSpans(key)
            for (span in spans) {
                cache.removeSpan(span)
                freed += span.length
                if (freed >= bytesNeeded) break
            }
        }
    }
}
