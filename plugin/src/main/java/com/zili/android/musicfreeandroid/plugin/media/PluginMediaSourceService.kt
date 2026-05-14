package com.zili.android.musicfreeandroid.plugin.media

import com.zili.android.musicfreeandroid.core.media.MediaSourceResolution
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.media.StaleUrlRefresher
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.core.model.fallbackSequence
import com.zili.android.musicfreeandroid.data.repository.CachedSource
import com.zili.android.musicfreeandroid.data.repository.MediaCacheRepository
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.network.PluginNetworkStateProvider
import com.zili.android.musicfreeandroid.plugin.playback.CacheControl
import com.zili.android.musicfreeandroid.plugin.playback.shouldUseCache
import com.zili.android.musicfreeandroid.plugin.playback.shouldWriteCache
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PluginMediaSourceService @Inject constructor(
    private val pluginManager: PluginManager,
    private val mediaCacheRepository: MediaCacheRepository,
    private val playbackRuntimeSettings: PlaybackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
    private val networkStateProvider: PluginNetworkStateProvider = PluginNetworkStateProvider.AlwaysOnline,
) : MediaSourceResolver, StaleUrlRefresher {

    /**
     * Default resolution entry. Reads cache when the source plugin's declared
     * `cacheControl` permits it (`"cache"`, or `"no-cache"` while offline).
     * Always writes
     * cache on success unless `cacheControl == "no-store"`.
     */
    override suspend fun resolve(
        item: MusicItem,
        quality: String?,
    ): MediaSourceResolution? = doResolve(item, quality, useCache = true)

    /**
     * Bypass-cache entry used by the playback failure recovery path (spec §5.7).
     * Always skips the cache read; still writes cache on success unless the
     * source plugin declared `cacheControl == "no-store"`.
     */
    override suspend fun resolveFresh(
        item: MusicItem,
        quality: String?,
    ): MediaSourceResolution? = doResolve(item, quality, useCache = false)

    /**
     * Single-quality cache eviction; called from `PlayerController` when ExoPlayer
     * reports `ERROR_CODE_IO_BAD_HTTP_STATUS` on a URL we already played.
     */
    override suspend fun evictCacheEntry(platform: String, id: String, quality: PlayQuality) {
        mediaCacheRepository.deleteEntry(platform, id, quality)
    }

    private suspend fun doResolve(
        item: MusicItem,
        quality: String?,
        useCache: Boolean,
    ): MediaSourceResolution? {
        val sourcePlugin = pluginManager.getPlugin(item.platform) ?: return null
        val cacheControl = CacheControl.parse(sourcePlugin.info.cacheControl)

        // 1. Try cache (only when the caller permits it AND policy permits it).
        //
        // FOLLOW-UP (Phase A-3 review Important #1, RN parity): cache hit currently
        // bypasses the alternative-plugin override below. If the user newly maps
        // `kuwo -> kugou` after a kuwo URL was already cached, replay returns the
        // stale kuwo URL. Track as a follow-up; fix candidates: invalidate cache on
        // PluginMetaStore alternative-plugins change, or include resolver platform
        // in the cache key.
        if (useCache) {
            val isOffline = networkStateProvider.isOffline()
            if (shouldUseCache(cacheControl, isOffline = isOffline)) {
                // Important #2/#3 fix: when quality is null, target the user's
                // default play quality (the same quality the fetch loop will ask
                // for first) so caches written at HIGH/SUPER actually get re-read.
                val requestedQuality = if (quality.isNullOrBlank()) {
                    playbackRuntimeSettings.defaultPlayQuality()
                } else {
                    parseQualityOrDefault(quality)
                }
                val cached = mediaCacheRepository.get(item, requestedQuality)
                if (cached != null) {
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "plugin_get_media_source_cache_hit",
                        fields = mapOf(
                            "platform" to item.platform,
                            "musicItemId" to item.id,
                            "quality" to requestedQuality.wireName(),
                        ),
                    )
                    return cached.toResolution(
                        item = item,
                        quality = requestedQuality,
                        resolverPlatform = sourcePlugin.info.platform,
                    )
                }
            }
        }

        // 2. Walk quality candidates, ask plugin (optionally via alternative).
        val disabled = pluginManager.pluginMetaStore.disabledPlugins.first()
        val alternatives = pluginManager.pluginMetaStore.alternativePlugins.first()
        val alternativePlatform = alternatives[item.platform]
            ?.takeUnless { it == item.platform }
            ?.takeUnless { it in disabled }
        val alternativePlugin = alternativePlatform
            ?.let { pluginManager.getPlugin(it) }
            ?.takeIf { it.supportsMediaSource() }

        for (candidateQuality in qualityCandidates(quality)) {
            val viaAlternative = alternativePlugin?.resolveWith(
                item = item,
                quality = candidateQuality,
                requestedPlatform = item.platform,
                redirected = true,
            )
            if (viaAlternative != null) {
                maybeWriteCache(cacheControl, item, candidateQuality, viaAlternative.source)
                return viaAlternative
            }

            val viaSource = sourcePlugin.resolveWith(
                item = item,
                quality = candidateQuality,
                requestedPlatform = item.platform,
                redirected = false,
            )
            if (viaSource != null) {
                maybeWriteCache(cacheControl, item, candidateQuality, viaSource.source)
                return viaSource
            }
        }

        return null
    }

    private suspend fun maybeWriteCache(
        cacheControl: CacheControl,
        item: MusicItem,
        candidateQuality: String,
        source: MediaSourceResult,
    ) {
        if (!shouldWriteCache(cacheControl)) return
        // Important #4 fix: don't pollute the STANDARD slot with payloads that
        // came back for an unknown wire quality. parseStrictQuality returns null
        // when the wire string does not map to a known PlayQuality.
        val pq = parseStrictQuality(candidateQuality) ?: run {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_get_media_source_cache_write_skipped_unknown_quality",
                fields = mapOf(
                    "platform" to item.platform,
                    "musicItemId" to item.id,
                    "quality" to candidateQuality,
                ),
            )
            return
        }
        mediaCacheRepository.put(item, pq, source)
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_get_media_source_cache_write",
            fields = mapOf(
                "platform" to item.platform,
                "musicItemId" to item.id,
                "quality" to pq.wireName(),
            ),
        )
    }

    private fun CachedSource.toResolution(
        item: MusicItem,
        quality: PlayQuality,
        resolverPlatform: String,
    ): MediaSourceResolution = MediaSourceResolution(
        item = item.copy(url = url),
        source = MediaSourceResult(
            url = url,
            headers = headers,
            userAgent = userAgent,
            quality = quality,
        ),
        requestedPlatform = item.platform,
        resolverPlatform = resolverPlatform,
        redirected = false,
    )

    private suspend fun LoadedPlugin.resolveWith(
        item: MusicItem,
        quality: String,
        requestedPlatform: String,
        redirected: Boolean,
    ): MediaSourceResolution? {
        if (!supportsMediaSource()) return null
        val source = runCatching { getMediaSource(item, quality) }.getOrNull()
            ?.takeIf { it.url.isNotBlank() }
            ?: return null
        return MediaSourceResolution(
            item = item.copy(url = source.url),
            source = source,
            requestedPlatform = requestedPlatform,
            resolverPlatform = info.platform,
            redirected = redirected,
        )
    }

    private fun LoadedPlugin.supportsMediaSource(): Boolean =
        "getMediaSource" in info.supportedMethods

    private suspend fun qualityCandidates(explicitQuality: String?): List<String> {
        if (!explicitQuality.isNullOrBlank()) {
            return listOf(explicitQuality)
        }

        val defaultQuality = playbackRuntimeSettings.defaultPlayQuality()
        val order = playbackRuntimeSettings.playQualityOrder()
        return defaultQuality.fallbackSequence(order).map { it.wireName() }
    }

    private fun parseQualityOrDefault(wire: String?): PlayQuality {
        if (wire.isNullOrBlank()) return PlayQuality.STANDARD
        return runCatching { PlayQuality.valueOf(wire.uppercase()) }.getOrDefault(PlayQuality.STANDARD)
    }

    /** Returns null when the wire string does not map to a known [PlayQuality]. */
    private fun parseStrictQuality(wire: String?): PlayQuality? {
        if (wire.isNullOrBlank()) return null
        return runCatching { PlayQuality.valueOf(wire.uppercase()) }.getOrNull()
    }

    private fun PlayQuality.wireName(): String = name.lowercase()
}
