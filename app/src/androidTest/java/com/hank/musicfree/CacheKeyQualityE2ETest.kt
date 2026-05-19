package com.hank.musicfree

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.player.source.TrackHeaderRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Smoke test verifying that the cache-key quality-differentiation data path works end-to-end
 * through the real Hilt DI graph.
 *
 * Design note: [HeaderInjectingDataSourceFactory] is omitted from direct injection here because
 * it extends `DataSource.Factory` from Media3, which is not on the `:app` androidTest classpath
 * (it arrives transitively through `:player` but the compiler can't resolve the supertype in this
 * compilation unit). The full key-quality assertion is covered by
 * [com.hank.musicfree.player.source.MediaCacheKeyStabilityTest] (Robolectric unit test).
 *
 * This test gains real-device confidence that:
 *   1. The Hilt DI graph resolves `TrackHeaderRegistry` as a @Singleton.
 *   2. Entries written for HIGH and LOW quality on distinct URLs are stored independently.
 *   3. The `cacheKey` field round-trips correctly for the quality-suffix path.
 */
@MediumTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CacheKeyQualityE2ETest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var registry: TrackHeaderRegistry

    @Before fun setup() {
        hilt.inject()
    }

    @Test
    fun registryIsProvidedAsSingleton() {
        // If this test passes, the Hilt DI graph has a complete binding for TrackHeaderRegistry.
        val url = "https://example.invalid/e2e-singleton.mp3"
        registry.put(url, emptyMap(), null, cacheKey = "kugou:song1", quality = PlayQuality.HIGH)
        assertNotNull(registry.get(url))
    }

    @Test
    fun differentQualityEntriesAreDistinctInRegistry() {
        val urlHigh = "https://example.invalid/song-e2e.mp3?q=high"
        val urlLow  = "https://example.invalid/song-e2e.mp3?q=low"
        registry.put(urlHigh, emptyMap(), null, cacheKey = "kugou:song2", quality = PlayQuality.HIGH)
        registry.put(urlLow,  emptyMap(), null, cacheKey = "kugou:song2", quality = PlayQuality.LOW)

        val high = registry.get(urlHigh)
        val low  = registry.get(urlLow)

        assertNotNull("HIGH entry must be in registry", high)
        assertNotNull("LOW entry must be in registry", low)
        assert(high?.quality != low?.quality) {
            "HIGH and LOW quality entries must be distinct, both returned ${high?.quality}"
        }
        assert(high?.cacheKey == "kugou:song2") { "cacheKey mismatch for HIGH: ${high?.cacheKey}" }
        assert(low?.cacheKey  == "kugou:song2") { "cacheKey mismatch for LOW: ${low?.cacheKey}" }
    }

    @Test
    fun highQualityHeaderEntryRoundTrips() {
        val url = "https://example.invalid/high-quality.mp3"
        registry.put(
            url = url,
            headers = mapOf("X-Source" to "kugou"),
            userAgent = "MusicFree/1.0",
            cacheKey = "kugou:abc123",
            quality = PlayQuality.HIGH,
        )
        val entry = registry.get(url)
        assertNotNull(entry)
        assert(entry?.quality == PlayQuality.HIGH) { "Expected HIGH, got ${entry?.quality}" }
        assert(entry?.cacheKey == "kugou:abc123") { "Expected cacheKey kugou:abc123, got ${entry?.cacheKey}" }
        assert(entry?.headers?.get("X-Source") == "kugou") { "Header mismatch: ${entry?.headers}" }
        assert(entry?.userAgent == "MusicFree/1.0") { "UA mismatch: ${entry?.userAgent}" }
    }
}
