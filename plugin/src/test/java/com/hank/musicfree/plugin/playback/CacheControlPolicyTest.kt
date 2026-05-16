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
    fun `shouldWriteCache returns true except for NoStore`() {
        assertTrue(shouldWriteCache(CacheControl.Cache))
        assertTrue(shouldWriteCache(CacheControl.NoCache))
        assertFalse(shouldWriteCache(CacheControl.NoStore))
    }
}
