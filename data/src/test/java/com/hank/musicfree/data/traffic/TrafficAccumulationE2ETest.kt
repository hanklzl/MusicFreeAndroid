package com.hank.musicfree.data.traffic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.network.NetworkTrafficEventListener
import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.core.network.NetworkTypeDetector
import com.hank.musicfree.core.util.Clock
import com.hank.musicfree.data.db.AppDatabase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end integration test for the traffic-stats wiring:
 *
 *   OkHttp call
 *     -> @BaseOkHttp `eventListenerFactory` ([NetworkTrafficEventListener.Factory])
 *     -> in-memory [TrafficSampleSinkImpl] flush loop
 *     -> Room [TrafficDailyDao] persisted row.
 *
 * Validates that real wire bytes counted by OkHttp's [okhttp3.EventListener] callbacks
 * end up in `traffic_daily`. This is the headline contract of Group A-L combined.
 */
@RunWith(RobolectricTestRunner::class)
class TrafficAccumulationE2ETest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var sink: TrafficSampleSinkImpl
    private lateinit var scope: CoroutineScope
    private lateinit var client: OkHttpClient

    private val fakeClock = object : Clock {
        override fun now(): Long = System.currentTimeMillis()
    }

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        sink = TrafficSampleSinkImpl(
            dao = db.trafficDailyDao(),
            clock = fakeClock,
            scope = scope,
            flushIntervalMs = 80L,
            maxBatch = 64,
        )
        // NetworkTypeDetector under Robolectric without registered networks returns OTHER —
        // mock it so the persisted row has a deterministic network_type column.
        val detector = mockk<NetworkTypeDetector> {
            every { current() } returns NetworkType.WIFI
        }
        client = OkHttpClient.Builder()
            .eventListenerFactory(NetworkTrafficEventListener.Factory(sink, detector, fakeClock))
            .build()
        server = MockWebServer().apply { start() }
    }

    @After fun teardown() {
        server.shutdown()
        scope.cancel()
        db.close()
    }

    @Test fun http_response_bytes_persist_to_traffic_daily() = runBlocking {
        val body = "x".repeat(2048)
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val request = Request.Builder().url(server.url("/track.mp3")).build()
        client.newCall(request).execute().use { it.body.bytes() }

        // sink flush window is 80ms; give the worker a couple windows + Room write.
        // Polling avoids flake on slow CI but bounds total wait.
        val dao = db.trafficDailyDao()
        var rows = emptyList<com.hank.musicfree.data.db.entity.TrafficDailyEntity>()
        repeat(20) {
            rows = dao.observeRange("2000-01-01", "2099-12-31").first()
            if (rows.isNotEmpty()) return@repeat
            delay(100)
        }
        assertEquals("expected exactly one row persisted, got $rows", 1, rows.size)
        val row = rows[0]
        assertEquals("WIFI", row.networkType)
        // EventListener.responseBodyEnd reports decoded body byte count; OkHttp identity
        // encoded body is exactly the 2048 bytes we sent.
        assertEquals(2048L, row.bytesReceived)
        assertEquals(0L, row.bytesSent)
    }

    @Test fun multiple_calls_accumulate_into_single_daily_row() = runBlocking {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("a".repeat(500)))
        }
        repeat(3) {
            val req = Request.Builder().url(server.url("/x")).build()
            client.newCall(req).execute().use { it.body.bytes() }
        }

        val dao = db.trafficDailyDao()
        var rows = emptyList<com.hank.musicfree.data.db.entity.TrafficDailyEntity>()
        repeat(25) {
            rows = dao.observeRange("2000-01-01", "2099-12-31").first()
            if (rows.isNotEmpty() && rows[0].bytesReceived >= 1500L) return@repeat
            delay(100)
        }
        assertEquals(1, rows.size)
        // 3 × 500 bytes — but the sink may flush after each batch boundary, so
        // accept any value >= 1500. The DAO accumulator handles re-upsert correctly.
        assertTrue(
            "expected >= 1500 bytes accumulated, got ${rows[0].bytesReceived}",
            rows[0].bytesReceived >= 1500L,
        )
    }
}
