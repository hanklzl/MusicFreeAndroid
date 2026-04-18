package com.zili.android.musicfreeandroid.player.ext

import com.zili.android.musicfreeandroid.core.model.MusicItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.function.ThrowingRunnable
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicItemMediaExtTest {

    @Test
    fun `toMediaItem sets mediaId as platform colon id`() {
        val item = MusicItem(
            id = "abc", platform = "netease", title = "Song",
            artist = "Artist", album = "Album", duration = 200_000L,
            url = "https://example.com/song.mp3", artwork = "https://example.com/art.jpg",
            qualities = null,
        )
        val mediaItem = item.toMediaItem()
        assertEquals("netease:abc", mediaItem.mediaId)
        assertEquals("https://example.com/song.mp3", mediaItem.localConfiguration?.uri?.toString())
        assertEquals("Song", mediaItem.mediaMetadata.title?.toString())
        assertEquals("Artist", mediaItem.mediaMetadata.artist?.toString())
        assertEquals("Album", mediaItem.mediaMetadata.albumTitle?.toString())
        assertEquals("https://example.com/art.jpg", mediaItem.mediaMetadata.artworkUri?.toString())
    }

    @Test
    fun `toMediaItem throws IllegalArgumentException when url is null`() {
        val item = MusicItem(
            id = "1", platform = "local", title = "Song",
            artist = "Artist", album = null, duration = 0L,
            url = null, artwork = null, qualities = null,
        )
        assertThrows(
            "Cannot create MediaItem without URL for: Song (local:1)",
            IllegalArgumentException::class.java,
            ThrowingRunnable { item.toMediaItem() },
        )
    }

    @Test
    fun `toMediaItem throws IllegalArgumentException when url is blank`() {
        val item = MusicItem(
            id = "2", platform = "local", title = "Song2",
            artist = "Artist", album = null, duration = 0L,
            url = "", artwork = null, qualities = null,
        )
        assertThrows(
            IllegalArgumentException::class.java,
            ThrowingRunnable { item.toMediaItem() },
        )
    }
}
