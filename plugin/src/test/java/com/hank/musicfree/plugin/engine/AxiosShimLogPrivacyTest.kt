package com.hank.musicfree.plugin.engine

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AxiosShimLogPrivacyTest {
    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        MfLog.configure(MfLog.LogSwitches(detailEnabled = true, errorEnabled = true))
        MfLog.install(object : MfLogger {
            override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) { events += event to fields }
            override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) { events += event to fields }
            override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) {
                events += event to (fields + ("exception" to throwable?.stackTraceToString()))
            }
            override fun flush() = Unit
        })
    }

    @After
    fun teardown() { server.shutdown(); MfLog.resetForTest() }

    @Test
    fun `detailed logging cannot capture auth headers ticket bodies or private URL fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"ticket":"synthetic-response-ticket","expiresAt":9999999999999}"""))
        val result = AxiosShim.performPost(
            server.url("/v1/gateway/ticket?token=synthetic-query").toString(),
            """{"deviceId":"synthetic-device","unknown":"synthetic-body"}""",
            mapOf("headers" to mapOf("Authorization" to "Bearer synthetic-auth", "X-Qing-Sign" to "synthetic-sign")),
        )
        // Sanitization must never change the real HTTP request or response seen by the caller.
        val request = server.takeRequest()
        assertEquals("Bearer synthetic-auth", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("synthetic-device"))
        assertTrue(result.contains("synthetic-response-ticket"))
        assertTrue(events.any { it.first == "axios_request" })
        assertTrue(events.any { it.first == "axios_response" })
        assertFalse(events.toString().contains("synthetic-"))
    }
}
