package com.hank.musicfree.plugin.engine

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Contract tests for [AxiosShim] timeout behavior.
 *
 * Aligned with RN MusicFree `axios` default of 2000ms. Plugin authors override
 * via `config.timeout` — see plugin-engine-alignment design §8.1.
 *
 * Uses [MockWebServer] with [MockResponse.setBodyDelay] to simulate a slow
 * backend; we then assert that the call fails within the expected timeout
 * window. The window is wide on both ends to absorb CI noise but tight enough
 * to distinguish 500ms from 2000ms from "no timeout at all".
 *
 * `performGet` is `internal` and lives in the same module, so the test can call
 * it directly without going through the JS bridge — same pattern as
 * [AxiosShimTest] reaching `buildResponse`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AxiosShimTimeoutTest {

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
    fun `default timeout is 2000ms`() = runTest {
        server.enqueue(MockResponse().setBodyDelay(5, TimeUnit.SECONDS).setBody("ok"))
        val start = System.currentTimeMillis()
        val result = runCatching {
            AxiosShim.performGet(server.url("/").toString(), config = null)
        }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(
            "Expected IOException due to read timeout, was ${result.exceptionOrNull()}",
            result.isFailure,
        )
        assertTrue(
            "Expected ~2000ms timeout window, was ${elapsed}ms",
            elapsed in 1800L..3500L,
        )
    }

    @Test
    fun `per-call timeout override works`() = runTest {
        server.enqueue(MockResponse().setBodyDelay(5, TimeUnit.SECONDS).setBody("ok"))
        val start = System.currentTimeMillis()
        val result = runCatching {
            AxiosShim.performGet(
                server.url("/").toString(),
                config = mapOf<String, Any?>("timeout" to 500),
            )
        }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Expected IOException, was ${result.exceptionOrNull()}", result.isFailure)
        assertTrue(
            "Expected ~500ms timeout window, was ${elapsed}ms",
            elapsed in 400L..2000L,
        )
    }

    @Test
    fun `per-call timeout accepts long value`() = runTest {
        server.enqueue(MockResponse().setBodyDelay(5, TimeUnit.SECONDS).setBody("ok"))
        val start = System.currentTimeMillis()
        val result = runCatching {
            AxiosShim.performGet(
                server.url("/").toString(),
                config = mapOf<String, Any?>("timeout" to 500L),
            )
        }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Expected IOException, was ${result.exceptionOrNull()}", result.isFailure)
        assertTrue(
            "Expected ~500ms timeout window, was ${elapsed}ms",
            elapsed in 400L..2000L,
        )
    }

    @Test
    fun `non-positive timeout falls back to default`() = runTest {
        server.enqueue(MockResponse().setBodyDelay(5, TimeUnit.SECONDS).setBody("ok"))
        val start = System.currentTimeMillis()
        val result = runCatching {
            AxiosShim.performGet(
                server.url("/").toString(),
                config = mapOf<String, Any?>("timeout" to 0),
            )
        }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Expected IOException, was ${result.exceptionOrNull()}", result.isFailure)
        // 0 → fall back to 2000ms default
        assertTrue(
            "Expected ~2000ms (fallback), was ${elapsed}ms",
            elapsed in 1800L..3500L,
        )
    }

    @Test
    fun `fast response under timeout succeeds`() = runTest {
        server.enqueue(MockResponse().setBody("""{"hello":"world"}"""))
        val response = AxiosShim.performGet(
            server.url("/").toString(),
            config = mapOf<String, Any?>("timeout" to 2000),
        )
        val obj = JSONObject(response)
        assertEquals(200, obj.getInt("status"))
    }
}
