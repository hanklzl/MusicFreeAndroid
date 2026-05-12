package com.zili.android.musicfreeandroid.plugin.engine

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * Contract tests for `user:pass@host` URL credential extraction.
 *
 * RN MusicFree's `axios` treats `https://user:pass@host/` as a Basic Auth
 * shortcut. OkHttp does NOT — it throws if userinfo is left inline. So we
 * lift the credentials into an `Authorization` header and strip them from
 * the URL before issuing the request.
 *
 * The `normalizeRequest` helper is unit-tested directly (pure function); the
 * end-to-end `performGet` smoke test confirms the wired-up behavior.
 *
 * Design source: plugin-engine-alignment design §8.1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AxiosShimAuthUrlTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `url with user pass becomes Authorization Basic`() = runTest {
        server.enqueue(MockResponse().setBody("ok"))
        val authedUrl = server.url("/path").newBuilder()
            .username("alice")
            .password("s3cret")
            .build()
            .toString()

        AxiosShim.performGet(authedUrl, config = null)

        val recorded = server.takeRequest()
        val expected = "Basic " + Base64.getEncoder().encodeToString(
            "alice:s3cret".toByteArray(Charsets.UTF_8),
        )
        assertEquals(expected, recorded.getHeader("Authorization"))
        // Userinfo must not leak into the request line.
        assertFalse(
            "request line leaked userinfo: ${recorded.requestLine}",
            recorded.requestLine.contains("alice"),
        )
        assertEquals("/path", recorded.path)
    }

    @Test
    fun `existing Authorization header is preserved`() {
        val headers = mutableMapOf<String, String>("Authorization" to "Bearer token-xyz")
        val cleaned = AxiosShim.normalizeRequest(
            "https://alice:s3cret@example.com/path",
            headers,
        )
        // Explicit Bearer survives — auto Basic does not clobber it.
        assertEquals("Bearer token-xyz", headers["Authorization"])
        // URL still gets cleaned even though we didn't write an Authorization header.
        assertEquals("https://example.com/path", cleaned)
    }

    @Test
    fun `existing authorization header in different case is preserved`() {
        val headers = mutableMapOf<String, String>("authorization" to "Bearer token-lower")
        AxiosShim.normalizeRequest(
            "https://u:p@example.com/",
            headers,
        )
        // We don't insert a second Authorization; case-insensitive matching.
        assertEquals(1, headers.size)
        assertEquals("Bearer token-lower", headers["authorization"])
    }

    @Test
    fun `plain url without credentials does not add Authorization`() = runTest {
        server.enqueue(MockResponse().setBody("ok"))

        AxiosShim.performGet(server.url("/clean").toString(), config = null)

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("/clean", recorded.path)
    }

    @Test
    fun `unparseable url is passed through unchanged`() {
        val headers = mutableMapOf<String, String>()
        // OkHttp's HttpUrl rejects custom schemes — we don't try to be clever,
        // we just pass through (caller deals with the failure).
        val cleaned = AxiosShim.normalizeRequest("ftp://u:p@example.com/file", headers)
        assertEquals("ftp://u:p@example.com/file", cleaned)
        assertNull(headers["Authorization"])
    }

    @Test
    fun `special characters in password are url-decoded before encoding`() {
        // URL-encoded `@:?` in the password — these decode through HttpUrl
        // before we re-encode them into the Basic credential.
        val headers = mutableMapOf<String, String>()
        AxiosShim.normalizeRequest(
            "https://user:p%40ss%3Aword@example.com/",
            headers,
        )
        val expected = "Basic " + Base64.getEncoder().encodeToString(
            "user:p@ss:word".toByteArray(Charsets.UTF_8),
        )
        assertEquals(expected, headers["Authorization"])
    }

    @Test
    fun `password-only credentials are still extracted`() {
        val headers = mutableMapOf<String, String>()
        // username is empty, password is set — both go into the credential.
        val cleaned = AxiosShim.normalizeRequest(
            "https://:secretToken@example.com/",
            headers,
        )
        val expected = "Basic " + Base64.getEncoder().encodeToString(
            ":secretToken".toByteArray(Charsets.UTF_8),
        )
        assertEquals(expected, headers["Authorization"])
        assertEquals("https://example.com/", cleaned)
    }
}
