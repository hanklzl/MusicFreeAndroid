package com.zili.android.musicfreeandroid.updater.api

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpUpdateClient constructor(
    private val http: OkHttpClient,
    private val mirrors: List<String>,
) : UpdateClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in mirrors) {
            val info = tryFetch(url) ?: continue
            return@withContext info
        }
        null
    }

    private fun tryFetch(url: String): UpdateInfo? = try {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body.string()
            json.decodeFromString(UpdateInfo.serializer(), body)
        }
    } catch (t: Throwable) {
        MfLog.error(
            category = LogCategory.UPDATE,
            event = "update_manifest_fetch_error",
            throwable = t,
            fields = mapOf(
                "url" to url,
                "host" to LogFields.host(url),
                "error" to (t.message ?: t.javaClass.simpleName),
            ),
        )
        null
    }
}
