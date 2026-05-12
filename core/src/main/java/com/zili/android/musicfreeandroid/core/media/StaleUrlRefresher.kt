package com.zili.android.musicfreeandroid.core.media

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality

/**
 * Narrow capability surface exposed to `:player` so it can react to playback
 * failures (HTTP 4xx/5xx) by evicting the offending cache entry and re-resolving
 * the source via the plugin path, without requiring `:player` to depend on
 * `:plugin` or `:data` directly.
 *
 * Spec §5.7 of `2026-05-11-plugin-engine-alignment-design.md`:
 *
 * ```
 * PlayerController.onPlayerError:
 *   if (error.code == ERROR_CODE_IO_BAD_HTTP_STATUS) {
 *     staleUrlRefresher.evictCacheEntry(platform, id, currentQuality)
 *     val fresh = staleUrlRefresher.resolveFresh(item, currentQuality.wireName())
 *     ...
 *   }
 * ```
 *
 * Implementations live in `:plugin` (`PluginMediaSourceService`) and are bound
 * via Hilt.
 */
interface StaleUrlRefresher {
    /**
     * Remove the cached source row for the given (platform, id, quality). If no
     * other quality remains the underlying row should be deleted entirely.
     */
    suspend fun evictCacheEntry(platform: String, id: String, quality: PlayQuality)

    /**
     * Re-resolve the source via the plugin, bypassing cache reads (writes may
     * still happen subject to the plugin's declared `cacheControl`). Returns
     * `null` when no plugin / network path can produce a fresh URL.
     */
    suspend fun resolveFresh(item: MusicItem, quality: String? = null): MediaSourceResolution?
}

/** Inert default usable as a constructor default in `PlayerController` test setups. */
object EmptyStaleUrlRefresher : StaleUrlRefresher {
    override suspend fun evictCacheEntry(platform: String, id: String, quality: PlayQuality) = Unit
    override suspend fun resolveFresh(item: MusicItem, quality: String?): MediaSourceResolution? = null
}
