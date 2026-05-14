package com.zili.android.musicfreeandroid.updater.downloader

import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OkHttpApkDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var cacheDir: File
    private lateinit var downloader: OkHttpApkDownloader

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        cacheDir = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "updates",
        ).apply { mkdirs() }
        downloader = OkHttpApkDownloader(http = http, cacheRoot = { cacheDir })
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun makeInfo(url: String, body: ByteArray, sha: String): UpdateInfo = UpdateInfo(
        schemaVersion = 1,
        version = "1.2.3",
        versionCode = 10203,
        releasedAt = "2026-05-13T18:00:00Z",
        download = listOf(url),
        size = body.size.toLong(),
        sha256 = sha,
        changeLog = emptyList(),
        releaseNotesUrl = "https://example.com/notes",
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `successful download writes file and reports progress`() = runTest {
        val body = ByteArray(4096) { 0x42 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = makeInfo(server.url("/app.apk").toString(), body, sha256Hex(body))

        val progresses = mutableListOf<Float>()
        val result = downloader.download(info) { _, _, fraction -> progresses.add(fraction) }

        assertTrue(result is ApkDownloader.Result.Success)
        val file = (result as ApkDownloader.Result.Success).apkFile
        assertTrue(file.exists())
        assertEquals(body.size.toLong(), file.length())
        assertTrue(progresses.last() in 0.99f..1.0f)
        assertFalse(File(file.parentFile, "${file.name}.part").exists())
    }

    @Test
    fun `sha256 mismatch deletes file and returns mismatch`() = runTest {
        val body = ByteArray(2048) { 0x21 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = makeInfo(server.url("/app.apk").toString(), body, sha = "deadbeef")

        val result = downloader.download(info) { _, _, _ -> }

        assertTrue(result is ApkDownloader.Result.Failure)
        assertEquals(UpdateError.Sha256Mismatch, (result as ApkDownloader.Result.Failure).cause)
        assertFalse(File(cacheDir, "musicfree-${info.versionCode}.apk.part").exists())
        assertFalse(File(cacheDir, "musicfree-${info.versionCode}.apk").exists())
    }

    @Test
    fun `content length mismatch returns size mismatch`() = runTest {
        val body = ByteArray(1024) { 0x33 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = makeInfo(server.url("/app.apk").toString(), body, sha256Hex(body))
            .copy(size = 9999L)

        val result = downloader.download(info) { _, _, _ -> }

        assertEquals(UpdateError.SizeMismatch, (result as ApkDownloader.Result.Failure).cause)
    }

    @Test
    fun `falls back to next mirror when first returns 500`() = runTest {
        val body = ByteArray(512) { 0x11 }
        val sha = sha256Hex(body)
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = UpdateInfo(
            schemaVersion = 1, version = "1.2.3", versionCode = 10203,
            releasedAt = "2026-05-13T18:00:00Z",
            download = listOf(
                server.url("/dead.apk").toString(),
                server.url("/live.apk").toString(),
            ),
            size = body.size.toLong(), sha256 = sha,
            changeLog = emptyList(), releaseNotesUrl = "https://example.com/notes",
        )

        val result = downloader.download(info) { _, _, _ -> }
        assertTrue(result is ApkDownloader.Result.Success)
    }
}
