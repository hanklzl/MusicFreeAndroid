package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.core.network.TrafficSample
import com.hank.musicfree.core.util.Clock
import com.hank.musicfree.data.db.dao.TrafficDailyDao
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrafficSampleSinkImplTest {

    /**
     * Sink 的 flush loop 跑在真实 `Dispatchers.IO` 上并通过 `System.currentTimeMillis()`
     * 自带时间窗口，所以这里必须用 `runBlocking` 走真实墙钟，而不是 `runTest` 的虚拟时间。
     * 用 `flushIntervalMs = 80L` 把测试控制在亚秒级。
     */
    @Test fun flushes_aggregated_rows_after_window() = runBlocking {
        val dao = mockk<TrafficDailyDao>(relaxed = true)
        val clock = object : Clock {
            override fun now(): Long = System.currentTimeMillis()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sink = TrafficSampleSinkImpl(
            dao = dao,
            clock = clock,
            scope = scope,
            flushIntervalMs = 80L,
            maxBatch = 64,
        )

        sink.offer(TrafficSample(LocalDate.of(2026, 5, 19), NetworkType.WIFI, 100, 10, 0))
        sink.offer(TrafficSample(LocalDate.of(2026, 5, 19), NetworkType.WIFI, 200, 20, 0))

        delay(250)
        val captured = slot<List<TrafficDailyEntity>>()
        coVerify { dao.upsertAllAccumulating(capture(captured)) }
        assertEquals(1, captured.captured.size)
        assertEquals(300L, captured.captured[0].bytesReceived)
        assertEquals(30L, captured.captured[0].bytesSent)
        scope.cancel()
    }
}
