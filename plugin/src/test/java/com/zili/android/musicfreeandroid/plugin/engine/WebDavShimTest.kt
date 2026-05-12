package com.zili.android.musicfreeandroid.plugin.engine

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [WebDavShim] — minimal `require('webdav')` backend.
 *
 * Asserts the public surface that the JS shim depends on:
 *   1. URL joining (base + path)
 *   2. Basic Auth header derived from [WebDavAuth]
 *   3. PUT request body propagated as-is
 *   4. Non-2xx responses bubble as [java.io.IOException]
 *
 * Design source: plugin-engine-alignment design §8.4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WebDavShimTest {

    private lateinit var server: MockWebServer
    private lateinit var shim: WebDavShim

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Test client with short timeouts so the suite doesn't hang on regressions.
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()
        shim = WebDavShim(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `get sends Basic auth and joins path correctly`() = runTest {
        server.enqueue(MockResponse().setBody("hello"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val body = shim.get(baseUrl, "/backup/state.json", WebDavAuth("u", "p"))

        assertEquals("hello", body)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/backup/state.json", request.path)
        val expected = "Basic " + Base64.getEncoder().encodeToString("u:p".toByteArray())
        assertEquals(expected, request.getHeader("Authorization"))
    }

    @Test
    fun `get without auth omits Authorization header`() = runTest {
        server.enqueue(MockResponse().setBody("anon"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val body = shim.get(baseUrl, "/file.txt", auth = null)

        assertEquals("anon", body)
        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `put sends body with Basic auth`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        val baseUrl = server.url("/").toString().trimEnd('/')

        shim.put(baseUrl, "/state.json", """{"a":1}""", WebDavAuth("alice", "p@ss"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/state.json", request.path)
        assertEquals("""{"a":1}""", request.body.readUtf8())
        val expected = "Basic " + Base64.getEncoder().encodeToString("alice:p@ss".toByteArray())
        assertEquals(expected, request.getHeader("Authorization"))
        // Content-Type matches design §8.4 (text/plain; charset=utf-8).
        val contentType = request.getHeader("Content-Type").orEmpty()
        assertTrue(
            "Expected text/plain content type, was $contentType",
            contentType.startsWith("text/plain"),
        )
    }

    @Test
    fun `put without auth omits Authorization header`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        val baseUrl = server.url("/").toString().trimEnd('/')

        shim.put(baseUrl, "/anon.txt", "payload", auth = null)

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertEquals("payload", request.body.readUtf8())
    }

    @Test
    fun `non-2xx HTTP status throws IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val result = runCatching { shim.get(baseUrl, "/locked.txt", auth = null) }

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(
            "Expected IOException with HTTP 403 detail, was $exception",
            exception!!.message?.contains("403") == true,
        )
    }

    @Test
    fun `joinUrl normalizes trailing slash on base and leading slash on path`() = runTest {
        server.enqueue(MockResponse().setBody("x"))
        // baseUrl has trailing slash, path has leading slash — no double slash in output.
        val baseUrl = server.url("/").toString()

        shim.get(baseUrl, "/leaf.json", auth = null)

        val request = server.takeRequest()
        assertEquals("/leaf.json", request.path)
    }

    @Test
    fun `joinUrl works when path has no leading slash`() = runTest {
        server.enqueue(MockResponse().setBody("x"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        shim.get(baseUrl, "no-slash.txt", auth = null)

        val request = server.takeRequest()
        assertEquals("/no-slash.txt", request.path)
    }
}
