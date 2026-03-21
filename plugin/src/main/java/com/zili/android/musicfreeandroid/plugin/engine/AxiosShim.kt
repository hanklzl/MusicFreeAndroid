package com.zili.android.musicfreeandroid.plugin.engine

import android.util.Log
import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Registers an `axios`-like global object in the QuickJS context,
 * providing `axios.get(url, config)` and `axios.post(url, data, config)` to JS plugins.
 *
 * HTTP requests are executed synchronously via OkHttp (QuickJS is single-threaded).
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
     * Register the `axios` global with `get` and `post` methods.
     */
    fun register(context: QuickJSContext, engine: JsEngine) {
        val axios = context.createNewJSObject()
        val requestFunction = JSCallFunction { args ->
            handleRequest(context, engine, args)
        }

        axios.setProperty("get", JSCallFunction { args ->
            handleGet(context, engine, args)
        })

        axios.setProperty("post", JSCallFunction { args ->
            handlePost(context, engine, args)
        })

        // CommonJS interop for transpiled plugins:
        // require("axios").default(config) and require("axios").request(config)
        axios.setProperty("default", requestFunction)
        axios.setProperty("request", requestFunction)
        axios.setProperty("create", JSCallFunction { _ -> axios })

        context.globalObject.setProperty("axios", axios)

        // Mirror methods onto axios.default for transpiled calls like axios.default.get(...)
        context.evaluate(
            """
            (function() {
              if (globalThis.axios && typeof globalThis.axios.default === "function") {
                globalThis.axios.default.get = globalThis.axios.get;
                globalThis.axios.default.post = globalThis.axios.post;
                globalThis.axios.default.request = globalThis.axios.default;
                globalThis.axios.default.create = function() { return globalThis.axios.default; };
              }
            })();
            """.trimIndent()
        )
    }

    private fun handleRequest(context: QuickJSContext, engine: JsEngine, args: Array<out Any?>): Any? {
        val config = args.getOrNull(0) as? JSObject
            ?: return buildErrorResponse(context, "Config object is required")

        val method = config.getProperty("method")?.toString()?.lowercase().orEmpty().ifBlank { "get" }
        val url = config.getProperty("url")?.toString()
            ?: return buildErrorResponse(context, "Config.url is required")

        return when (method) {
            "post" -> performPost(
                context = context,
                bodyArg = config.getProperty("data"),
                config = config,
                url = url,
            )
            "get" -> performGet(context = context, config = config, url = url)
            else -> buildErrorResponse(context, "Unsupported method: $method")
        }
    }

    private fun handleGet(context: QuickJSContext, engine: JsEngine, args: Array<out Any?>): Any? {
        try {
            val url = args.getOrNull(0)?.toString() ?: return buildErrorResponse(context, "URL is required")
            val config = args.getOrNull(1) as? JSObject

            return performGet(context = context, config = config, url = url)
        } catch (e: Exception) {
            Log.e(TAG, "axios.get failed", e)
            return buildErrorResponse(context, e.message ?: "Unknown error")
        }
    }

    private fun handlePost(context: QuickJSContext, engine: JsEngine, args: Array<out Any?>): Any? {
        try {
            val url = args.getOrNull(0)?.toString() ?: return buildErrorResponse(context, "URL is required")
            val bodyArg = args.getOrNull(1)
            val config = args.getOrNull(2) as? JSObject

            return performPost(context = context, bodyArg = bodyArg, config = config, url = url)
        } catch (e: Exception) {
            Log.e(TAG, "axios.post failed", e)
            return buildErrorResponse(context, e.message ?: "Unknown error")
        }
    }

    private fun performGet(context: QuickJSContext, url: String, config: JSObject?): Any? {
        // Build URL with query params
        val fullUrl = buildUrlWithParams(url, config)

        // Build request
        val requestBuilder = Request.Builder().url(fullUrl).get()
        applyHeaders(requestBuilder, config)

        val response = client.newCall(requestBuilder.build()).execute()
        return response.use {
            val body = readResponseBody(it)
            logResponsePreview(method = "GET", url = fullUrl, status = it.code, body = body)
            buildResponse(context, it.code, body)
        }
    }

    private fun performPost(
        context: QuickJSContext,
        url: String,
        bodyArg: Any?,
        config: JSObject?,
    ): Any? {
        val fullUrl = buildUrlWithParams(url, config)
        val contentType = resolveContentType(config, bodyArg)
        val bodyString = when (bodyArg) {
            is JSObject -> {
                if (contentType.contains("application/x-www-form-urlencoded", ignoreCase = true)) {
                    toFormUrlEncoded(bodyArg)
                } else {
                    context.stringify(bodyArg) ?: "{}"
                }
            }
            is String -> bodyArg
            null -> ""
            else -> bodyArg.toString()
        }
        val requestBody = bodyString.toRequestBody(contentType.toMediaTypeOrNull())

        val requestBuilder = Request.Builder().url(fullUrl).post(requestBody)
        applyHeaders(requestBuilder, config)

        val response = client.newCall(requestBuilder.build()).execute()
        return response.use {
            val body = readResponseBody(it)
            logResponsePreview(method = "POST", url = fullUrl, status = it.code, body = body)
            buildResponse(context, it.code, body)
        }
    }

    private fun resolveContentType(config: JSObject?, bodyArg: Any?): String {
        val explicitContentType = getHeaderIgnoreCase(config = config, key = "content-type")
        if (!explicitContentType.isNullOrBlank()) {
            return explicitContentType
        }
        return when (bodyArg) {
            is String -> "application/x-www-form-urlencoded; charset=utf-8"
            is JSObject -> "application/json; charset=utf-8"
            else -> "text/plain; charset=utf-8"
        }
    }

    private fun getHeaderIgnoreCase(config: JSObject?, key: String): String? {
        val headers = config?.getProperty("headers") as? JSObject ?: return null
        val names = headers.getNames() ?: return null
        for (i in 0 until names.length()) {
            val headerName = names.get(i)?.toString() ?: continue
            if (headerName.equals(key, ignoreCase = true)) {
                return headers.getProperty(headerName)?.toString()
            }
        }
        return null
    }

    private fun toFormUrlEncoded(data: JSObject): String {
        val names = data.getNames() ?: return ""
        val pairs = mutableListOf<String>()
        for (i in 0 until names.length()) {
            val key = names.get(i)?.toString() ?: continue
            val value = data.getProperty(key)?.toString() ?: ""
            pairs += "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return pairs.joinToString("&")
    }

    private fun readResponseBody(response: okhttp3.Response): String? {
        val body = response.body ?: return null
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val bytes = body.bytes()
        if (bytes.isEmpty()) return ""

        val encoding = response.header("Content-Encoding")?.lowercase().orEmpty()
        val decodedBytes = try {
            when {
                encoding.contains("gzip") -> {
                    GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                }
                encoding.contains("deflate") -> {
                    InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                }
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
        val preview = body
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.take(240)
            ?: ""
        Log.d(TAG, "$method $url -> $status body=$preview")
    }

    /**
     * Build URL with query params from config.params (JSObject).
     */
    private fun buildUrlWithParams(baseUrl: String, config: JSObject?): String {
        if (config == null) return baseUrl
        val params = config.getProperty("params") as? JSObject ?: return baseUrl

        val names = params.getNames() ?: return baseUrl
        val queryParts = mutableListOf<String>()
        for (i in 0 until names.length()) {
            val key = names.get(i)?.toString() ?: continue
            val value = params.getProperty(key)?.toString() ?: continue
            queryParts.add(
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            )
        }

        if (queryParts.isEmpty()) return baseUrl

        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator${queryParts.joinToString("&")}"
    }

    /**
     * Apply headers from config.headers (JSObject) to the request builder.
     */
    private fun applyHeaders(builder: Request.Builder, config: JSObject?) {
        if (config == null) return
        val headers = config.getProperty("headers") as? JSObject ?: return

        val names = headers.getNames() ?: return
        for (i in 0 until names.length()) {
            val key = names.get(i)?.toString() ?: continue
            val value = headers.getProperty(key)?.toString() ?: continue
            builder.addHeader(key, value)
        }
    }

    /**
     * Build a JS response object: `{ status: number, data: object|string }`.
     * Attempts to parse the response body as JSON; falls back to raw string.
     */
    private fun buildResponse(context: QuickJSContext, status: Int, body: String?): JSObject {
        val result = context.createNewJSObject()
        result.setProperty("status", status)

        if (body != null) {
            try {
                // Try parsing as JSON via the JS engine
                val parsed = context.parse(body)
                if (parsed is JSObject) {
                    result.setProperty("data", parsed)
                } else {
                    result.setProperty("data", body)
                }
            } catch (_: Exception) {
                // Not valid JSON, return as string
                result.setProperty("data", body)
            }
        } else {
            result.setProperty("data", "")
        }

        return result
    }

    /**
     * Build an error response object: `{ status: -1, data: errorMessage }`.
     */
    private fun buildErrorResponse(context: QuickJSContext, message: String): JSObject {
        val result = context.createNewJSObject()
        result.setProperty("status", -1)
        result.setProperty("data", message)
        return result
    }
}
