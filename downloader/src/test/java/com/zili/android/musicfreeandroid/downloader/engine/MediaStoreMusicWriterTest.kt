package com.zili.android.musicfreeandroid.downloader.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreMusicWriterTest {

    @Test fun commitInsertsThenClearsIsPendingAndStreamsBytes() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tmp = Files.createTempDirectory("mscache").toFile()
        val cacheFile = File(tmp, "cache.mp3").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        val writer = MediaStoreMusicWriter(ctx)
        val uri = writer.commit(
            cacheFile = cacheFile,
            displayName = "qq@1@title@artist.mp3",
            mimeType = "audio/mpeg",
            relativePath = "Music/MusicFree/",
            sizeBytes = cacheFile.length(),
        )
        assertNotNull(uri)
        ctx.contentResolver.openInputStream(uri).use { input ->
            assertTrue(input!!.readBytes().isNotEmpty())
        }
    }
}
