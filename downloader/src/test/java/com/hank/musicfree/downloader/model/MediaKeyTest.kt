package com.hank.musicfree.downloader.model

import com.hank.musicfree.core.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKeyTest {
    private fun item(id: String, platform: String) = MusicItem(
        id = id, platform = platform, title = "t", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun fromMusicItemFormatsAsIdAtPlatform() {
        val k = MediaKey.of(item(id = "abc", platform = "qq"))
        assertEquals("abc@qq", k.value)
    }

    @Test fun ofIdAndPlatformMatchesItemFactory() {
        assertEquals(MediaKey.of("x", "wy"), MediaKey.of(item("x", "wy")))
    }

    @Test fun decomposeRoundTrips() {
        val k = MediaKey.of("song-1", "kuwo")
        assertEquals("song-1" to "kuwo", k.id to k.platform)
    }
}
