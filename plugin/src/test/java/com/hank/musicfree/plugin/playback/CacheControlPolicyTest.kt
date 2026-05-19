package com.hank.musicfree.plugin.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheControlPolicyTest {

    @Test
    fun `parse returns matching enum and is case-insensitive`() {
        assertEquals(CacheControl.Cache, CacheControl.parse("cache"))
        assertEquals(CacheControl.Cache, CacheControl.parse("CACHE"))
        assertEquals(CacheControl.NoStore, CacheControl.parse("no-store"))
        assertEquals(CacheControl.NoCache, CacheControl.parse("no-cache"))
    }

    @Test
    fun `parse falls back to NoCache for null and unknown values`() {
        assertEquals(CacheControl.NoCache, CacheControl.parse(null))
        assertEquals(CacheControl.NoCache, CacheControl.parse(""))
        assertEquals(CacheControl.NoCache, CacheControl.parse("garbage"))
        assertEquals(CacheControl.NoCache, CacheControl.parse("foo-bar"))
    }

    @Test
    fun `shouldUseCache truth table`() {
        // cache: always reads
        assertTrue(shouldUseCache(CacheControl.Cache, isOffline = false))
        assertTrue(shouldUseCache(CacheControl.Cache, isOffline = true))
        // no-cache: only offline
        assertFalse(shouldUseCache(CacheControl.NoCache, isOffline = false))
        assertTrue(shouldUseCache(CacheControl.NoCache, isOffline = true))
        // no-store: never
        assertFalse(shouldUseCache(CacheControl.NoStore, isOffline = false))
        assertFalse(shouldUseCache(CacheControl.NoStore, isOffline = true))
    }

    @Test
    fun `shouldWriteCache(cc) deprecated overload returns true except for NoStore`() {
        @Suppress("DEPRECATION")
        assertTrue(shouldWriteCache(CacheControl.Cache))
        @Suppress("DEPRECATION")
        assertTrue(shouldWriteCache(CacheControl.NoCache))
        @Suppress("DEPRECATION")
        assertFalse(shouldWriteCache(CacheControl.NoStore))
    }

    @Test
    fun `shouldWriteCache Cache always writes regardless of connectivity`() {
        assertTrue(shouldWriteCache(CacheControl.Cache, isOffline = false))
        assertTrue(shouldWriteCache(CacheControl.Cache, isOffline = true))
    }

    @Test
    fun `shouldWriteCache NoStore never writes regardless of connectivity`() {
        assertFalse(shouldWriteCache(CacheControl.NoStore, isOffline = false))
        assertFalse(shouldWriteCache(CacheControl.NoStore, isOffline = true))
    }

    @Test
    fun `shouldWriteCache NoCache writes only when offline`() {
        assertFalse(shouldWriteCache(CacheControl.NoCache, isOffline = false))
        assertTrue(shouldWriteCache(CacheControl.NoCache, isOffline = true))
    }

    @Test
    fun `shouldUseCache unchanged contract`() {
        // Cache: always reads
        assertTrue(shouldUseCache(CacheControl.Cache, isOffline = false))
        assertTrue(shouldUseCache(CacheControl.Cache, isOffline = true))
        // NoCache: only offline
        assertFalse(shouldUseCache(CacheControl.NoCache, isOffline = false))
        assertTrue(shouldUseCache(CacheControl.NoCache, isOffline = true))
        // NoStore: never reads
        assertFalse(shouldUseCache(CacheControl.NoStore, isOffline = false))
        assertFalse(shouldUseCache(CacheControl.NoStore, isOffline = true))
    }
}
