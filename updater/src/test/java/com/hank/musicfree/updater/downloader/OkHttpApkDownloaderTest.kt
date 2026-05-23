package com.hank.musicfree.updater.downloader

import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.updater.checker.ResolvedUpdate
import com.hank.musicfree.updater.checker.UpdateError
import com.hank.musicfree.updater.model.ApkVariant
import com.hank.musicfree.updater.model.UpdateInfo
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
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    private fun makeResolved(url: String, body: ByteArray, sha: String, abi: String = "arm64-v8a"): ResolvedUpdate {
        val variant = ApkVariant(
            download = listOf(url),
            size = body.size.toLong(),
            sha256 = sha,
        )
        val info = UpdateInfo(
            schemaVersion = 2,
            version = "1.2.3",
            versionCode = 10203,
            releasedAt = "2026-05-16T18:00:00Z",
            releaseNotesUrl = "https://example.com/notes",
            changeLog = emptyList(),
            variants = mapOf(abi to variant),
        )
        return ResolvedUpdate(info = info, abi = abi, variant = variant)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `successful download writes abi-scoped file and reports progress`() = runTest {
        val body = ByteArray(4096) { 0x42 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha256Hex(body))

        val progresses = mutableListOf<Float>()
        val result = downloader.download(resolved) { _, _, fraction -> progresses.add(fraction) }

        assertTrue(result is ApkDownloader.Result.Success)
        val file = (result as ApkDownloader.Result.Success).apkFile
        assertTrue(file.exists())
        assertEquals(body.size.toLong(), file.length())
        assertTrue(progresses.last() in 0.99f..1.0f)
        assertFalse(File(file.parentFile, "${file.name}.part").exists())
        assertEquals("musicfree-10203-arm64-v8a.apk", file.name)
    }

    @Test
    fun `valid cached apk is reused without network`() = runTest {
        val body = ByteArray(4096) { 0x24 }
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha256Hex(body))
        val finalFile = File(cacheDir, "musicfree-${resolved.info.versionCode}-${resolved.abi}.apk")
            .apply { writeBytes(body) }

        val progresses = mutableListOf<Float>()
        val result = downloader.download(resolved) { _, _, fraction -> progresses.add(fraction) }

        assertTrue(result is ApkDownloader.Result.Success)
        val success = result as ApkDownloader.Result.Success
        assertEquals(finalFile, success.apkFile)
        assertTrue(success.fromCache)
        assertEquals(0, server.requestCount)
        assertTrue(progresses.last() in 0.99f..1.0f)
    }

    @Test
    fun `partial apk is resumed with range request`() = runTest {
        val prefix = ByteArray(1024) { 0x35 }
        val suffix = ByteArray(2048) { 0x36 }
        val body = prefix + suffix
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes ${prefix.size}-${body.lastIndex}/${body.size}")
                .setBody(Buffer().apply { write(suffix) }),
        )
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha256Hex(body))
        val partFile = File(cacheDir, "musicfree-${resolved.info.versionCode}-${resolved.abi}.apk.part")
            .apply { writeBytes(prefix) }

        val result = downloader.download(resolved) { _, _, _ -> }

        assertTrue(result is ApkDownloader.Result.Success)
        val success = result as ApkDownloader.Result.Success
        assertEquals(prefix.size.toLong(), success.resumedFromBytes)
        assertFalse(partFile.exists())
        assertEquals("bytes=${prefix.size}-", server.takeRequest().getHeader("Range"))
        assertEquals(body.toList(), success.apkFile.readBytes().toList())
    }

    @Test
    fun `server ignoring range restarts partial download safely`() = runTest {
        val stalePrefix = ByteArray(512) { 0x10 }
        val body = ByteArray(2048) { 0x44 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha256Hex(body))
        File(cacheDir, "musicfree-${resolved.info.versionCode}-${resolved.abi}.apk.part")
            .apply { writeBytes(stalePrefix) }

        val result = downloader.download(resolved) { _, _, _ -> }

        assertTrue(result is ApkDownloader.Result.Success)
        val success = result as ApkDownloader.Result.Success
        assertEquals(0L, success.resumedFromBytes)
        assertEquals("bytes=${stalePrefix.size}-", server.takeRequest().getHeader("Range"))
        assertEquals(body.toList(), success.apkFile.readBytes().toList())
    }

    @Test
    fun `sha256 mismatch deletes file and returns mismatch`() = runTest {
        val body = ByteArray(2048) { 0x21 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha = "deadbeef")

        val result = downloader.download(resolved) { _, _, _ -> }

        assertTrue(result is ApkDownloader.Result.Failure)
        assertEquals(UpdateError.Sha256Mismatch, (result as ApkDownloader.Result.Failure).cause)
        val abi = resolved.abi
        assertFalse(File(cacheDir, "musicfree-${resolved.info.versionCode}-${abi}.apk.part").exists())
        assertFalse(File(cacheDir, "musicfree-${resolved.info.versionCode}-${abi}.apk").exists())
    }

    @Test
    fun `content length mismatch returns size mismatch`() = runTest {
        val body = ByteArray(1024) { 0x33 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha256Hex(body))
        // 重新构造一个 variant.size = 9999 的 ResolvedUpdate
        val tampered = resolved.copy(variant = resolved.variant.copy(size = 9999L))

        val result = downloader.download(tampered) { _, _, _ -> }

        assertEquals(UpdateError.SizeMismatch, (result as ApkDownloader.Result.Failure).cause)
    }

    @Test
    fun `falls back to next mirror when first returns 500`() = runTest {
        val body = ByteArray(512) { 0x11 }
        val sha = sha256Hex(body)
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))

        val variant = ApkVariant(
            download = listOf(
                server.url("/dead.apk").toString(),
                server.url("/live.apk").toString(),
            ),
            size = body.size.toLong(),
            sha256 = sha,
        )
        val info = UpdateInfo(
            schemaVersion = 2,
            version = "1.2.3",
            versionCode = 10203,
            releasedAt = "2026-05-16T18:00:00Z",
            releaseNotesUrl = "https://example.com/notes",
            changeLog = emptyList(),
            variants = mapOf("x86_64" to variant),
        )
        val resolved = ResolvedUpdate(info = info, abi = "x86_64", variant = variant)

        val result = downloader.download(resolved) { _, _, _ -> }
        assertTrue(result is ApkDownloader.Result.Success)
        assertEquals("musicfree-10203-x86_64.apk", (result as ApkDownloader.Result.Success).apkFile.name)
    }
}
