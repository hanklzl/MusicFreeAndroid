package com.zili.android.musicfreeandroid.plugin.engine

import okhttp3.Headers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AxiosShimTest {

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
}
