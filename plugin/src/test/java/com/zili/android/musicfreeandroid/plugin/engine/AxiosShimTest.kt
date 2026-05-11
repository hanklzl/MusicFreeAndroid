package com.zili.android.musicfreeandroid.plugin.engine

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.MfLogger
import okhttp3.Headers
import org.json.JSONObject
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
class AxiosShimTest {

    private lateinit var logger: RecordingLogger

    @Before
    fun setup() {
        logger = RecordingLogger()
        MfLog.install(logger)
    }

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun `buildResponse backfills netease album picUrl from picId`() {
        val body = """
            {
              "result": {
                "songs": [
                  {
                    "name": "In the End",
                    "album": {
                      "name": "Hybrid Theory",
                      "picId": 109951163572864669
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = buildResponse(body)

        val album = JSONObject(response)
            .getJSONObject("data")
            .getJSONObject("result")
            .getJSONArray("songs")
            .getJSONObject(0)
            .getJSONObject("album")

        assertEquals(
            "https://p1.music.126.net/CUgGGyE5KHiRsyR43mF3eQ==/109951163572864669.jpg",
            album.optString("picUrl"),
        )
    }

    @Test
    fun `request start log includes host status and payload sizes`() {
        val method = AxiosShim::class.java.getDeclaredMethod(
            "logRequest",
            String::class.java,
            String::class.java,
            Headers::class.java,
            String::class.java,
        )
        method.isAccessible = true

        method.invoke(
            AxiosShim,
            "POST",
            "https://music.example.com/api/search",
            Headers.headersOf("X-Trace", "abc"),
            "payload",
        )

        val event = logger.events.single { it.event == "axios_request" }
        assertEquals("music.example.com", event.fields["host"])
        assertEquals("start", event.fields["status"])
        assertFalse(event.fields.containsKey("result"))
        assertEquals(1, event.fields["headerCount"])
        assertEquals(7, event.fields["bodyLength"])
    }

    @Test
    fun `non successful response log marks http status failure`() {
        val method = AxiosShim::class.java.getDeclaredMethod(
            "logResponse",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Headers::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
        )
        method.isAccessible = true

        method.invoke(
            AxiosShim,
            "GET",
            "https://music.example.com/not-found",
            404,
            Headers.headersOf("Content-Type", "text/plain"),
            "missing",
            12L,
        )

        val event = logger.events.single { it.event == "axios_response" }
        assertEquals("music.example.com", event.fields["host"])
        assertEquals(404, event.fields["statusCode"])
        assertEquals("failure", event.fields["result"])
        assertEquals("http_status", event.fields["reason"])
        assertEquals(7, event.fields["responseLength"])
        assertTrue((event.fields["durationMs"] as Long) >= 0)
    }

    private fun buildResponse(body: String): String {
        val method = AxiosShim::class.java.getDeclaredMethod(
            "buildResponse",
            Int::class.javaPrimitiveType,
            String::class.java,
            Headers::class.java,
        )
        method.isAccessible = true
        return method.invoke(AxiosShim, 200, body, null) as String
    }

    private data class RecordedEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = mutableListOf<RecordedEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedEvent(category, event, fields)
        }

        override fun flush() = Unit
    }
}
