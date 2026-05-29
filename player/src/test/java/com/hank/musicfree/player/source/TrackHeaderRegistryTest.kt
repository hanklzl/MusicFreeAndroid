package com.hank.musicfree.player.source

import com.hank.musicfree.core.media.MediaSourceCachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackHeaderRegistryTest {

    @Test
    fun `miss returns null`() {
        assertNull(TrackHeaderRegistry().get("absent"))
    }

    @Test
    fun `put stores and get returns matching entry`() {
        val r = TrackHeaderRegistry()
        r.put("http://a", mapOf("Referer" to "x"), "ua-1")
        val e = r.get("http://a")!!
        assertEquals("x", e.headers["Referer"])
        assertEquals("ua-1", e.userAgent)
    }

    @Test
    fun `LRU evicts oldest when MAX exceeded`() {
        val r = TrackHeaderRegistry()
        val overflow = 5
        repeat(TrackHeaderRegistry.MAX + overflow) { i ->
            r.put("k$i", mapOf("X" to "$i"), null)
        }
        // first 5 should be evicted
        repeat(overflow) { i -> assertNull(r.get("k$i")) }
        // last MAX still present
        repeat(TrackHeaderRegistry.MAX) { i ->
            val key = "k${i + overflow}"
            assertEquals("${i + overflow}", r.get(key)?.headers?.get("X"))
        }
    }

    @Test
    fun `accessing entry refreshes LRU position`() {
        val r = TrackHeaderRegistry()
        repeat(TrackHeaderRegistry.MAX) { i -> r.put("k$i", emptyMap(), null) }
        r.get("k0") // touch oldest, now MRU
        r.put("k-new", emptyMap(), null) // should evict k1, NOT k0
        assertNull(r.get("k1"))
        assertEquals(emptyMap<String, String>(), r.get("k0")?.headers)
    }

    @Test
    fun `put with cacheKey returns in entry`() {
        val r = TrackHeaderRegistry()
        r.put("https://x/a", mapOf("k" to "v"), "UA", cacheKey = "media-123")
        assertEquals("media-123", r.get("https://x/a")?.cacheKey)
    }

    @Test
    fun `put defaults include byte-cache allowed and no-cache policy`() {
        val r = TrackHeaderRegistry()
        r.put("https://x/b", emptyMap(), "UA")
        val entry = r.get("https://x/b")!!
        assertEquals(true, entry.byteCacheAllowed)
        assertEquals(MediaSourceCachePolicy.NoCache, entry.cachePolicy)
    }

    @Test
    fun `MAX is 64 entries`() {
        assertEquals(64, TrackHeaderRegistry.MAX)
    }

    @Test
    fun `put without cacheKey default null`() {
        val r = TrackHeaderRegistry()
        r.put("https://x/b", emptyMap(), null)
        assertNull(r.get("https://x/b")?.cacheKey)
    }

    @Test
    fun `put stores byteCacheAllowed false when configured`() {
        val r = TrackHeaderRegistry()
        r.put("https://x/c", emptyMap(), null, byteCacheAllowed = false, cachePolicy = MediaSourceCachePolicy.NoStore)
        val entry = r.get("https://x/c")!!
        assertEquals(false, entry.byteCacheAllowed)
        assertEquals(MediaSourceCachePolicy.NoStore, entry.cachePolicy)
    }
}
