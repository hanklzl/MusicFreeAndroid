package com.hank.musicfree.downloader.io

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
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
    private lateinit var logger: CapturingLogger

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        subject = OkHttpDownloader(OkHttpClient())
        workDir = Files.createTempDirectory("dl").toFile()
        logger = CapturingLogger()
        MfLog.install(logger)
    }

    @After fun teardown() {
        MfLog.resetForTest()
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
        val success = logger.events.single { it.event == "download_http_success" }
        assertEquals(LogCategory.DOWNLOAD, success.category)
        assertEquals(200, success.fields["statusCode"])
        assertEquals(expected.size.toLong(), success.fields["bytes"])
        assertEquals(LogFields.Result.SUCCESS, success.fields["result"])
    }

    @Test
    fun http500BubblesAsHttpDownloadException() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            subject.download(
                url = server.url("/x").toString(),
                headers = emptyMap(),
                target = File(workDir, "x.mp3"),
                onProgress = {},
            )
            throw AssertionError("Expected HttpDownloadException")
        } catch (_: HttpDownloadException) {
            val failure = logger.events.single { it.event == "download_http_failed" }
            assertEquals(LogCategory.DOWNLOAD, failure.category)
            assertEquals(500, failure.fields["statusCode"])
            assertEquals(LogFields.Result.FAILURE, failure.fields["result"])
        }
    }
}

private data class CapturedLogEvent(
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
)

private class CapturingLogger : MfLogger {
    val events = mutableListOf<CapturedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CapturedLogEvent(category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CapturedLogEvent(category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += CapturedLogEvent(category, event, fields + ("throwable" to throwable))
    }

    override fun flush() = Unit
}
