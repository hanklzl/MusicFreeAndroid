package com.zili.android.musicfreeandroid.plugin.local

import com.zili.android.musicfreeandroid.core.local.Mp3Metadata
import com.zili.android.musicfreeandroid.core.local.Mp3MetadataReader
import com.zili.android.musicfreeandroid.core.model.MusicItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class LocalFilePluginTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test fun `getMediaSource standard returns file URL when localPath set`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        val plugin = LocalFilePlugin(reader)
        val item = localItem(localPath = "/sdcard/song.mp3")

        val result = plugin.getMediaSource(item, "standard")!!
        assertEquals("file:///sdcard/song.mp3", result.url)
    }

    @Test fun `getMediaSource non-standard quality returns null`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        val plugin = LocalFilePlugin(reader)
        val item = localItem(localPath = "/sdcard/song.mp3")
        assertNull(plugin.getMediaSource(item, "super"))
    }

    @Test fun `getMediaSource without localPath falls back to file URL when url is file scheme`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        val plugin = LocalFilePlugin(reader)
        val item = localItem(localPath = null, url = "file:///sdcard/song.mp3")
        val result = plugin.getMediaSource(item, "standard")!!
        assertEquals("file:///sdcard/song.mp3", result.url)
    }

    @Test fun `getMusicInfo reads via Mp3MetadataReader`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        whenever(reader.read("/sdcard/song.mp3")).thenReturn(
            Mp3Metadata("T", "A", "Al", 1000, byteArrayOf(1, 2, 3), null),
        )
        val plugin = LocalFilePlugin(reader)
        val item = localItem(localPath = "/sdcard/song.mp3")
        val result = plugin.getMusicInfo(item)!!
        assertEquals("T", result.title)
        assertEquals("A", result.artist)
        assertEquals("Al", result.album)
        assertEquals(1000L, result.duration)
    }

    @Test fun `importMusicItem reads metadata from path`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        whenever(reader.read("/sdcard/x.mp3")).thenReturn(
            Mp3Metadata("X", "Y", null, 2000, null, null),
        )
        val plugin = LocalFilePlugin(reader)
        val item = plugin.importMusicItem("/sdcard/x.mp3")!!
        assertEquals("X", item.title)
        assertEquals("Y", item.artist)
        assertEquals("本地", item.platform)
        assertEquals("/sdcard/x.mp3", item.localPath)
    }

    @Test fun `getLyric reads adjacent lrc file when present`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        val plugin = LocalFilePlugin(reader)
        val mp3 = tempFolder.newFile("song.mp3")
        File(mp3.parent, "song.lrc").writeText("[00:00.00]hello")
        val item = localItem(localPath = mp3.absolutePath)
        val result = plugin.getLyric(item)!!
        assertEquals("[00:00.00]hello", result.rawLrc)
    }

    @Test fun `getLyric returns null when neither adjacent nor embedded`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        val plugin = LocalFilePlugin(reader)
        val mp3 = tempFolder.newFile("only.mp3")
        whenever(reader.read(mp3.absolutePath)).thenReturn(
            Mp3Metadata(null, null, null, null, null, null),
        )
        val item = localItem(localPath = mp3.absolutePath)
        assertNull(plugin.getLyric(item))
    }

    @Test fun `getLyric falls back to embedded lrc when no adjacent file`() = runTest {
        val reader = mock<Mp3MetadataReader>()
        val plugin = LocalFilePlugin(reader)
        val mp3 = tempFolder.newFile("emb.mp3")
        whenever(reader.read(mp3.absolutePath)).thenReturn(
            Mp3Metadata(null, null, null, null, null, "[00:01]from-embedded"),
        )
        val item = localItem(localPath = mp3.absolutePath)
        val result = plugin.getLyric(item)!!
        assertEquals("[00:01]from-embedded", result.rawLrc)
    }

    private fun localItem(localPath: String? = null, url: String? = null): MusicItem = MusicItem(
        id = "1",
        platform = "本地",
        title = "",
        artist = "",
        album = null,
        duration = 0L,
        url = url,
        artwork = null,
        qualities = null,
        localPath = localPath,
    )
}
