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
import androidx.media3.datasource.TransferListener
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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource.Factory that:
 * 1. routes Media3 HTTP through @BaseOkHttp (流量统计 EventListener)
 * 2. injects per-track HTTP headers/UA from TrackHeaderRegistry
 * 3. caches via SimpleCache with policy-aware fallback to uncached transport
 *
 * Non-HTTP/HTTPS media sources are never cached and never trigger registry miss logs.
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

    data class OpenDecision(
        val useCache: Boolean,
        val cacheKey: String,
        val cacheBypassReason: String?,
    )

    private val upstreamFactory = DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory(okHttpClient),
    )

    private val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
        resolveDataSpec(dataSpec)
    }

    private fun newCacheDataSourceFactory(): CacheDataSource.Factory? {
        val cache = simpleCacheHolder.current ?: return null
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(C.LENGTH_UNSET.toLong())
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Pure DataSpec transformer for the [ResolvingDataSource]:
     * - non-http(s) schemes are passed through untouched (file://, asset://...)
     * - registry miss leaves the DataSpec alone (no header injection, no cacheKey)
     * - registry hit merges headers, fills in UA only if absent (case-insensitive),
     *   and applies no stable cache key directly on DataSpec.
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
        return dataSpec.buildUpon().setHttpRequestHeaders(merged).build()
    }

    /**
     * Returns the stable cache key for the given [uri].
     *
     * Key format:
     * - Registry hit with cacheKey + quality  → `"<cacheKey>:<quality.name.lowercase>"`
     * - Registry hit with cacheKey, no quality → `"<cacheKey>:unknown"`
     * - Registry miss (http(s))               → `uri.toString()`, logging miss
     */
    internal fun cacheKeyFor(uri: Uri): String {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return uri.toString()

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

    internal fun resolveOpenDecision(dataSpec: DataSpec): OpenDecision {
        val scheme = dataSpec.uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return OpenDecision(
                useCache = false,
                cacheKey = dataSpec.uri.toString(),
                cacheBypassReason = null,
            )
        }

        val entry = registry.get(dataSpec.uri.toString())
        val cache = simpleCacheHolder.current
        return when {
            entry != null && !entry.byteCacheAllowed -> OpenDecision(
                useCache = false,
                cacheKey = cacheKeyFor(dataSpec.uri),
                cacheBypassReason = "no_store",
            )
            else -> OpenDecision(
                useCache = cache != null,
                cacheKey = cacheKeyFor(dataSpec.uri),
                cacheBypassReason = null,
            )
        }
    }

    override fun createDataSource(): DataSource =
        InstrumentedSource(
            resolveDataSource = { dataSpec ->
                val decision = resolveOpenDecision(dataSpec)
                val session = eventBridge.newSession(
                    cacheKey = decision.cacheKey,
                    cacheBypassReason = decision.cacheBypassReason,
                )
                val delegate = if (decision.useCache) {
                    newCacheDataSourceFactory()?.let { factory ->
                        factory.setEventListener(session)
                            .setCacheKeyFactory { spec ->
                                cacheKeyFor(spec.uri)
                            }
                            .createDataSource()
                    } ?: resolvingFactory.createDataSource()
                } else {
                    resolvingFactory.createDataSource()
                }
                Pair(delegate, session)
            },
        )

    internal class InstrumentedSource(
        private val resolveDataSource: (DataSpec) -> Pair<DataSource, CacheDataSourceEventBridge.Session>,
    ) : DataSource {
        private var delegate: DataSource? = null
        private var session: CacheDataSourceEventBridge.Session? = null
        private var transferListeners: MutableList<TransferListener> = mutableListOf()

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners.add(transferListener)
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            closeAndReset()
            val (nextDelegate, nextSession) = resolveDataSource(dataSpec)
            session = nextSession
            delegate = nextDelegate
            transferListeners.forEach(nextDelegate::addTransferListener)
            return try {
                nextDelegate.open(dataSpec)
            } catch (error: IOException) {
                throw closeAndReset(openError = error) ?: error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val delegate = requireNotNull(delegate) { "DataSource.read called before open()" }
            val bytesRead = delegate.read(buffer, offset, length)
            if (bytesRead > 0) {
                session?.addBytesRead(bytesRead.toLong())
            }
            return bytesRead
        }

        override fun getUri() = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

        override fun close() {
            closeAndReset()?.let { throw it }
        }

        private fun closeAndReset(openError: IOException? = null): IOException? {
            val currentDelegate = delegate
            val currentSession = session
            delegate = null
            session = null
            var closeError: IOException? = null

            if (currentDelegate != null) {
                try {
                    currentDelegate.close()
                } catch (error: IOException) {
                    closeError = error
                }
            }
            currentSession?.closeOnce()

            return when {
                openError != null -> {
                    if (closeError != null) openError.addSuppressed(closeError)
                    openError
                }
                closeError != null -> closeError
                else -> null
            }
        }
    }
}
