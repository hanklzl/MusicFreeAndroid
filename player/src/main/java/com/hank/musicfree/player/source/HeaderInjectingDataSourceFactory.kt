package com.hank.musicfree.player.source

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.cache.CacheDataSourceEventBridge
import com.hank.musicfree.player.cache.SimpleCacheHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource.Factory that:
 * 1. routes Media3 HTTP through @BaseOkHttp (流量统计 EventListener)
 * 2. injects per-track HTTP headers/UA from TrackHeaderRegistry
 * 3. caches via SimpleCache with stable mediaId-based cache keys
 *
 * SimpleCache 不可用时降级到 OkHttpDataSource without caching (FLAG_IGNORE_CACHE_ON_ERROR style)
 */
@Singleton
@AndroidXOptIn(markerClass = [UnstableApi::class])
class HeaderInjectingDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @BaseOkHttp private val okHttpClient: OkHttpClient,
    private val registry: TrackHeaderRegistry,
    private val simpleCacheHolder: SimpleCacheHolder,
    private val eventBridge: CacheDataSourceEventBridge,
) : DataSource.Factory {

    /**
     * Pure DataSpec transformer for the [ResolvingDataSource]:
     * - non-http(s) schemes are passed through untouched (file://, asset://...)
     * - registry miss leaves the DataSpec alone (no header injection, no cacheKey)
     * - registry hit merges headers, fills in UA only if absent (case-insensitive),
     *   and applies the stable cacheKey via [DataSpec.Builder.setKey] so signature-rotated
     *   urls still hit the same SimpleCache entry.
     *
     * Visible for tests so the closure logic can be exercised without standing up Media3.
     */
    internal fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val scheme = dataSpec.uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return dataSpec
        val key = dataSpec.uri.toString()
        val entry = registry.get(key) ?: return dataSpec
        val merged = buildMap {
            putAll(dataSpec.httpRequestHeaders)
            putAll(entry.headers)
            entry.userAgent
                ?.takeIf { !this.containsKey("User-Agent") && !this.containsKey("user-agent") }
                ?.let { put("User-Agent", it) }
        }
        val builder = dataSpec.buildUpon().setHttpRequestHeaders(merged)
        return builder.build()
    }

    /**
     * Returns the stable cache key for the given [uri].
     *
     * Key format:
     * - Registry hit with cacheKey + quality  → `"<cacheKey>:<quality.name.lowercase>"`
     * - Registry hit with cacheKey, no quality → `"<cacheKey>:unknown"`
     * - Registry miss                           → `uri.toString()`
     *
     * Exposed as `internal` so tests can exercise the key-generation logic
     * without standing up a full Media3 pipeline.
     */
    internal fun cacheKeyFor(uri: Uri): String {
        val entry = registry.get(uri.toString())
        val cacheKey = entry?.cacheKey
        val quality = entry?.quality
        return when {
            cacheKey != null && quality != null -> "$cacheKey:${quality.name.lowercase()}"
            cacheKey != null -> "$cacheKey:unknown"
            else -> {
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "media_cache_key_registry_miss",
                    fields = mapOf("host" to (uri.host ?: ""), "scheme" to (uri.scheme ?: "")),
                )
                uri.toString()
            }
        }
    }

    override fun createDataSource(): DataSource {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        val resolving = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            resolveDataSpec(dataSpec)
        }
        val cache = simpleCacheHolder.current ?: return resolving.createDataSource()
        // Known limitation: cacheKey here is a placeholder because createDataSource() is called
        // once per ExoPlayer slot, not once per DataSpec. Actual per-DataSpec cache key is only
        // available inside the cacheKeyFactory lambda. Per-DataSpec byte tallies require a more
        // invasive CacheDataSource wrapping (tracked for Task 9+).
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolving)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(C.LENGTH_UNSET.toLong())
            )
            .setCacheKeyFactory { spec -> cacheKeyFor(spec.uri) }
            .setEventListener(eventBridge.newListener("(per-open)"))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()
    }
}
