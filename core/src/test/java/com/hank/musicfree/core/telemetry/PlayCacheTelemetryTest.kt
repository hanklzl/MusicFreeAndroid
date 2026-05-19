package com.hank.musicfree.core.telemetry

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayCacheTelemetryTest {
    private lateinit var sink: RecordingLogger
    private lateinit var telemetry: PlayCacheTelemetry

    @Before fun setUp() {
        sink = RecordingLogger()
        telemetry = PlayCacheTelemetry(sink)
    }

    @Test fun `playSessionStart emits play_session_start with required fields`() {
        telemetry.playSessionStart(
            sid = "ps_abc123",
            platform = "kugou",
            id = "song1",
            requestedQuality = "standard",
            networkType = "wifi",
            isOnline = true,
        )
        val entry = sink.entries.single()
        assertEquals("play_session_start", entry.event)
        assertEquals("ps_abc123", entry.fields["sid"])
        assertEquals("kugou", entry.fields["platform"])
        assertEquals("standard", entry.fields["requestedQuality"])
        assertEquals(true, entry.fields["isOnline"])
    }

    @Test fun `cacheHit emits media_cache_hit with source enum`() {
        telemetry.cacheHit(
            sid = "ps_abc123",
            source = CacheHitSource.LOCAL,
            platform = "kugou",
            id = "song1",
            quality = "standard",
            sizeBytes = null,
        )
        assertEquals("media_cache_hit", sink.entries.single().event)
        assertEquals("local", sink.entries.single().fields["source"])
    }

    @Test fun `cacheMiss emits media_cache_miss with reason enum`() {
        telemetry.cacheMiss(
            sid = "ps_abc123",
            reason = CacheMissReason.COLD,
            platform = "kugou",
            id = "song1",
            quality = "standard",
        )
        assertEquals("media_cache_miss", sink.entries.single().event)
        assertEquals("cold", sink.entries.single().fields["reason"])
    }

    @Test fun `urlHash returns 8 hex chars of sha1`() {
        val h = telemetry.urlHash("https://example/song.mp3")
        assertEquals(8, h.length)
        assertTrue(h.all { it in '0'..'9' || it in 'a'..'f' })
    }

    // -----------------------------------------------------------------------
    // Table-driven: all 9 diagnostic methods
    // -----------------------------------------------------------------------

    private data class Quadruple<T>(
        val expectedEvent: String,
        val expectedCategory: LogCategory,
        val description: String,
        val invoke: (T) -> Unit,
    )

    @Test fun `all 9 diagnostic methods emit with expected event name and category`() {
        val table = listOf(
            Quadruple("play_session_start", LogCategory.PLAYER, "playSessionStart") { t: PlayCacheTelemetry ->
                t.playSessionStart("ps_1", "p", "i", "standard", "wifi", true)
            },
            Quadruple("resolve_local_check", LogCategory.PLAYER, "resolveLocalCheck") { t ->
                t.resolveLocalCheck("ps_1", hasLocalPath = true, localPathReadable = true)
            },
            Quadruple("resolve_cache_lookup", LogCategory.PLAYER, "resolveCacheLookup") { t ->
                t.resolveCacheLookup("ps_1", "mem", hit = true, qualityMatched = true, ageSeconds = 0L)
            },
            Quadruple("resolve_plugin_call_start", LogCategory.PLAYER, "resolvePluginCallStart") { t ->
                t.resolvePluginCallStart("ps_1", "TestPlugin")
            },
            Quadruple("resolve_plugin_call_end", LogCategory.PLAYER, "resolvePluginCallEnd") { t ->
                t.resolvePluginCallEnd("ps_1", 100L, "standard", hasUrl = true, hasHeaders = false, urlHash = null)
            },
            Quadruple("cache_write", LogCategory.DATA, "cacheWriteEvent") { t ->
                t.cacheWriteEvent("ps_1", "db", 1024L)
            },
            Quadruple("media3_datasource_open", LogCategory.PLAYER, "media3DataSourceOpen") { t ->
                t.media3DataSourceOpen("ps_1", "key", cacheHit = true, bytesFromCache = 0L, bytesFromUpstream = 0L)
            },
            Quadruple("media3_datasource_error", LogCategory.PLAYER, "media3DataSourceError") { t ->
                t.media3DataSourceError("ps_1", 1, null, 0L, null, 0)
            },
            Quadruple("play_session_end", LogCategory.PLAYER, "playSessionEnd") { t ->
                t.playSessionEnd("ps_1", 60000L, 0L, 60000L, "partial")
            },
        )
        for ((expectedEvent, expectedCategory, description, invoke) in table) {
            sink.entries.clear()
            invoke(telemetry)
            val e = sink.entries.single()
            assertEquals("$description: event name", expectedEvent, e.event)
            assertEquals("$description: category", expectedCategory, e.category)
        }
    }

    // -----------------------------------------------------------------------
    // Table-driven: all 5 metric methods
    // -----------------------------------------------------------------------

    @Test fun `all 5 metric methods emit with expected event name and category`() {
        val table = listOf(
            Quadruple("media_cache_hit", LogCategory.PLAYER, "cacheHit") { t: PlayCacheTelemetry ->
                t.cacheHit("ps_1", CacheHitSource.LOCAL, "p", "i", "standard", null)
            },
            Quadruple("media_cache_miss", LogCategory.PLAYER, "cacheMiss") { t ->
                t.cacheMiss("ps_1", CacheMissReason.COLD, "p", "i", "standard")
            },
            Quadruple("media_cache_write", LogCategory.DATA, "cacheWrite") { t ->
                t.cacheWrite("ps_1", "db", null, null)
            },
            Quadruple("media_cache_evict", LogCategory.DATA, "cacheEvict") { t ->
                t.cacheEvict("plugin", 2, 1024L)
            },
            Quadruple("media_cache_lowspace", LogCategory.DATA, "cacheLowspace") { t ->
                t.cacheLowspace(100L, 200L, 150L)
            },
        )
        for ((expectedEvent, expectedCategory, description, invoke) in table) {
            sink.entries.clear()
            invoke(telemetry)
            val e = sink.entries.single()
            assertEquals("$description: event name", expectedEvent, e.event)
            assertEquals("$description: category", expectedCategory, e.category)
        }
    }

    private class RecordingLogger : MfLogger {
        data class Entry(val event: String, val fields: Map<String, Any?>, val category: LogCategory)
        val entries = mutableListOf<Entry>()
        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields, category)
        }
        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields, category)
        }
        override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) {
            entries += Entry(event, fields, category)
        }
        override fun flush() = Unit
    }
}
