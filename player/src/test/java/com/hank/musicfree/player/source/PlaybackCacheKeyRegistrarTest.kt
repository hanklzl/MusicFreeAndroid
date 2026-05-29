package com.hank.musicfree.player.source

import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCacheKeyRegistrarTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun `registers http url with empty headers and user-agent`() {
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)

        val result = registrar.register(
            platform = "test",
            itemId = "1",
            url = "https://cdn.example.test/audio.mp3",
            headers = emptyMap(),
            userAgent = null,
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.NoCache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
        )

        val entry = registry.get("https://cdn.example.test/audio.mp3")!!
        assertEquals(PlaybackCacheKeyRegistrar.Result.Registered("test:1"), result)
        assertEquals("test:1", entry.cacheKey)
        assertTrue(entry.headers.isEmpty())
        assertEquals(PlayQuality.STANDARD, entry.quality)
        assertEquals(MediaSourceCachePolicy.NoCache, entry.cachePolicy)
        assertTrue(entry.byteCacheAllowed)
    }

    @Test
    fun `NoStore policy disables byte cache`() {
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)

        registrar.register(
            platform = "test",
            itemId = "1",
            url = "https://cdn.example.test/no-store.mp3",
            headers = emptyMap(),
            userAgent = null,
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.NoStore,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
        )

        val entry = registry.get("https://cdn.example.test/no-store.mp3")!!
        assertFalse(entry.byteCacheAllowed)
    }

    @Test
    fun `non-http url is skipped`() {
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)

        val result = registrar.register(
            platform = "test",
            itemId = "1",
            url = "file:///tmp/audio.mp3",
            headers = emptyMap(),
            userAgent = null,
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.NoCache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
        )

        assertEquals(PlaybackCacheKeyRegistrar.Result.SkippedNonHttp("file"), result)
        assertEquals(null, registry.get("file:///tmp/audio.mp3"))
    }

    @Test
    fun `blank url is skipped`() {
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)

        val result = registrar.register(
            platform = "test",
            itemId = "1",
            url = "   ",
            headers = emptyMap(),
            userAgent = null,
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.NoCache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
        )

        assertEquals(PlaybackCacheKeyRegistrar.Result.SkippedBlankUrl, result)
        assertEquals(null, registry.get("   "))
    }

    @Test
    fun `successful registration logs fields`() {
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)
        val sink = RecordingLogger()
        MfLog.install(sink)

        val result = registrar.register(
            platform = "test",
            itemId = "1",
            url = "https://cdn.example.test/song.mp3",
            headers = mapOf("Referer" to "https://r.example"),
            userAgent = "UA/1.0",
            quality = PlayQuality.HIGH,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.STALE_REFRESH,
        )

        assertEquals(PlaybackCacheKeyRegistrar.Result.Registered("test:1"), result)
        val logged = sink.entries.single { it.event == "media_cache_key_registered" }
        assertEquals(LogCategory.PLAYER, logged.category)
        assertEquals("test", logged.fields["platform"])
        assertEquals("1", logged.fields["itemId"])
        assertEquals("high", logged.fields["quality"])
        assertEquals("stale_refresh", logged.fields["trigger"])
        assertEquals("cdn.example.test", logged.fields["host"])
        assertEquals("cache", logged.fields["cachePolicy"])
        assertEquals(true, logged.fields["byteCacheAllowed"])
        assertEquals(true, logged.fields["hasHeaders"])
        assertEquals(true, logged.fields["hasUserAgent"])
    }

    private class RecordingLogger : MfLogger {
        data class Entry(
            val event: String,
            val fields: Map<String, Any?>,
            val category: LogCategory,
        )

        val entries = mutableListOf<Entry>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields, category)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields, category)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            entries += Entry(event, fields, category)
        }

        override fun flush() = Unit
    }
}
