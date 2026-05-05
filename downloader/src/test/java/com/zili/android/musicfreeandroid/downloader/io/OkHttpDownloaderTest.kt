package com.zili.android.musicfreeandroid.downloader.io

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OkHttpDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var subject: OkHttpDownloader
    private lateinit var workDir: File

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        subject = OkHttpDownloader(OkHttpClient())
        workDir = Files.createTempDirectory("dl").toFile()
    }

    @After fun teardown() {
        server.shutdown()
        workDir.deleteRecursively()
    }

    @Test fun downloadsBodyToTargetFileAndEmitsProgress() = runTest {
        val payload = Buffer().apply { writeUtf8("hello-world".repeat(2048)) }
        val expected = payload.readByteArray()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Length", expected.size.toString())
                .setBody(Buffer().apply { write(expected) }),
        )
        val target = File(workDir, "out.mp3")
        val emissions = mutableListOf<HttpDownloadProgress>()
        subject.download(
            url = server.url("/song.mp3").toString(),
            headers = emptyMap(),
            target = target,
            onProgress = { emissions += it },
        )
        assertTrue(target.exists())
        assertEquals(expected.size.toLong(), target.length())
        assertEquals(expected.size.toLong(), emissions.last().downloaded)
        assertEquals(expected.size.toLong(), emissions.last().total)
    }

    @Test(expected = HttpDownloadException::class)
    fun http500BubblesAsHttpDownloadException() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        subject.download(
            url = server.url("/x").toString(),
            headers = emptyMap(),
            target = File(workDir, "x.mp3"),
            onProgress = {},
        )
    }
}
