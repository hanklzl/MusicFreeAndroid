package com.hank.musicfree.core.network

import com.hank.musicfree.core.util.Clock
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkTrafficEventListenerTest {
    private val fakeClock = object : Clock { override fun now() = 1_700_000_000_000L }
    private val fakeDetector = mockk<NetworkTypeDetector> {
        every { current() } returns NetworkType.WIFI
    }
    private val captured = mutableListOf<TrafficSample>()
    private val sink = object : TrafficSampleSink {
        override fun offer(sample: TrafficSample) { captured += sample }
    }
    private val call = mockk<Call>(relaxed = true)

    private fun newListener() = NetworkTrafficEventListener.Factory(sink, fakeDetector, fakeClock)
        .create(call) as NetworkTrafficEventListener

    @Test fun accumulates_request_and_response_body_bytes() {
        val l = newListener()
        l.callStart(call)
        l.requestBodyEnd(call, 100)
        l.responseBodyEnd(call, 500)
        l.callEnd(call)
        assertEquals(1, captured.size)
        assertEquals(100L, captured[0].bytesSent)
        assertEquals(500L, captured[0].bytesReceived)
        assertEquals(NetworkType.WIFI, captured[0].networkType)
    }

    @Test fun callFailed_still_flushes_partial_bytes() {
        captured.clear()
        val l = newListener()
        l.callStart(call)
        l.requestBodyEnd(call, 50)
        l.callFailed(call, IOException("boom"))
        assertEquals(1, captured.size)
        assertEquals(50L, captured[0].bytesSent)
    }

    @Test fun zero_bytes_call_does_not_offer() {
        captured.clear()
        val l = newListener()
        l.callStart(call)
        l.callFailed(call, IOException("dns"))
        assertEquals(0, captured.size)
    }

    @Test fun factory_returns_new_instance_per_call() {
        val factory = NetworkTrafficEventListener.Factory(sink, fakeDetector, fakeClock)
        val a = factory.create(call); val b = factory.create(call)
        assertTrue(a !== b)
    }

    @Test fun retry_resets_counters_and_flushes_each_attempt() {
        captured.clear()
        val l = newListener()
        l.callStart(call)
        l.responseBodyEnd(call, 100)
        l.callFailed(call, IOException("first attempt"))
        l.callStart(call)
        l.responseBodyEnd(call, 200)
        l.callEnd(call)
        assertEquals(2, captured.size)
        assertEquals(100L, captured[0].bytesReceived)
        assertEquals(200L, captured[1].bytesReceived)
    }
}
