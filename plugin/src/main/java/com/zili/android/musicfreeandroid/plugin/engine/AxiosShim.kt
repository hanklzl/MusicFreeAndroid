package com.zili.android.musicfreeandroid.plugin.engine

import android.util.Log
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Headers
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Registers an `axios`-like global object in the QuickJs context.
 * HTTP requests are truly async — JS thread is released during network I/O.
 */
object AxiosShim {

    private const val TAG = "AxiosShim"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Register the `axios` global with `get`, `post`, `request`, `default`, and `create` methods.
     *
     * axios must be callable as a function: `axios(config)` and `axios.default(config)`.
     * We register individual async functions at global scope, then assemble the axios
     * object structure in JS so that `axios` itself is a callable function.
     */
    suspend fun register(engine: JsEngine) {
        // Register the core async handlers as global functions.
        // They return JSON strings — the JS wrapper parses them into objects.
        // This avoids quickjs-kt nested Map conversion issues.
        engine.asyncFunction<String>("__axios_get") { args -> handleGet(args) }
        engine.asyncFunction<String>("__axios_post") { args -> handlePost(args) }
        engine.asyncFunction<String>("__axios_request") { args -> handleRequest(args) }

        // Build the axios object in JS: axios is a callable function with .get/.post/.request.
        // Each wrapper awaits the JSON string and parses it.
        engine.evaluate<Any?>(
            """
            (function() {
              async function parseResponse(promise) {
                var json = await promise;
                try { return JSON.parse(json); } catch(e) { return { status: -1, data: json }; }
              }
              var axios = function(config) { return parseResponse(__axios_request(config)); };
              axios.get = function() { return parseResponse(__axios_get.apply(null, arguments)); };
              axios.post = function() { return parseResponse(__axios_post.apply(null, arguments)); };
              axios.request = function(config) { return parseResponse(__axios_request(config)); };
              axios.create = function() { return axios; };
              axios.default = axios;
              globalThis.axios = axios;
            })();
            """.trimIndent()
        )
    }

    private suspend fun handleGet(args: Array<Any?>): String {
        return try {
            val url = args.getOrNull(0)?.toString()
                ?: return buildErrorResponse("URL is required")
            val config = args.getOrNull(1) as? Map<*, *>
            performGet(url = url, config = config)
        } catch (e: Exception) {
            Log.e(TAG, "axios.get failed", e)
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private suspend fun handlePost(args: Array<Any?>): String {
        return try {
            val url = args.getOrNull(0)?.toString()
                ?: return buildErrorResponse("URL is required")
            val bodyArg = args.getOrNull(1)
            val config = args.getOrNull(2) as? Map<*, *>
            performPost(url = url, bodyArg = bodyArg, config = config)
        } catch (e: Exception) {
            Log.e(TAG, "axios.post failed", e)
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private suspend fun handleRequest(args: Array<Any?>): String {
        val config = args.getOrNull(0) as? Map<*, *>
            ?: return buildErrorResponse("Config object is required")

        val method = config["method"]?.toString()?.lowercase().orEmpty().ifBlank { "get" }
        val url = config["url"]?.toString()
            ?: return buildErrorResponse("Config.url is required")

        return try {
            when (method) {
                "post" -> performPost(url = url, bodyArg = config["data"], config = config)
                "get" -> performGet(url = url, config = config)
                else -> buildErrorResponse("Unsupported method: $method")
            }
        } catch (e: Exception) {
            Log.e(TAG, "axios.request failed", e)
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private suspend fun performGet(url: String, config: Map<*, *>?): String {
        val fullUrl = buildUrlWithParams(url, config)
        val requestBuilder = Request.Builder().url(fullUrl).get()
        applyHeaders(requestBuilder, config)

        val response = client.newCall(requestBuilder.build()).await()
        return response.use {
            val body = readResponseBody(it)
            logResponsePreview(method = "GET", url = fullUrl, status = it.code, body = body)
            buildResponse(it.code, body, it.headers)
        }
    }

    private suspend fun performPost(
        url: String,
        bodyArg: Any?,
        config: Map<*, *>?,
    ): String {
        val fullUrl = buildUrlWithParams(url, config)
        val contentType = resolveContentType(config, bodyArg)
        val bodyString = when (bodyArg) {
            is Map<*, *> -> {
                if (contentType.contains("application/x-www-form-urlencoded", ignoreCase = true)) {
                    toFormUrlEncoded(bodyArg)
                } else {
                    jsonStringify(bodyArg)
                }
            }
            is String -> bodyArg
            null -> ""
            else -> bodyArg.toString()
        }
        val requestBody = bodyString.toRequestBody(contentType.toMediaTypeOrNull())

        val requestBuilder = Request.Builder().url(fullUrl).post(requestBody)
        applyHeaders(requestBuilder, config)

        val response = client.newCall(requestBuilder.build()).await()
        return response.use {
            val body = readResponseBody(it)
            logResponsePreview(method = "POST", url = fullUrl, status = it.code, body = body)
            buildResponse(it.code, body, it.headers)
        }
    }

    // -- OkHttp suspend extension --

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

    private fun resolveContentType(config: Map<*, *>?, bodyArg: Any?): String {
        val explicitContentType = getHeaderIgnoreCase(config, "content-type")
        if (!explicitContentType.isNullOrBlank()) {
            return explicitContentType
        }
        return when (bodyArg) {
            is String -> "application/x-www-form-urlencoded; charset=utf-8"
            is Map<*, *> -> "application/json; charset=utf-8"
            else -> "text/plain; charset=utf-8"
        }
    }

    private fun getHeaderIgnoreCase(config: Map<*, *>?, key: String): String? {
        val headers = config?.get("headers") as? Map<*, *> ?: return null
        return headers.entries.firstOrNull { (k, _) ->
            k.toString().equals(key, ignoreCase = true)
        }?.value?.toString()
    }

    private fun toFormUrlEncoded(data: Map<*, *>): String {
        return data.entries.mapNotNull { (k, v) ->
            val key = k?.toString() ?: return@mapNotNull null
            val value = v?.toString() ?: ""
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }.joinToString("&")
    }

    private fun readResponseBody(response: Response): String? {
        val body = response.body ?: return null
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val bytes = body.bytes()
        if (bytes.isEmpty()) return ""

        val encoding = response.header("Content-Encoding")?.lowercase().orEmpty()
        val decodedBytes = try {
            when {
                encoding.contains("gzip") ->
                    GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                encoding.contains("deflate") ->
                    InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                else -> bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode response body with Content-Encoding='$encoding'", e)
            bytes
        }
        return decodedBytes.toString(charset)
    }

    private fun logResponsePreview(method: String, url: String, status: Int, body: String?) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val preview = body?.replace("\n", " ")?.replace("\r", " ")?.take(240) ?: ""
        Log.d(TAG, "$method $url -> $status body=$preview")
    }

    private fun buildUrlWithParams(baseUrl: String, config: Map<*, *>?): String {
        if (config == null) return baseUrl
        val params = config["params"] as? Map<*, *> ?: return baseUrl

        val queryParts = params.entries.mapNotNull { (k, v) ->
            val key = k?.toString() ?: return@mapNotNull null
            val value = v?.toString() ?: return@mapNotNull null
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        if (queryParts.isEmpty()) return baseUrl
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator${queryParts.joinToString("&")}"
    }

    private fun applyHeaders(builder: Request.Builder, config: Map<*, *>?) {
        if (config == null) return
        val headers = config["headers"] as? Map<*, *> ?: return
        for ((key, value) in headers) {
            val k = key?.toString() ?: continue
            val v = value?.toString() ?: continue
            builder.addHeader(k, v)
        }
    }

    private fun buildResponse(status: Int, body: String?, headers: Headers? = null): String {
        val obj = JSONObject()
        obj.put("status", status)
        obj.put("statusCode", status)
        if (body != null) {
            try {
                val trimmed = body.trim()
                when {
                    trimmed.startsWith("{") -> obj.put("data", JSONObject(trimmed))
                    trimmed.startsWith("[") -> obj.put("data", JSONArray(trimmed))
                    else -> obj.put("data", body)
                }
            } catch (_: Exception) {
                obj.put("data", body)
            }
        } else {
            obj.put("data", "")
        }
        // Include headers to match real axios response structure
        val headersObj = JSONObject()
        if (headers != null) {
            for (name in headers.names()) {
                val values = headers.values(name)
                if (values.size == 1) {
                    headersObj.put(name.lowercase(), values[0])
                } else {
                    headersObj.put(name.lowercase(), JSONArray(values))
                }
            }
        }
        obj.put("headers", headersObj)
        return obj.toString()
    }

    private fun buildErrorResponse(message: String): String {
        val obj = JSONObject()
        obj.put("status", -1)
        obj.put("statusCode", -1)
        obj.put("data", message)
        obj.put("headers", JSONObject())
        return obj.toString()
    }

    private fun jsonStringify(map: Map<*, *>): String {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k?.toString() ?: continue, v)
        }
        return obj.toString()
    }
}
