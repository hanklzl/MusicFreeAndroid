package com.hank.musicfree.player.cache

import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheDataSourceEventBridgeTest {

    @Test
    fun `newSession emits media3_datasource_open once with provided cacheKey and current sid`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider()
        val sid = sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

        bridge.newSession(cacheKey = "kugou:song1:high")

        val entry = sink.entries.single { it.event == "media3_datasource_open" }
        assertEquals("media3_datasource_open", entry.event)
        assertEquals("kugou:song1:high", entry.fields["cacheKey"])
        assertEquals(sid, entry.fields["sid"])
    }

    @Test
    fun `newSession emits cacheHit=false and zero byte counters on open`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider()
        sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

        bridge.newSession(cacheKey = "netease:abc:standard")

        val entry = sink.entries.single { it.event == "media3_datasource_open" }
        assertEquals(false, entry.fields["cacheHit"])
        assertEquals(0L, entry.fields["bytesFromCache"])
        assertEquals(0L, entry.fields["bytesFromUpstream"])
    }

    @Test
    fun `newSession without active session emits null sid`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider() // no session started
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

        bridge.newSession(cacheKey = "qq:song2:low")

        val entry = sink.entries.single { it.event == "media3_datasource_open" }
        assertEquals(null, entry.fields["sid"])
    }

    @Test
    fun `session emits close once with correct byte accounting`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider()
        sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)
        val session = bridge.newSession("qq:cached:standard")

        session.onCachedBytesRead(100L, 40L)
        session.addBytesRead(160L)
        session.closeOnce()
        session.closeOnce()

        val closeEvent = sink.entries.single { it.event == "media3_datasource_close" }
        assertEquals("media3_datasource_close", closeEvent.event)
        assertEquals("qq:cached:standard", closeEvent.fields["cacheKey"])
        assertEquals(40L, closeEvent.fields["bytesFromCache"])
        assertEquals(120L, closeEvent.fields["bytesFromUpstream"])
        assertEquals(true, closeEvent.fields["cacheHit"])
    }

    @Test
    fun `close calculation never emits negative upstream bytes`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider()
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)
        val session = bridge.newSession("open:test")

        session.addBytesRead(10L)
        session.onCachedBytesRead(10L, 20L)
        session.closeOnce()

        val closeEvent = sink.entries.single { it.event == "media3_datasource_close" }
        assertEquals(0L, closeEvent.fields["bytesFromUpstream"])
        assertEquals(20L, closeEvent.fields["bytesFromCache"])
        assertEquals(true, closeEvent.fields["cacheHit"])
    }

    @Test
    fun `session logs bypass reason when provided`() {
        val sink = RecordingLogger()
        val telemetry = PlayCacheTelemetry(sink)
        val sidProvider = CurrentSidProvider()
        sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)
        val session = bridge.newSession("open:policy", cacheBypassReason = "no_store")
        session.closeOnce()

        val bypass = sink.entries.single { it.event == "media_cache_bypass" }
        assertEquals("no_store", bypass.fields["reason"])
        assertEquals("open:policy", bypass.fields["cacheKey"])
        val openEvent = sink.entries.single { it.event == "media3_datasource_open" }
        assertEquals("open:policy", openEvent.fields["cacheKey"])
        val closeEvent = sink.entries.single { it.event == "media3_datasource_close" }
        assertEquals("media_cache_bypass", bypass.event)
        assertEquals("no_store", closeEvent.fields["cacheBypassReason"])
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
