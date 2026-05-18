package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.core.network.TrafficSample
import com.hank.musicfree.core.network.TrafficSampleSink
import com.hank.musicfree.core.util.Clock
import com.hank.musicfree.data.db.dao.TrafficDailyDao
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 OkHttp [TrafficSample] 累加到 [traffic_daily] 表的常驻 sink。
 *
 * 运行模型：
 * - 进程级单例，绑定到 [@ApplicationScope] —— 故意永不退出。
 * - 内部 [Channel] 解耦 HTTP 主线程与 IO 落盘，DROP_OLDEST 保证生产者从不阻塞。
 * - IO worker 协程接收 sample → 在内存按 `(date, networkType)` 聚合 →
 *   `flushIntervalMs` 窗口结束或批内累计达 [maxBatch] 条时刷盘。
 *
 * 测试约束：单测必须显式 `scope.cancel()` 来释放后台 worker；测试时建议传入更小的
 * `flushIntervalMs` 以避免真实墙钟等待。
 */
@Singleton
class TrafficSampleSinkImpl(
    private val dao: TrafficDailyDao,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    private val maxBatch: Int = DEFAULT_MAX_BATCH,
) : TrafficSampleSink {

    @Inject
    constructor(
        dao: TrafficDailyDao,
        clock: Clock,
        @ApplicationScope scope: CoroutineScope,
    ) : this(dao, clock, scope, DEFAULT_FLUSH_INTERVAL_MS, DEFAULT_MAX_BATCH)

    private val channel = Channel<TrafficSample>(
        capacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch(Dispatchers.IO) {
            val pending = mutableMapOf<Pair<String, String>, Accum>()
            while (isActive) {
                val first = channel.receive()
                aggregate(pending, first)
                val deadline = clock.now() + flushIntervalMs
                while (pending.size < maxBatch) {
                    val remaining = deadline - clock.now()
                    if (remaining <= 0) break
                    val next = withTimeoutOrNull(remaining) { channel.receive() } ?: break
                    aggregate(pending, next)
                }
                flush(pending)
                pending.clear()
            }
        }
    }

    override fun offer(sample: TrafficSample) {
        channel.trySend(sample)
    }

    private fun aggregate(p: MutableMap<Pair<String, String>, Accum>, s: TrafficSample) {
        val key = s.localDate.toString() to s.networkType.name
        val a = p.getOrPut(key) { Accum() }
        a.rx += s.bytesReceived
        a.tx += s.bytesSent
    }

    private suspend fun flush(p: Map<Pair<String, String>, Accum>) {
        if (p.isEmpty()) return
        val now = clock.now()
        val rows = p.map { (k, a) ->
            TrafficDailyEntity(
                localDate = k.first,
                networkType = k.second,
                bytesReceived = a.rx,
                bytesSent = a.tx,
                updatedAt = now,
            )
        }
        runCatching { dao.upsertAllAccumulating(rows) }
            .onFailure { error ->
                MfLog.error(
                    category = LogCategory.DATA,
                    event = "traffic_sink_flush_failed",
                    throwable = error,
                    fields = mapOf(
                        "rowCount" to rows.size,
                        "bytesTotal" to rows.sumOf { it.bytesReceived + it.bytesSent },
                    ),
                )
            }
    }

    private class Accum {
        var rx = 0L
        var tx = 0L
    }

    companion object {
        const val DEFAULT_FLUSH_INTERVAL_MS = 5_000L
        const val DEFAULT_MAX_BATCH = 64
    }
}
