package com.zili.android.musicfreeandroid.player.ext

import com.zili.android.musicfreeandroid.core.model.MusicItem
import org.junit.Assert.*
import org.junit.Test
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
    fun `toMediaItem handles null url gracefully`() {
        val item = MusicItem(
            id = "1", platform = "local", title = "Song",
            artist = "Artist", album = null, duration = 0L,
            url = null, artwork = null, qualities = null,
        )
        val mediaItem = item.toMediaItem()
        assertEquals("local:1", mediaItem.mediaId)
        assertNull(mediaItem.localConfiguration)
        assertNull(mediaItem.mediaMetadata.albumTitle)
        assertNull(mediaItem.mediaMetadata.artworkUri)
    }
}
