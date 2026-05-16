package com.hank.musicfree.downloader.engine

import com.hank.musicfree.core.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFilenamesTest {
    private fun item(title: String = "晴天", artist: String = "周杰伦", platform: String = "qq", id: String = "001") =
        MusicItem(id = id, platform = platform, title = title, artist = artist,
            album = null, duration = 0L, url = null, artwork = null, qualities = null)

    @Test fun escapeReplacesIllegalChars() {
        val s = DownloadFilenames.escape("a/b\\c:d*e?f\"g<h>i|j@k")
        assertEquals("a_b_c_d_e_f_g_h_i_j_k", s)
    }

    @Test fun displayNameJoinsFieldsWithAtAndAppendsExtension() {
        val name = DownloadFilenames.displayName(item(), ext = "mp3")
        assertEquals("qq@001@晴天@周杰伦.mp3", name)
    }

    @Test fun displayNameStripsAtFromFieldValuesToProtectSeparator() {
        val name = DownloadFilenames.displayName(item(title = "a@b", artist = "c@d"), ext = "mp3")
        assertEquals("qq@001@a_b@c_d.mp3", name)
    }

    @Test fun displayNameTruncatesBaseTo200Chars() {
        val long = "x".repeat(300)
        val name = DownloadFilenames.displayName(item(title = long), ext = "mp3")
        assertTrue("base length", name.removeSuffix(".mp3").length == 200)
    }

    @Test fun extensionFromUrlMatchesPathTail() {
        assertEquals("flac", DownloadFilenames.extensionFromUrl("https://x.com/song.flac"))
        assertEquals("m4a", DownloadFilenames.extensionFromUrl("https://x.com/song.m4a?token=abc"))
    }

    @Test fun extensionFromUrlFallsBackToMp3WhenUnknownOrMissing() {
        assertEquals("mp3", DownloadFilenames.extensionFromUrl("https://x.com/song"))
        assertEquals("mp3", DownloadFilenames.extensionFromUrl("https://x.com/song.exe"))
    }

    @Test fun mimeForKnownExtensions() {
        assertEquals("audio/mpeg", DownloadFilenames.mimeFor("mp3"))
        assertEquals("audio/flac", DownloadFilenames.mimeFor("flac"))
        assertEquals("audio/mp4", DownloadFilenames.mimeFor("m4a"))
        assertEquals("audio/aac", DownloadFilenames.mimeFor("aac"))
        assertEquals("audio/ogg", DownloadFilenames.mimeFor("ogg"))
        assertEquals("audio/wav", DownloadFilenames.mimeFor("wav"))
        assertEquals("audio/x-ms-wma", DownloadFilenames.mimeFor("wma"))
        assertEquals("audio/x-ape", DownloadFilenames.mimeFor("ape"))
    }
}
