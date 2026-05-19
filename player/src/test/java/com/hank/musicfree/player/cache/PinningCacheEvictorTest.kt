package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@AndroidXOptIn(markerClass = [UnstableApi::class])
class PinningCacheEvictorTest {
    @Test fun `pinned key is skipped when within budget`() {
        val evictor = PinningCacheEvictor(maxBytes = 100L)
        evictor.updatePinned(setOf("kugou:song1:standard"))
        evictor.notePinnedSize(50L)
        assertTrue(evictor.shouldSkip("kugou:song1:standard"))
        assertFalse(evictor.shouldSkip("kugou:other:standard"))
    }

    @Test fun `pinning falls back to LRU above 70 percent of budget`() {
        val evictor = PinningCacheEvictor(maxBytes = 100L)
        evictor.updatePinned(setOf("a:1:s", "a:2:s", "a:3:s"))
        evictor.notePinnedSize(80L) // > 70%
        assertFalse(evictor.shouldSkip("a:1:s"))
    }

    @Test fun `pinning exactly at 70 percent still protects pinned keys`() {
        val evictor = PinningCacheEvictor(maxBytes = 100L)
        evictor.updatePinned(setOf("a:1:s"))
        evictor.notePinnedSize(70L) // 70% exactly — NOT > 70%
        assertTrue(evictor.shouldSkip("a:1:s"))
    }
}
