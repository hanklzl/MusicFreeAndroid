package com.hank.musicfree.player.model

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RepeatMode
import org.junit.Assert.*
import org.junit.Test

class PlayerStateTest {

    @Test
    fun `EMPTY state has sensible defaults`() {
        val state = PlayerState.EMPTY
        assertNull(state.currentItem)
        assertFalse(state.isPlaying)
        assertEquals(PlaybackState.IDLE, state.playbackState)
        assertEquals(0L, state.duration)
        assertEquals(0L, state.position)
        assertEquals(RepeatMode.OFF, state.repeatMode)
        assertFalse(state.shuffleEnabled)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val item = MusicItem(
            id = "1", platform = "local", title = "Song",
            artist = "Artist", album = null, duration = 180_000L,
            url = "file:///music/song.mp3", artwork = null, qualities = null,
        )
        val state = PlayerState.EMPTY.copy(currentItem = item, isPlaying = true)
        assertEquals(item, state.currentItem)
        assertTrue(state.isPlaying)
        assertEquals(PlaybackState.IDLE, state.playbackState)
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }

    @Test
    fun `hasMedia returns true when currentItem is set`() {
        assertFalse(PlayerState.EMPTY.hasMedia)
        val withItem = PlayerState.EMPTY.copy(
            currentItem = MusicItem(
                id = "1", platform = "local", title = "Song",
                artist = "Artist", album = null, duration = 0L,
                url = null, artwork = null, qualities = null,
            )
        )
        assertTrue(withItem.hasMedia)
    }
}
