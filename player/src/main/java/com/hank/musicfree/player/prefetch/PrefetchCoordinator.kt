package com.hank.musicfree.player.prefetch

import android.net.Uri
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.source.HeaderInjectingDataSourceFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ProgressTick(val item: MusicItem, val positionMs: Long, val durationMs: Long) {
    val ratio: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

/**
 * Triggers a single-buffer head prefetch for the next queue item once playback of the current
 * track passes [TRIGGER_RATIO]. Idempotent per (next item key). Wi-Fi only by default.
 *
 * When [headerInjectingDataSourceFactory] is non-null, also performs a 512KB head-warm read via
 * SimpleCache so that subsequent playback hits the already-cached bytes.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
class PrefetchCoordinator(
    private val resolver: MediaSourceResolver,
    private val progressFlow: Flow<ProgressTick>,
    private val nextItemFlow: StateFlow<MusicItem?>,
    private val isWifiFlow: StateFlow<Boolean>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val headerInjectingDataSourceFactory: HeaderInjectingDataSourceFactory? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var runningJob: Job? = null
    @Volatile private var lastPrefetchedKey: String? = null

    fun start() {
        scope.launch {
            combine(progressFlow, nextItemFlow, isWifiFlow) { tick, next, wifi ->
                Triple(tick, next, wifi)
            }.distinctUntilChanged().collect { (tick, next, wifi) ->
                if (next == null) return@collect
                if (!wifi) return@collect
                if (tick.ratio < TRIGGER_RATIO) return@collect
                val key = "${next.platform}:${next.id}"
                if (key == lastPrefetchedKey) return@collect
                lastPrefetchedKey = key
                runningJob?.cancel()
                runningJob = launch { runPrefetch(next) }
            }
        }
    }

    fun stop() { scope.cancel() }

    private suspend fun runPrefetch(next: MusicItem) {
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "prefetch_start",
            fields = mapOf("platform" to next.platform, "id" to next.id),
        )
        val resolution = runCatching { resolver.resolve(next, null, null) }.getOrNull()
        if (resolution == null) {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "prefetch_failed",
                fields = mapOf(
                    "platform" to next.platform, "id" to next.id,
                    "reason" to "resolve_returned_null",
                ),
            )
            return
        }
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "prefetch_success",
            fields = mapOf("platform" to next.platform, "id" to next.id),
        )
        // Head-warm: open a CacheDataSource and read 512 KB from the start of the resolved URL
        // so SimpleCache fills in the first bytes. Subsequent playback will hit those bytes.
        runCatching {
            val url = resolution.source.url
            if (url.isNotBlank()) warmHead(url)
        }.onFailure { error ->
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "prefetch_failed",
                fields = mapOf(
                    "platform" to next.platform, "id" to next.id,
                    "reason" to (error.javaClass.simpleName ?: "exception"),
                ),
            )
        }
    }

    private fun warmHead(url: String) {
        val factory = headerInjectingDataSourceFactory ?: return
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

    companion object {
        const val TRIGGER_RATIO = 0.6f
        const val HEAD_WARM_BYTES = 512 * 1024
    }
}
