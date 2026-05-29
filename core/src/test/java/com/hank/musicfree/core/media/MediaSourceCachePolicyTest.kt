package com.hank.musicfree.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceCachePolicyTest {
    @Test
    fun `parse uses cache no-cache no-store and defaults to no-cache`() {
        assertEquals(MediaSourceCachePolicy.Cache, MediaSourceCachePolicy.parse("cache"))
        assertEquals(MediaSourceCachePolicy.NoStore, MediaSourceCachePolicy.parse("no-store"))
        assertEquals(MediaSourceCachePolicy.NoCache, MediaSourceCachePolicy.parse(null))
        assertEquals(MediaSourceCachePolicy.NoCache, MediaSourceCachePolicy.parse("no-cache"))
        assertEquals(MediaSourceCachePolicy.NoCache, MediaSourceCachePolicy.parse("other"))
    }

    @Test
    fun `can read resolved source by policy and offline flag`() {
        assertTrue(MediaSourceCachePolicy.Cache.canReadResolvedSource(isOffline = true))
        assertTrue(MediaSourceCachePolicy.Cache.canReadResolvedSource(isOffline = false))
        assertTrue(MediaSourceCachePolicy.NoCache.canReadResolvedSource(isOffline = true))
        assertFalse(MediaSourceCachePolicy.NoCache.canReadResolvedSource(isOffline = false))
        assertFalse(MediaSourceCachePolicy.NoStore.canReadResolvedSource(isOffline = true))
        assertFalse(MediaSourceCachePolicy.NoStore.canReadResolvedSource(isOffline = false))
    }

    @Test
    fun `can write resolved source by policy`() {
        assertTrue(MediaSourceCachePolicy.Cache.canWriteResolvedSource())
        assertTrue(MediaSourceCachePolicy.NoCache.canWriteResolvedSource())
        assertFalse(MediaSourceCachePolicy.NoStore.canWriteResolvedSource())
    }

    @Test
    fun `can write byte cache by policy`() {
        assertTrue(MediaSourceCachePolicy.Cache.canWriteByteCache())
        assertTrue(MediaSourceCachePolicy.NoCache.canWriteByteCache())
        assertFalse(MediaSourceCachePolicy.NoStore.canWriteByteCache())
    }
}
