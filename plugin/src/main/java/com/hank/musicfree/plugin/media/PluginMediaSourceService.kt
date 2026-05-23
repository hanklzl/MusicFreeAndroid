package com.hank.musicfree.plugin.media

import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.media.StaleUrlRefresher
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.fallbackSequence
import com.hank.musicfree.core.telemetry.CacheHitSource
import com.hank.musicfree.core.telemetry.CacheMissReason
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.repository.CachedSource
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.network.PluginNetworkStateProvider
import com.hank.musicfree.plugin.playback.CacheControl
import com.hank.musicfree.plugin.playback.shouldUseCache
import com.hank.musicfree.plugin.playback.shouldWriteCache
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PluginMediaSourceService @Inject constructor(
    private val pluginManager: PluginManager,
    private val mediaCacheRepository: MediaCacheRepository,
    private val musicRepository: MusicRepository,
    private val localFileProbe: LocalFileProbe,
    private val playbackRuntimeSettings: PlaybackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
    private val networkStateProvider: PluginNetworkStateProvider = PluginNetworkStateProvider.AlwaysOnline,
    private val playCacheTelemetry: PlayCacheTelemetry,
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
        sid: String?,
    ): MediaSourceResolution? = doResolve(item, quality, useCache = true, sid = sid)

    /**
     * Bypass-cache entry used by the playback failure recovery path (spec §5.7).
     * Always skips the cache read; still writes cache on success unless the
     * source plugin declared `cacheControl == "no-store"`.
     */
    override suspend fun resolveFresh(
        item: MusicItem,
        quality: String?,
        sid: String?,
    ): MediaSourceResolution? = doResolve(item, quality, useCache = false, sid = sid)

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
        sid: String? = null,
    ): MediaSourceResolution? {
        // 0. Local short-circuit: if the item has a downloaded local copy, serve it directly.
        resolveLocal(item, sid)?.let { return it }

        val sourcePlugin = pluginManager.getPlugin(item.platform) ?: return null
        val cacheControl = CacheControl.parse(sourcePlugin.info.cacheControl)
        val isOffline = networkStateProvider.isOffline()

        // 1. Try cache (only when the caller permits it AND policy permits it).
        //
        // FOLLOW-UP (Phase A-3 review Important #1, RN parity): cache hit currently
        // bypasses the alternative-plugin override below. If the user newly maps
        // `kuwo -> kugou` after a kuwo URL was already cached, replay returns the
        // stale kuwo URL. Track as a follow-up; fix candidates: invalidate cache on
        // PluginMetaStore alternative-plugins change, or include resolver platform
        // in the cache key.
        if (useCache && shouldUseCache(cacheControl, isOffline = isOffline)) {
            // Important #2/#3 fix: when quality is null, target the user's
            // default play quality (the same quality the fetch loop will ask
            // for first) so caches written at HIGH/SUPER actually get re-read.
            val requestedQuality = if (quality.isNullOrBlank()) {
                playbackRuntimeSettings.defaultPlayQuality()
            } else {
                parseQualityOrDefault(quality)
            }
            val cached = mediaCacheRepository.get(item, requestedQuality)
            // Memory-vs-disk tier distinction lives behind an internal flag on
            // MediaCacheRepository (cross-module access not exposed). Logging the
            // generic "repo_db" layer is enough to answer "did the URL cache hit";
            // upgrade later if a finer split becomes necessary.
            playCacheTelemetry.resolveCacheLookup(
                sid = sid,
                layer = "repo_db",
                hit = cached != null,
                qualityMatched = if (cached != null) true else null,
                ageSeconds = null,
            )
            if (cached != null) {
                playCacheTelemetry.cacheHit(
                    sid = sid,
                    source = CacheHitSource.REPO_DB,
                    platform = item.platform,
                    id = item.id,
                    quality = requestedQuality.wireName(),
                    sizeBytes = null,
                )
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_get_media_source_cache_hit",
                    fields = mapOf(
                        "sid" to sid,
                        "platform" to item.platform,
                        "musicItemId" to item.id,
                        "quality" to requestedQuality.wireName(),
                        "cacheControl" to cacheControl.wire,
                        "offline" to isOffline,
                        "useCache" to useCache,
                    ),
                )
                return cached.toResolution(
                    item = item,
                    quality = requestedQuality,
                    resolverPlatform = sourcePlugin.info.platform,
                )
            }
        } else {
            logCacheReadSkipped(
                item = item,
                cacheControl = cacheControl,
                isOffline = isOffline,
                useCache = useCache,
            )
        }

        // 2. Walk quality candidates, ask plugin (optionally via alternative).
        val missReason = when {
            !useCache -> CacheMissReason.DISABLED
            cacheControl == CacheControl.NoStore -> CacheMissReason.DISABLED
            cacheControl == CacheControl.NoCache && !isOffline -> CacheMissReason.NO_CACHE_POLICY
            else -> CacheMissReason.COLD
        }
        playCacheTelemetry.cacheMiss(
            sid = sid,
            reason = missReason,
            platform = item.platform,
            id = item.id,
            quality = quality ?: playbackRuntimeSettings.defaultPlayQuality().name.lowercase(),
        )

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
                sid = sid,
            )
            if (viaAlternative != null) {
                maybeWriteCache(
                    cacheControl = cacheControl,
                    item = item,
                    candidateQuality = candidateQuality,
                    source = viaAlternative.source,
                    isOffline = isOffline,
                    useCache = useCache,
                    sid = sid,
                )
                return viaAlternative
            }

            val viaSource = sourcePlugin.resolveWith(
                item = item,
                quality = candidateQuality,
                requestedPlatform = item.platform,
                redirected = false,
                sid = sid,
            )
            if (viaSource != null) {
                maybeWriteCache(
                    cacheControl = cacheControl,
                    item = item,
                    candidateQuality = candidateQuality,
                    source = viaSource.source,
                    isOffline = isOffline,
                    useCache = useCache,
                    sid = sid,
                )
                return viaSource
            }
        }

        return null
    }

    /**
     * 在调用 plugin / 缓存之前尝试用 [MusicItem.localPath] 直接构造 [MediaSourceResolution]。
     *
     * - localPath 为空：返回 null，回到正常解析链路。
     * - localPath 可读：直接短路，emit `media_cache_hit{source=local}`。
     * - localPath 不可读：**destructive cleanup** — 调用 [MusicRepository.removeFromLocalLibrary] 永久
     *   清掉 DownloadedTrack 行与 MusicItem.localPath，然后回退到 plugin 路径。
     *
     * 已知 trade-off：transient I/O 失败（SD 卡临时拔出、ContentResolver 权限暂时被吊销）会被当作
     * "用户删除"对待，下载记录无法恢复，需要用户重新下载。这是为了避免"已下载"列表里存在永远播不出
     * 来的死记录。如果未来观测到 transient 误触发明显，可以考虑：
     *   1) 收紧触发条件（仅 file:// 且父目录存在但文件缺失时清理）；
     *   2) 把清理移到后台 sweep，避免阻塞解析路径；
     *   3) 使用计数器，连续 N 次失败才清。
     */
    private suspend fun resolveLocal(item: MusicItem, sid: String?): MediaSourceResolution? {
        // Bug fix (v1.2.5): When the incoming MusicItem comes from a plugin / search
        // result, its `localPath` is null even when the user has actually downloaded
        // the track (the download is recorded in `music_items.localPath` by
        // `MusicRepository.commitDownloadedTrack`, but UI list items from the plugin
        // path don't carry it). Reverse-query the DB once so all entry points
        // benefit from the local short-circuit.
        val effectiveItem = if (item.localPath.isNullOrBlank()) {
            val recovered = runCatching { musicRepository.getById(item.id, item.platform) }
                .getOrNull()
                ?.localPath
                ?.takeUnless { it.isBlank() }
            playCacheTelemetry.resolveLocalDbLookup(
                sid = sid,
                platform = item.platform,
                id = item.id,
                found = recovered != null,
            )
            if (recovered != null) item.copy(localPath = recovered) else item
        } else item

        val path = effectiveItem.localPath
        if (path.isNullOrBlank()) {
            playCacheTelemetry.resolveLocalCheck(sid ?: "", hasLocalPath = false, localPathReadable = null)
            return null
        }

        return if (localFileProbe.isReadable(path)) {
            playCacheTelemetry.resolveLocalCheck(sid ?: "", hasLocalPath = true, localPathReadable = true)
            val quality = playbackRuntimeSettings.defaultPlayQuality()
            playCacheTelemetry.cacheHit(
                sid = sid,
                source = CacheHitSource.LOCAL,
                platform = effectiveItem.platform,
                id = effectiveItem.id,
                quality = quality.name.lowercase(),
                sizeBytes = null,
            )
            MediaSourceResolution(
                item = effectiveItem.copy(url = path),
                source = MediaSourceResult(
                    url = path,
                    headers = null,
                    userAgent = null,
                    quality = quality,
                ),
                requestedPlatform = effectiveItem.platform,
                resolverPlatform = effectiveItem.platform,
                redirected = false,
            )
        } else {
            playCacheTelemetry.resolveLocalCheck(sid ?: "", hasLocalPath = true, localPathReadable = false)
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "local_short_circuit_fallback",
                fields = mapOf(
                    "sid" to sid,
                    "platform" to effectiveItem.platform,
                    "id" to effectiveItem.id,
                    "reason" to "unreadable",
                    // Path tail only (last 40 chars) to avoid logging full storage
                    // paths; enough to tell foo/Music/track.mp3 from another path.
                    "localPathTail" to path.takeLast(40),
                    "isContentUri" to path.startsWith("content://"),
                ),
            )
            musicRepository.removeFromLocalLibrary(effectiveItem)
            null
        }
    }

    private suspend fun maybeWriteCache(
        cacheControl: CacheControl,
        item: MusicItem,
        candidateQuality: String,
        source: MediaSourceResult,
        isOffline: Boolean,
        useCache: Boolean,
        sid: String?,
    ) {
        if (!shouldWriteCache(cacheControl, isOffline)) return
        // Important #4 fix: don't pollute the STANDARD slot with payloads that
        // came back for an unknown wire quality. parseStrictQuality returns null
        // when the wire string does not map to a known PlayQuality.
        val pq = parseStrictQuality(candidateQuality) ?: run {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_get_media_source_cache_write_skipped_unknown_quality",
                fields = mapOf(
                    "sid" to sid,
                    "platform" to item.platform,
                    "musicItemId" to item.id,
                    "quality" to candidateQuality,
                    "cacheControl" to cacheControl.wire,
                    "offline" to isOffline,
                    "useCache" to useCache,
                ),
            )
            return
        }
        mediaCacheRepository.put(item, pq, source)
        playCacheTelemetry.cacheWriteEvent(sid = sid, layer = "repo_db", sizeBytes = null)
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_get_media_source_cache_write",
            fields = mapOf(
                "sid" to sid,
                "platform" to item.platform,
                "musicItemId" to item.id,
                "quality" to pq.wireName(),
                "cacheControl" to cacheControl.wire,
                "offline" to isOffline,
                "useCache" to useCache,
            ),
        )
    }

    private fun logCacheReadSkipped(
        item: MusicItem,
        cacheControl: CacheControl,
        isOffline: Boolean,
        useCache: Boolean,
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_get_media_source_cache_read_skipped",
            fields = mapOf(
                "platform" to item.platform,
                "musicItemId" to item.id,
                "cacheControl" to cacheControl.wire,
                "offline" to isOffline,
                "useCache" to useCache,
                "reason" to cacheReadSkipReason(
                    cacheControl = cacheControl,
                    isOffline = isOffline,
                    useCache = useCache,
                ),
            ),
        )
    }

    private fun cacheReadSkipReason(
        cacheControl: CacheControl,
        isOffline: Boolean,
        useCache: Boolean,
    ): String = when {
        !useCache -> "caller_bypassed_cache"
        cacheControl == CacheControl.NoStore -> "cache_control_no_store"
        cacheControl == CacheControl.NoCache && !isOffline -> "policy_no_cache_online"
        else -> "cache_policy_not_allowed"
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
        sid: String?,
    ): MediaSourceResolution? {
        if (!supportsMediaSource()) return null
        playCacheTelemetry.resolvePluginCallStart(sid = sid, pluginName = info.platform)
        val startedAt = System.nanoTime()
        val source = runCatching { getMediaSource(item, quality) }.getOrNull()
            ?.takeIf { it.url.isNotBlank() }
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        playCacheTelemetry.resolvePluginCallEnd(
            sid = sid,
            durationMs = durationMs,
            returnedQuality = source?.quality?.name?.lowercase(),
            hasUrl = source != null,
            hasHeaders = !source?.headers.isNullOrEmpty(),
            urlHash = source?.url?.let { playCacheTelemetry.urlHash(it) },
        )
        if (source == null) return null
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
