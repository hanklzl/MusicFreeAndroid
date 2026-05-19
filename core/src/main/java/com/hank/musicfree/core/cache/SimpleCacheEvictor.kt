package com.hank.musicfree.core.cache

import com.hank.musicfree.core.model.PlayQuality

/**
 * Abstraction for evicting a single song's data from the Media3 SimpleCache layer.
 *
 * Defined in :core so that :data (MediaCacheRepository) can hold a callback reference
 * without creating a direct dependency on :player (SimpleCacheHolder). The actual
 * implementation lives in :player and is wired in :app via Hilt.
 */
fun interface SimpleCacheEvictor {
    /**
     * Remove cached spans for the given song from the underlying SimpleCache.
     *
     * @param platform  plugin platform identifier (e.g. "kg", "netease")
     * @param id        song ID within that platform
     * @param quality   specific quality to evict, or null to evict ALL qualities
     */
    fun evictForKey(platform: String, id: String, quality: PlayQuality?)
}
