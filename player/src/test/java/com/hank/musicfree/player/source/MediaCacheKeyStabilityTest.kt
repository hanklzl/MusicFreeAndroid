package com.hank.musicfree.player.source

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.player.cache.SimpleCacheHolder
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end stability test for the cacheKey path of [HeaderInjectingDataSourceFactory].
 *
 * Two signature-rotated URLs that resolve to the same business `mediaId` MUST produce
 * the same [DataSpec.key] downstream so [androidx.media3.datasource.cache.CacheDataSource]
 * deduplicates them to a single SimpleCache entry. We test that contract directly against
 * the factory's resolver (not via the ResolvingDataSource indirection), which is the unit
 * that controls cacheKey propagation.
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
        holder = SimpleCacheHolder(ctx)
        factory = HeaderInjectingDataSourceFactory(
            context = ctx,
            okHttpClient = OkHttpClient(),
            registry = registry,
            simpleCacheHolder = holder,
        )
    }

    @After fun teardown() {
        holder.resetForClear()
    }

    @Test fun `signature rotated urls with same cacheKey resolve to identical DataSpec key`() {
        val signedUrlV1 = "https://cdn.example.com/track.mp3?sig=AAA&t=1"
        val signedUrlV2 = "https://cdn.example.com/track.mp3?sig=BBB&t=2"
        val stableKey = "media-7788"

        registry.put(signedUrlV1, mapOf("Referer" to "ref"), "UA-v1", cacheKey = stableKey)
        registry.put(signedUrlV2, mapOf("Referer" to "ref"), "UA-v2", cacheKey = stableKey)

        val spec1 = DataSpec.Builder().setUri(signedUrlV1).build()
        val spec2 = DataSpec.Builder().setUri(signedUrlV2).build()

        val out1 = factory.resolveDataSpec(spec1)
        val out2 = factory.resolveDataSpec(spec2)

        assertEquals(stableKey, out1.key)
        assertEquals(stableKey, out2.key)
        // Both URLs route to the same CacheDataSource entry — that's the whole point.
        assertEquals(out1.key, out2.key)
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
        // even with a matching registry entry, file:// must not pick up headers — it's a local path
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
        assertEquals("media-42", out.key)
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
        assertEquals("k", out.key)
    }

    @Test fun `createDataSource works in both cached and uncached paths`() {
        // Smoke: with SimpleCache available we get a CacheDataSource wrap; without one,
        // we fall back to the resolving DataSource. Both paths must not crash on construction.
        assertNotNull(factory.createDataSource())
        holder.resetForClear()
        // After reset the holder lazily recreates — both calls must succeed.
        assertNotNull(factory.createDataSource())
    }
}
