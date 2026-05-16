package com.zili.android.musicfreeandroid.updater.api

import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpUpdateClientTest {

    private lateinit var primary: MockWebServer
    private lateinit var fallback: MockWebServer
    private lateinit var http: OkHttpClient

    @Before
    fun setUp() {
        primary = MockWebServer().apply { start() }
        fallback = MockWebServer().apply { start() }
        http = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        primary.shutdown()
        fallback.shutdown()
    }

    private fun canonicalJson(): String = """
        {
          "schemaVersion": 2,
          "version": "1.2.3",
          "versionCode": 10203,
          "releasedAt": "2026-05-13T18:00:00Z",
          "releaseNotesUrl": "https://example.com/notes",
          "changeLog": [],
          "variants": {
            "arm64-v8a": {
              "download": ["https://example.com/arm64.apk"],
              "size": 1,
              "sha256": "x"
            }
          }
        }
    """.trimIndent()

    @Test
    fun `returns info when primary succeeds`() = runTest {
        primary.enqueue(MockResponse().setBody(canonicalJson()))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        val info: UpdateInfo? = client.fetchLatest()
        assertEquals("1.2.3", info?.version)
        assertEquals(1, primary.requestCount)
        assertEquals(0, fallback.requestCount)
    }

    @Test
    fun `falls back when primary returns 500`() = runTest {
        primary.enqueue(MockResponse().setResponseCode(500))
        fallback.enqueue(MockResponse().setBody(canonicalJson()))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        val info: UpdateInfo? = client.fetchLatest()
        assertEquals("1.2.3", info?.version)
        assertEquals(1, primary.requestCount)
        assertEquals(1, fallback.requestCount)
    }

    @Test
    fun `returns null when all mirrors fail`() = runTest {
        primary.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        fallback.enqueue(MockResponse().setResponseCode(503))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        assertNull(client.fetchLatest())
    }

    @Test
    fun `returns null when body is unparseable`() = runTest {
        primary.enqueue(MockResponse().setBody("garbage{"))
        fallback.enqueue(MockResponse().setBody("also bad"))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        assertNull(client.fetchLatest())
    }
}
