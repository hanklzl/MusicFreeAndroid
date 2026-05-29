package com.hank.musicfree.player.prefetch

import android.net.Uri
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.source.HeaderInjectingDataSourceFactory
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class PrefetchContext(
    val tick: ProgressTick,
    val next: MusicItem?,
    val isWifi: Boolean,
    val quality: PlayQuality,
)

data class ProgressTick(val item: MusicItem, val positionMs: Long, val durationMs: Long) {
    val ratio: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

/**
 * Triggers a single-buffer head prefetch for the next queue item once playback of the current
 * track passes [TRIGGER_RATIO]. Idempotent per (next item, quality) key. Wi-Fi only by default.
 *
 * When [headerInjectingDataSourceFactory] is non-null, also performs a 512KB head-warm read via
 * SimpleCache so that subsequent playback hits the already-cached bytes.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
open class PrefetchCoordinator(
    private val resolver: MediaSourceResolver,
    private val progressFlow: Flow<ProgressTick>,
    private val nextItemFlow: StateFlow<MusicItem?>,
    private val isWifiFlow: StateFlow<Boolean>,
    private val currentQualityFlow: StateFlow<PlayQuality>,
    private val cacheKeyRegistrar: PlaybackCacheKeyRegistrar,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val headerInjectingDataSourceFactory: HeaderInjectingDataSourceFactory? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var runningJob: Job? = null
    @Volatile private var lastPrefetchedKey: String? = null

    fun start() {
        scope.launch {
            combine(progressFlow, nextItemFlow, isWifiFlow, currentQualityFlow) { tick, next, wifi, quality ->
                PrefetchContext(tick, next, wifi, quality)
            }.distinctUntilChanged().collect { (tick, next, wifi, quality) ->
                if (next == null) return@collect
                if (!wifi) return@collect
                if (tick.ratio < TRIGGER_RATIO) return@collect
                val qualityName = quality.wireName()
                val key = "${next.platform}:${next.id}:$qualityName"
                if (key == lastPrefetchedKey) return@collect
                lastPrefetchedKey = key
                runningJob?.cancel()
                runningJob = launch { runPrefetch(next, quality) }
            }
        }
    }

    fun stop() { scope.cancel() }

    private suspend fun runPrefetch(next: MusicItem, quality: PlayQuality) {
        val requestedQuality = quality.wireName()
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "prefetch_start",
            fields = mapOf(
                "platform" to next.platform,
                "id" to next.id,
                "requestedQuality" to requestedQuality,
            ),
        )
        val explicitResolution = runCatching { resolver.resolve(next, requestedQuality, null) }.getOrElse { error ->
            if (error is CancellationException) throw error
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "prefetch_failed",
                fields = logFieldSet(
                    next = next,
                    requestedQuality = requestedQuality,
                    effectiveQuality = requestedQuality,
                    quality = requestedQuality,
                    cachePolicy = null,
                    fallback = false,
                    fallbackUnknownQuality = false,
                ).plus(
                    mapOf(
                        "reason" to "resolve_failed",
                        "errorClass" to error::class.java.name,
                    ),
                ),
            )
            null
        }
        val (resolution, fallbackUsed) = if (explicitResolution != null) {
            explicitResolution to false
        } else {
            val fallbackResolution = runCatching {
                resolver.resolve(next, null, null)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "prefetch_failed",
                    fields = logFieldSet(
                        next = next,
                        requestedQuality = requestedQuality,
                        effectiveQuality = "",
                        quality = "",
                        cachePolicy = null,
                        fallback = true,
                        fallbackUnknownQuality = true,
                    ).plus(
                        mapOf(
                            "reason" to "resolve_failed",
                            "errorClass" to error::class.java.name,
                        ),
                    ),
                )
                return
            } ?: run {
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "prefetch_failed",
                    fields = logFieldSet(
                        next = next,
                        requestedQuality = requestedQuality,
                        effectiveQuality = "",
                        quality = "",
                        cachePolicy = null,
                        fallback = true,
                        fallbackUnknownQuality = true,
                    ).plus(
                        mapOf("reason" to "resolve_returned_null"),
                    ),
                )
                return
            }
            fallbackResolution to true
        }

        val effectiveQuality = resolution.source.quality ?: quality
        val fallbackUnknownQuality = fallbackUsed && resolution.source.quality == null
        val cachePolicy = resolution.cachePolicy
        val effectiveQualityWire = effectiveQuality.wireName()

        cacheKeyRegistrar.register(
            platform = next.platform,
            itemId = next.id,
            url = resolution.source.url,
            headers = resolution.source.headers.orEmpty(),
            userAgent = resolution.source.userAgent,
            quality = effectiveQuality,
            cachePolicy = cachePolicy,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        if (cachePolicy == MediaSourceCachePolicy.NoStore) {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "prefetch_skipped",
                fields = logFieldSet(
                    next = next,
                    requestedQuality = requestedQuality,
                    effectiveQuality = effectiveQualityWire,
                    quality = effectiveQualityWire,
                    cachePolicy = cachePolicy,
                    fallback = fallbackUsed,
                    fallbackUnknownQuality = fallbackUnknownQuality,
                ).plus(
                    mapOf("reason" to "no_store"),
                ),
            )
            return
        }

        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "prefetch_success",
            fields = logFieldSet(
                next = next,
                requestedQuality = requestedQuality,
                effectiveQuality = effectiveQualityWire,
                quality = effectiveQualityWire,
                cachePolicy = cachePolicy,
                fallback = fallbackUsed,
                fallbackUnknownQuality = fallbackUnknownQuality,
            ),
        )

        // Head-warm: open a CacheDataSource and read 512 KB from the start of the resolved URL.
        // Subsequent playback will hit those bytes.
        try {
            warmHead(resolution.source.url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "prefetch_failed",
                fields = logFieldSet(
                    next = next,
                    requestedQuality = requestedQuality,
                    effectiveQuality = effectiveQualityWire,
                    quality = effectiveQualityWire,
                    cachePolicy = cachePolicy,
                    fallback = fallbackUsed,
                    fallbackUnknownQuality = fallbackUnknownQuality,
                ).plus(
                    mapOf(
                        "reason" to "warm_failed",
                        "errorClass" to error::class.java.name,
                    ),
                ),
            )
        }
    }

    private fun PlayQuality.wireName(): String = this.name.lowercase()

    protected open fun warmHead(url: String) {
        val factory = headerInjectingDataSourceFactory ?: return
        if (url.isBlank()) return
        val dataSpec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setPosition(0L)
            .setLength(HEAD_WARM_BYTES.toLong())
            .build()
        val dataSource = factory.createDataSource()
        try {
            dataSource.open(dataSpec)
            val buffer = ByteArray(8 * 1024)
            var totalRead = 0
            while (totalRead < HEAD_WARM_BYTES) {
                val n = dataSource.read(buffer, 0, minOf(buffer.size, HEAD_WARM_BYTES - totalRead))
                if (n == C.RESULT_END_OF_INPUT) break
                totalRead += n
            }
        } finally {
            runCatching { dataSource.close() }
        }
    }

    private fun logFieldSet(
        next: MusicItem,
        requestedQuality: String,
        effectiveQuality: String,
        quality: String,
        cachePolicy: MediaSourceCachePolicy?,
        fallback: Boolean,
        fallbackUnknownQuality: Boolean,
        extra: Map<String, Any> = emptyMap(),
    ): Map<String, Any> {
        val fields = mutableMapOf<String, Any>(
            "platform" to next.platform,
            "id" to next.id,
            "requestedQuality" to requestedQuality,
            "effectiveQuality" to effectiveQuality,
            "quality" to quality,
            "fallback" to fallback,
            "fallback_unknown_quality" to fallbackUnknownQuality,
        )
        fields["cachePolicy"] = cachePolicy?.wire ?: ""
        fields.putAll(extra)
        return fields
    }

    companion object {
        const val TRIGGER_RATIO = 0.6f
        const val HEAD_WARM_BYTES = 512 * 1024
    }
}
