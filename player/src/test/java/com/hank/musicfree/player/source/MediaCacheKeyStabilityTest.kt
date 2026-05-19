package com.hank.musicfree.player.source

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.cache.CacheDataSourceEventBridge
import com.hank.musicfree.player.cache.SimpleCacheHolder
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end stability test for the cacheKey path of [HeaderInjectingDataSourceFactory].
 *
 * After Task 3, cacheKey is no longer embedded in the DataSpec (resolveDataSpec no longer
 * calls setKey). Instead, the factory uses setCacheKeyFactory pointing to [cacheKeyFor].
 * These tests validate both the DataSpec-header path and the cacheKeyFor quality-aware key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@AndroidXOptIn(markerClass = [UnstableApi::class])
class MediaCacheKeyStabilityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var registry: TrackHeaderRegistry
    private lateinit var holder: SimpleCacheHolder
    private lateinit var factory: HeaderInjectingDataSourceFactory

    @Before fun setup() {
        registry = TrackHeaderRegistry()
        val mockPrefs = mock<AppPreferences>()
        whenever(mockPrefs.mediaCacheSchemaVersion).thenReturn(flowOf(1))
        val noOpLogger = object : MfLogger {
            override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) = Unit
            override fun flush() = Unit
        }
        holder = SimpleCacheHolder(ctx, mockPrefs, PlayCacheTelemetry(noOpLogger))
        val eventBridge = CacheDataSourceEventBridge(PlayCacheTelemetry(noOpLogger), CurrentSidProvider())
        factory = HeaderInjectingDataSourceFactory(
            context = ctx,
            okHttpClient = OkHttpClient(),
            registry = registry,
            simpleCacheHolder = holder,
            eventBridge = eventBridge,
        )
    }

    @After fun teardown() {
        holder.resetForClear()
    }

    // ── resolveDataSpec no longer sets DataSpec.key ──────────────────────────

    @Test fun `resolveDataSpec does not set DataSpec key (key now via cacheKeyFactory)`() {
        val url = "https://cdn.example.com/track.mp3?sig=AAA&t=1"
        registry.put(url, mapOf("Referer" to "ref"), "UA-v1", cacheKey = "media-7788")
        val spec = DataSpec.Builder().setUri(url).build()
        val out = factory.resolveDataSpec(spec)
        // Key must NOT be in the DataSpec — cacheKeyFactory handles it instead.
        assertNull(out.key)
    }

    @Test fun `registered entry without cacheKey leaves DataSpec key null`() {
        val url = "https://cdn.example.com/track.mp3"
        registry.put(url, mapOf("Referer" to "r"), "UA")
        val out = factory.resolveDataSpec(DataSpec.Builder().setUri(url).build())
        assertNull(out.key)
    }

    @Test fun `registry miss passes DataSpec through unchanged`() {
        val url = "https://cdn.example.com/unknown.mp3"
        val input = DataSpec.Builder().setUri(url).build()
        val out = factory.resolveDataSpec(input)
        assertNull(out.key)
        assertEquals(emptyMap<String, String>(), out.httpRequestHeaders)
    }

    @Test fun `non http scheme is passed through and not consulted against registry`() {
        val url = "file:///tmp/x.mp3"
        registry.put(url, mapOf("X" to "y"), "UA", cacheKey = "should-not-apply")
        val out = factory.resolveDataSpec(DataSpec.Builder().setUri(url).build())
        assertNull(out.key)
    }

    @Test fun `headers and UA are merged when entry has cacheKey`() {
        val url = "https://cdn.example.com/song.mp3"
        registry.put(
            url = url,
            headers = mapOf("Referer" to "https://r"),
            userAgent = "MusicFree/1.0",
            cacheKey = "media-42",
        )
        val out = factory.resolveDataSpec(DataSpec.Builder().setUri(url).build())
        // key is no longer in DataSpec — but headers are still merged
        assertNull(out.key)
        assertEquals("https://r", out.httpRequestHeaders["Referer"])
        assertEquals("MusicFree/1.0", out.httpRequestHeaders["User-Agent"])
    }

    @Test fun `caller provided User-Agent wins over registry UA`() {
        val url = "https://cdn.example.com/song.mp3"
        registry.put(url, emptyMap(), "RegistryUA", cacheKey = "k")
        val input = DataSpec.Builder().setUri(url)
            .setHttpRequestHeaders(mapOf("User-Agent" to "CallerUA"))
            .build()
        val out = factory.resolveDataSpec(input)
        assertEquals("CallerUA", out.httpRequestHeaders["User-Agent"])
        // key is no longer set in DataSpec
        assertNull(out.key)
    }

    // ── cacheKeyFor: quality-aware key generation ────────────────────────────

    @Test fun `cacheKeyFor with cacheKey and quality HIGH returns quality-suffixed key`() {
        val url = "https://cdn.example.com/song.mp3"
        registry.put(url, emptyMap(), null, cacheKey = "kugou:abc123", quality = PlayQuality.HIGH)
        assertEquals("kugou:abc123:high", factory.cacheKeyFor(Uri.parse(url)))
    }

    @Test fun `cacheKeyFor with cacheKey and quality LOW returns low suffix`() {
        val url = "https://cdn.example.com/low.mp3"
        registry.put(url, emptyMap(), null, cacheKey = "netease:xyz", quality = PlayQuality.LOW)
        assertEquals("netease:xyz:low", factory.cacheKeyFor(Uri.parse(url)))
    }

    @Test fun `cacheKeyFor with cacheKey and quality STANDARD returns standard suffix`() {
        val url = "https://cdn.example.com/std.mp3"
        registry.put(url, emptyMap(), null, cacheKey = "kugou:s1", quality = PlayQuality.STANDARD)
        assertEquals("kugou:s1:standard", factory.cacheKeyFor(Uri.parse(url)))
    }

    @Test fun `cacheKeyFor with cacheKey and quality SUPER returns super suffix`() {
        val url = "https://cdn.example.com/super.mp3"
        registry.put(url, emptyMap(), null, cacheKey = "qq:s2", quality = PlayQuality.SUPER)
        assertEquals("qq:s2:super", factory.cacheKeyFor(Uri.parse(url)))
    }

    @Test fun `cacheKeyFor with cacheKey and no quality returns unknown suffix`() {
        val url = "https://cdn.example.com/noq.mp3"
        registry.put(url, emptyMap(), null, cacheKey = "kugou:id1", quality = null)
        assertEquals("kugou:id1:unknown", factory.cacheKeyFor(Uri.parse(url)))
    }

    @Test fun `cacheKeyFor with registry miss returns uri string`() {
        val url = "https://cdn.example.com/unknown.mp3"
        assertEquals(url, factory.cacheKeyFor(Uri.parse(url)))
    }

    @Test fun `cacheKeyFor signature rotation produces identical key for two urls with same cacheKey`() {
        val urlV1 = "https://cdn.example.com/track.mp3?sig=AAA&t=1"
        val urlV2 = "https://cdn.example.com/track.mp3?sig=BBB&t=2"
        val stableKey = "media-7788"
        registry.put(urlV1, emptyMap(), null, cacheKey = stableKey, quality = PlayQuality.HIGH)
        registry.put(urlV2, emptyMap(), null, cacheKey = stableKey, quality = PlayQuality.HIGH)
        assertEquals(factory.cacheKeyFor(Uri.parse(urlV1)), factory.cacheKeyFor(Uri.parse(urlV2)))
        assertEquals("media-7788:high", factory.cacheKeyFor(Uri.parse(urlV1)))
    }

    @Test fun `different qualities for same cacheKey produce distinct cacheKeys`() {
        val urlHigh = "https://example.invalid/a.mp3?sig=abc&q=high"
        val urlLow = "https://example.invalid/a.mp3?sig=abc&q=low"
        registry.put(urlHigh, emptyMap(), null, cacheKey = "kugou:song1", quality = PlayQuality.HIGH)
        registry.put(urlLow, emptyMap(), null, cacheKey = "kugou:song1", quality = PlayQuality.LOW)
        val keyHigh = factory.cacheKeyFor(Uri.parse(urlHigh))
        val keyLow = factory.cacheKeyFor(Uri.parse(urlLow))
        org.junit.Assert.assertEquals("kugou:song1:high", keyHigh)
        org.junit.Assert.assertEquals("kugou:song1:low", keyLow)
        org.junit.Assert.assertNotEquals(keyHigh, keyLow)
    }

    @Test fun `createDataSource works in both cached and uncached paths`() {
        assertNotNull(factory.createDataSource())
        holder.resetForClear()
        assertNotNull(factory.createDataSource())
    }
}
