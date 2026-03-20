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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

        axios.setProperty("get", JSCallFunction { args ->
            handleGet(context, engine, args)
        })

        axios.setProperty("post", JSCallFunction { args ->
            handlePost(context, engine, args)
        })

        context.globalObject.setProperty("axios", axios)
    }

    private fun handleGet(context: QuickJSContext, engine: JsEngine, args: Array<out Any?>): Any? {
        try {
            val url = args.getOrNull(0)?.toString() ?: return buildErrorResponse(context, "URL is required")
            val config = args.getOrNull(1) as? JSObject

            // Build URL with query params
            val fullUrl = buildUrlWithParams(url, config)

            // Build request
            val requestBuilder = Request.Builder().url(fullUrl).get()
            applyHeaders(requestBuilder, config)

            val response = client.newCall(requestBuilder.build()).execute()
            return response.use { buildResponse(context, it.code, it.body?.string()) }
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

            // Build request body
            val bodyString = when (bodyArg) {
                is JSObject -> context.stringify(bodyArg) ?: "{}"
                is String -> bodyArg
                null -> ""
                else -> bodyArg.toString()
            }
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = bodyString.toRequestBody(mediaType)

            val requestBuilder = Request.Builder().url(url).post(requestBody)
            applyHeaders(requestBuilder, config)

            val response = client.newCall(requestBuilder.build()).execute()
            return response.use { buildResponse(context, it.code, it.body?.string()) }
        } catch (e: Exception) {
            Log.e(TAG, "axios.post failed", e)
            return buildErrorResponse(context, e.message ?: "Unknown error")
        }
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
