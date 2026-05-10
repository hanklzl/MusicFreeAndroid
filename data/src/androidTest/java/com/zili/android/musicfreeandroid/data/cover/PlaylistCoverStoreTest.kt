package com.zili.android.musicfreeandroid.data.cover

import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlaylistCoverStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = PlaylistCoverStore(context)
    private val baseDir = File(context.filesDir, PlaylistCoverStore.BASE_DIR_NAME)

    @After fun cleanup() { baseDir.deleteRecursively() }

    @Test fun saveFromUri_writesFile_andReturnsFileUri() = runBlocking {
        val src = createTempImage("origin.jpg")
        val out = store.saveFromUri(playlistId = "plistA", src = src.toUri())
        assertNotNull(out)
        val expected = "file://" + File(baseDir, "plistA.jpg").absolutePath
        assertEquals(expected, out)
        assertTrue(File(baseDir, "plistA.jpg").exists())
    }

    @Test fun delete_removesFile() = runBlocking {
        val src = createTempImage("origin.jpg")
        store.saveFromUri("plistA", src.toUri())
        store.delete("plistA")
        assertTrue(!File(baseDir, "plistA.jpg").exists())
    }

    @Test fun copyFromArtwork_passesThroughHttpsUrl() = runBlocking {
        val url = "https://example.com/cover.jpg"
        val out = store.copyFromArtwork("plistA", url)
        assertEquals(url, out)
        assertTrue("no file should be written", !File(baseDir, "plistA.jpg").exists())
    }

    @Test fun copyFromArtwork_passesThroughHttpUrl() = runBlocking {
        val url = "http://example.com/cover.jpg"
        val out = store.copyFromArtwork("plistA", url)
        assertEquals(url, out)
    }

    @Test fun copyFromArtwork_savesFileUriToDisk() = runBlocking {
        val src = createTempImage("art.jpg")
        val out = store.copyFromArtwork("plistA", "file://${src.absolutePath}")
        val expected = "file://" + File(baseDir, "plistA.jpg").absolutePath
        assertEquals(expected, out)
        assertTrue(File(baseDir, "plistA.jpg").exists())
    }

    @Test fun copyFromArtwork_returnsNullForBlankOrUnknownScheme() = runBlocking {
        assertNull(store.copyFromArtwork("plistA", null))
        assertNull(store.copyFromArtwork("plistA", ""))
        assertNull(store.copyFromArtwork("plistA", "asset://x.jpg"))
    }

    private fun createTempImage(name: String): File =
        File(context.cacheDir, name).apply { writeBytes(ByteArray(64) { 1 }) }
}
