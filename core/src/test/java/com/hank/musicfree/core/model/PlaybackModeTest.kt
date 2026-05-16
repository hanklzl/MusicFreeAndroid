package com.hank.musicfree.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModeTest {

    @Test
    fun `from maps shuffle enabled to shuffle mode`() {
        assertEquals(
            PlaybackMode.Shuffle,
            PlaybackMode.from(shuffleEnabled = true, repeatMode = RepeatMode.ALL),
        )
        assertEquals(
            PlaybackMode.Shuffle,
            PlaybackMode.from(shuffleEnabled = true, repeatMode = RepeatMode.ONE),
        )
        assertEquals(
            PlaybackMode.Shuffle,
            PlaybackMode.from(shuffleEnabled = true, repeatMode = RepeatMode.OFF),
        )
    }

    @Test
    fun `from maps repeat one to single mode when shuffle is disabled`() {
        assertEquals(
            PlaybackMode.Single,
            PlaybackMode.from(shuffleEnabled = false, repeatMode = RepeatMode.ONE),
        )
    }

    @Test
    fun `from maps repeat all and off to queue mode when shuffle is disabled`() {
        assertEquals(
            PlaybackMode.Queue,
            PlaybackMode.from(shuffleEnabled = false, repeatMode = RepeatMode.ALL),
        )
        assertEquals(
            PlaybackMode.Queue,
            PlaybackMode.from(shuffleEnabled = false, repeatMode = RepeatMode.OFF),
        )
    }

    @Test
    fun `next follows RN repeat mode order`() {
        assertEquals(PlaybackMode.Single, PlaybackMode.Shuffle.next())
        assertEquals(PlaybackMode.Queue, PlaybackMode.Single.next())
        assertEquals(PlaybackMode.Shuffle, PlaybackMode.Queue.next())
    }
}
