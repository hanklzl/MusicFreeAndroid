package com.hank.musicfree.player.cache

import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheDataSourceEventBridgeTest {

    @Test
    fun `newListener emits media3_datasource_open once with provided cacheKey and current sid`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider()
        val sid = sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

        bridge.newListener(cacheKey = "kugou:song1:high")

        val entry = sink.entries.single { it.event == "media3_datasource_open" }
        assertEquals("media3_datasource_open", entry.event)
        assertEquals("kugou:song1:high", entry.fields["cacheKey"])
        assertEquals(sid, entry.fields["sid"])
    }

    @Test
    fun `newListener emits cacheHit=false and zero byte counters on open`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider()
        sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

        bridge.newListener(cacheKey = "netease:abc:standard")

        val entry = sink.entries.single { it.event == "media3_datasource_open" }
        assertEquals(false, entry.fields["cacheHit"])
        assertEquals(0L, entry.fields["bytesFromCache"])
        assertEquals(0L, entry.fields["bytesFromUpstream"])
    }

    @Test
    fun `newListener without active session emits null sid`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider() // no session started
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

        bridge.newListener(cacheKey = "qq:song2:low")

        val entry = sink.entries.single { it.event == "media3_datasource_open" }
        assertEquals(null, entry.fields["sid"])
    }

    private class RecordingLogger : MfLogger {
        data class Entry(val event: String, val fields: Map<String, Any?>)

        val entries = mutableListOf<Entry>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }

        override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }

        override fun flush() = Unit
    }
}
