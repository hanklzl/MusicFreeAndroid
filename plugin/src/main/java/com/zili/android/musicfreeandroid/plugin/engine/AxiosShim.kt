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
     */
    suspend fun register(engine: JsEngine) {
        engine.define("axios") {
            asyncFunction<Any?>("get") { args ->
                handleGet(args)
            }
            asyncFunction<Any?>("post") { args ->
                handlePost(args)
            }
            asyncFunction<Any?>("request") { args ->
                handleRequest(args)
            }
            function<Any?>("create") { _ ->
                null
            }
        }

        // Set up CommonJS interop aliases
        engine.evaluate<Any?>(
            """
            (function() {
              var ax = globalThis.axios;
              ax.default = ax;
              ax.create = function() { return ax; };
            })();
            """.trimIndent()
        )
    }

    private suspend fun handleGet(args: Array<Any?>): Any? {
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

    private suspend fun handlePost(args: Array<Any?>): Any? {
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

    private suspend fun handleRequest(args: Array<Any?>): Any? {
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

    private suspend fun performGet(url: String, config: Map<*, *>?): Map<String, Any?> {
        val fullUrl = buildUrlWithParams(url, config)
        val requestBuilder = Request.Builder().url(fullUrl).get()
        applyHeaders(requestBuilder, config)

        val response = client.newCall(requestBuilder.build()).await()
        return response.use {
            val body = readResponseBody(it)
            logResponsePreview(method = "GET", url = fullUrl, status = it.code, body = body)
            buildResponse(it.code, body)
        }
    }

    private suspend fun performPost(
        url: String,
        bodyArg: Any?,
        config: Map<*, *>?,
    ): Map<String, Any?> {
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
            buildResponse(it.code, body)
        }
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

    private fun buildResponse(status: Int, body: String?): Map<String, Any?> {
        val data: Any? = if (body != null) {
            try {
                parseJsonValue(body)
            } catch (_: Exception) {
                body
            }
        } else {
            ""
        }
        return mapOf("status" to status, "data" to data)
    }

    private fun buildErrorResponse(message: String): Map<String, Any?> {
        return mapOf("status" to -1, "data" to message)
    }

    private fun parseJsonValue(json: String): Any? {
        val trimmed = json.trim()
        return when {
            trimmed.startsWith("{") -> jsonObjectToMap(JSONObject(trimmed))
            trimmed.startsWith("[") -> jsonArrayToList(JSONArray(trimmed))
            else -> trimmed
        }
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in obj.keys()) {
            map[key] = jsonElementToKotlin(obj.opt(key))
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any?> {
        return (0 until arr.length()).map { jsonElementToKotlin(arr.opt(it)) }
    }

    private fun jsonElementToKotlin(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        else -> value
    }

    private fun jsonStringify(map: Map<*, *>): String {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k?.toString() ?: continue, v)
        }
        return obj.toString()
    }
}
