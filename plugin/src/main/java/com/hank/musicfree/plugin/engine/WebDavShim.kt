package com.hank.musicfree.plugin.engine

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Auth credentials for a WebDAV endpoint. `password` defaults to empty for the
 * password-less anonymous case (rarely used in practice).
 */
data class WebDavAuth(val username: String, val password: String)

/**
 * Minimal `require('webdav')` backend.
 *
 * RN MusicFree plugins commonly use the official `webdav` npm package to back
 * up / restore user state. We do NOT ship the full package — we implement the
 * two RPCs that the JS shim ([webdav.js]) exposes:
 *
 *  - `__webdav_get` → HTTP GET, returns body as UTF-8 string
 *  - `__webdav_put` → HTTP PUT with `text/plain; charset=utf-8`
 *
 * Other WebDAV verbs (PROPFIND, MKCOL, DELETE, MOVE, …) are intentionally
 * unimplemented: the JS shim never references them, so plugins that need them
 * will throw `__webdav_X is not a function` at call time — which is the
 * explicit failure mode we want (vs. silently misbehaving).
 *
 * The OkHttpClient is provided by [WebDavShimModule] as a Hilt singleton —
 * derived from `@BaseOkHttp` so all WebDAV traffic flows through the
 * [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory] and gets
 * counted into `traffic_daily`. WebDAV uses a separate timeout profile (10s)
 * from [AxiosShim]'s 2000ms axios default, but shares the same base client
 * (connection pool / dispatcher / event listener factory).
 */
class WebDavShim(
    private val client: OkHttpClient,
) {

    suspend fun get(baseUrl: String, path: String, auth: WebDavAuth?): String =
        request(method = "GET", baseUrl = baseUrl, path = path, body = null, auth = auth)

    suspend fun put(baseUrl: String, path: String, data: String, auth: WebDavAuth?): String =
        request(method = "PUT", baseUrl = baseUrl, path = path, body = data, auth = auth)

    private suspend fun request(
        method: String,
        baseUrl: String,
        path: String,
        body: String?,
        auth: WebDavAuth?,
    ): String {
        val url = joinUrl(baseUrl, path)
        val builder = Request.Builder().url(url)
        when (method) {
            "GET" -> builder.get()
            "PUT" -> {
                val requestBody = (body ?: "").toRequestBody(
                    "text/plain; charset=utf-8".toMediaTypeOrNull(),
                )
                builder.put(requestBody)
            }
            else -> error("Unsupported WebDAV method: $method")
        }
        if (auth != null) {
            val token = Base64.getEncoder().encodeToString(
                "${auth.username}:${auth.password}".toByteArray(Charsets.UTF_8),
            )
            builder.header("Authorization", "Basic $token")
        }

        val startedAt = System.currentTimeMillis()
        val response = client.newCall(builder.build()).await()
        return response.use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "webdav_request",
                fields = mapOf(
                    "method" to method,
                    "url" to url,
                    "status" to resp.code,
                    "durationMs" to System.currentTimeMillis() - startedAt,
                ),
            )
            if (!resp.isSuccessful) {
                throw IOException("WebDAV $method $url failed: HTTP ${resp.code}")
            }
            responseBody
        }
    }

    private fun joinUrl(baseUrl: String, path: String): String {
        val trimmedBase = baseUrl.trimEnd('/')
        val trimmedPath = path.trimStart('/')
        return if (trimmedPath.isEmpty()) trimmedBase else "$trimmedBase/$trimmedPath"
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { cancel() }
    }

    companion object {
        /**
         * Register `__webdav_get` / `__webdav_put` global async functions on the
         * QuickJS context. The matching JS shim lives at
         * `assets/jslibs/webdav.js` and is wired in via [RequireShim].
         *
         * Argument layout (positional, matches JS):
         *
         *   __webdav_get(baseUrl: string, path: string, auth: { username, password } | null)
         *   __webdav_put(baseUrl: string, path: string, data: string, auth: { username, password } | null)
         *
         * The [shim] must be injected by the caller — typically a Hilt-provided
         * singleton from [WebDavShimModule] so every QuickJS context shares the
         * same `@BaseOkHttp`-derived OkHttp pool / event listener factory.
         */
        suspend fun register(engine: JsEngine, shim: WebDavShim) {
            engine.asyncFunction<String>("__webdav_get") { args ->
                val baseUrl = args.getOrNull(0)?.toString().orEmpty()
                val path = args.getOrNull(1)?.toString().orEmpty()
                val auth = parseAuth(args.getOrNull(2))
                shim.get(baseUrl, path, auth)
            }
            engine.asyncFunction<String>("__webdav_put") { args ->
                val baseUrl = args.getOrNull(0)?.toString().orEmpty()
                val path = args.getOrNull(1)?.toString().orEmpty()
                val data = args.getOrNull(2)?.toString().orEmpty()
                val auth = parseAuth(args.getOrNull(3))
                shim.put(baseUrl, path, data, auth)
            }
        }

        private fun parseAuth(raw: Any?): WebDavAuth? {
            val map = raw as? Map<*, *> ?: return null
            val username = map["username"]?.toString() ?: return null
            val password = map["password"]?.toString().orEmpty()
            return WebDavAuth(username = username, password = password)
        }
    }
}
