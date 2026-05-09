package com.zili.android.musicfreeandroid.player.source

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
        repeat(TrackHeaderRegistry.MAX + 5) { i ->
            r.put("k$i", mapOf("X" to "$i"), null)
        }
        // first 5 should be evicted
        repeat(5) { i -> assertNull(r.get("k$i")) }
        // last MAX still present
        repeat(TrackHeaderRegistry.MAX) { i ->
            val key = "k${i + 5}"
            assertEquals("${i + 5}", r.get(key)?.headers?.get("X"))
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
}
