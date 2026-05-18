package com.hank.musicfree.plugin.engine

import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import java.security.MessageDigest
import java.util.Base64
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

    private const val NETEASE_IMAGE_MAGIC = "3go8&$8*3*3h0k(2)2"
    private const val NETEASE_IMAGE_HOST = "https://p1.music.126.net"
    private const val REQUEST_BODY_PREVIEW_CHARS = 200
    private const val RESPONSE_BODY_PREVIEW_CHARS = 240
    private const val HEADER_VALUE_PREVIEW_CHARS = 120
    private const val HEADER_PREVIEW_CHARS = 600

    /**
     * Default per-call HTTP timeout in milliseconds.
     *
     * Aligned with RN MusicFree's `axios` default (2000ms). Plugin code that needs
     * more headroom MUST pass `config.timeout` per-call; we never relax this default.
     */
    internal const val DEFAULT_TIMEOUT_MS = 2000L

    /**
     * Shared base client — connection pool / dispatcher are reused across calls.
     * Per-call we derive a `.newBuilder()` copy with the requested timeout to avoid
     * GC churn on every plugin request.
     *
     * Defaults to a bare builder so unit tests and (very) early app startup can
     * call into [AxiosShim] before Hilt finishes wiring. Production swaps this in
     * via [setBaseClient] from [com.hank.musicfree.MusicFreeApplication.onCreate],
     * promoting the client to a `@BaseOkHttp` derivative so all axios traffic
     * flows through [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory].
     */
    @Volatile
    private var baseClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    /**
     * Static injection point for the `@BaseOkHttp` client. Called exactly once by
     * [com.hank.musicfree.MusicFreeApplication.onCreate] after Hilt finishes
     * field injection. The new client must already carry the
     * [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory]; we
     * reseat it through `.newBuilder().followRedirects(true).build()` so the
     * axios-side default (auto-follow 30x) is preserved regardless of how the
     * base client was configured.
     *
     * `@Volatile` makes the swap visible across threads without taking a lock;
     * per-call [clientFor] always reads the latest reference.
     */
    @JvmStatic
    fun setBaseClient(client: OkHttpClient) {
        baseClient = client.newBuilder().followRedirects(true).build()
    }

    private fun clientFor(timeoutMs: Long): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun resolveTimeoutMs(config: Map<*, *>?): Long {
        val raw = config?.get("timeout") ?: return DEFAULT_TIMEOUT_MS
        return when (raw) {
            is Number -> raw.toLong().takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
            is String -> raw.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
            else -> DEFAULT_TIMEOUT_MS
        }
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
                var res;
                try { res = JSON.parse(json); } catch(e) { res = { status: -1, data: json }; }
                globalThis.__lastAxiosResponse = res;
                return res;
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
        val url = args.getOrNull(0)?.toString()
        return try {
            if (url.isNullOrBlank()) {
                logRequestFailed(
                    method = "GET",
                    url = url,
                    reason = LogFields.Reason.INVALID_URL,
                )
                return buildErrorResponse("URL is required")
            }
            val config = args.getOrNull(1) as? Map<*, *>
            performGet(url = url, config = config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logRequestFailed(
                method = "GET",
                url = url,
                reason = "request_failed",
                throwable = e,
            )
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private suspend fun handlePost(args: Array<Any?>): String {
        val url = args.getOrNull(0)?.toString()
        return try {
            if (url.isNullOrBlank()) {
                logRequestFailed(
                    method = "POST",
                    url = url,
                    reason = LogFields.Reason.INVALID_URL,
                )
                return buildErrorResponse("URL is required")
            }
            val bodyArg = args.getOrNull(1)
            val config = args.getOrNull(2) as? Map<*, *>
            performPost(url = url, bodyArg = bodyArg, config = config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logRequestFailed(
                method = "POST",
                url = url,
                reason = "request_failed",
                throwable = e,
            )
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private suspend fun handleRequest(args: Array<Any?>): String {
        val config = args.getOrNull(0) as? Map<*, *>
            ?: run {
                logRequestFailed(
                    method = "GET",
                    url = null,
                    reason = "request_build_failed",
                )
                return buildErrorResponse("Config object is required")
            }

        val method = config["method"]?.toString()?.lowercase().orEmpty().ifBlank { "get" }
        val url = config["url"]?.toString()
            ?: run {
                logRequestFailed(
                    method = method.uppercase(),
                    url = null,
                    reason = LogFields.Reason.INVALID_URL,
                )
                return buildErrorResponse("Config.url is required")
            }

        return try {
            when (method) {
                "post" -> performPost(url = url, bodyArg = config["data"], config = config)
                "get" -> performGet(url = url, config = config)
                else -> {
                    logRequestFailed(
                        method = method.uppercase(),
                        url = url,
                        reason = "unsupported_method",
                    )
                    buildErrorResponse("Unsupported method: $method")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logRequestFailed(
                method = method.uppercase(),
                url = url,
                reason = "request_failed",
                throwable = e,
            )
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    internal suspend fun performGet(url: String, config: Map<*, *>?): String {
        val mutableHeaders = extractMutableHeaders(config)
        val authedUrl = normalizeRequest(url, mutableHeaders)
        val fullUrl = buildUrlWithParams(authedUrl, config)
        val request = buildRequestOrNull(
            method = "GET",
            url = fullUrl,
            bodyString = null,
            headers = mutableHeaders,
        ) ?: return buildErrorResponse("Invalid request")
        logRequest(method = "GET", url = fullUrl, headers = request.headers)

        val timeoutMs = resolveTimeoutMs(config)
        val client = clientFor(timeoutMs)
        val startedAt = System.currentTimeMillis()
        val response = try {
            client.newCall(request).await()
        } catch (e: CancellationException) {
            logRequestCancelled(
                method = "GET",
                url = fullUrl,
                durationMs = System.currentTimeMillis() - startedAt,
            )
            throw e
        } catch (e: Exception) {
            logRequestFailed(
                method = "GET",
                url = fullUrl,
                reason = "network_error",
                throwable = e,
                durationMs = System.currentTimeMillis() - startedAt,
            )
            return buildErrorResponse(e.message ?: "Unknown error")
        }
        return response.use {
            val body = readResponseBody(it)
            logResponse(
                method = "GET",
                url = fullUrl,
                status = it.code,
                headers = it.headers,
                body = body,
                durationMs = System.currentTimeMillis() - startedAt,
            )
            buildResponse(it.code, body, it.headers)
        }
    }

    internal suspend fun performPost(
        url: String,
        bodyArg: Any?,
        config: Map<*, *>?,
    ): String {
        val mutableHeaders = extractMutableHeaders(config)
        val authedUrl = normalizeRequest(url, mutableHeaders)
        val fullUrl = buildUrlWithParams(authedUrl, config)
        val contentType = resolveContentType(mutableHeaders, bodyArg)
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

        val request = buildRequestOrNull(
            method = "POST",
            url = fullUrl,
            bodyString = bodyString,
            requestBody = requestBody,
            headers = mutableHeaders,
        ) ?: return buildErrorResponse("Invalid request")
        logRequest(
            method = "POST",
            url = fullUrl,
            headers = request.headers,
            bodyPreview = bodyString,
        )

        val timeoutMs = resolveTimeoutMs(config)
        val client = clientFor(timeoutMs)
        val startedAt = System.currentTimeMillis()
        val response = try {
            client.newCall(request).await()
        } catch (e: CancellationException) {
            logRequestCancelled(
                method = "POST",
                url = fullUrl,
                durationMs = System.currentTimeMillis() - startedAt,
                bodyLength = bodyString.length,
            )
            throw e
        } catch (e: Exception) {
            logRequestFailed(
                method = "POST",
                url = fullUrl,
                reason = "network_error",
                throwable = e,
                durationMs = System.currentTimeMillis() - startedAt,
                bodyLength = bodyString.length,
            )
            return buildErrorResponse(e.message ?: "Unknown error")
        }
        return response.use {
            val body = readResponseBody(it)
            logResponse(
                method = "POST",
                url = fullUrl,
                status = it.code,
                headers = it.headers,
                body = body,
                durationMs = System.currentTimeMillis() - startedAt,
            )
            buildResponse(it.code, body, it.headers)
        }
    }

    /**
     * Lift `user:pass@host` URL credentials into an `Authorization: Basic <…>`
     * header, then strip them from the returned URL. RN MusicFree relies on this
     * behavior — OkHttp does NOT auto-promote URL userinfo into a header (it
     * fails with `IllegalArgumentException` if you keep it inline).
     *
     * Returns the original URL untouched when:
     * - the URL cannot be parsed by OkHttp (e.g. exotic schemes),
     * - the URL carries no userinfo,
     * - or the caller already provided an `Authorization` header (case-insensitive).
     */
    internal fun normalizeRequest(
        originalUrl: String,
        headers: MutableMap<String, String>,
    ): String {
        val parsed: HttpUrl = originalUrl.toHttpUrlOrNull() ?: return originalUrl
        val username = parsed.username
        val password = parsed.password
        if (username.isEmpty() && password.isEmpty()) {
            return originalUrl
        }
        val hasAuth = headers.keys.any { it.equals("Authorization", ignoreCase = true) }
        if (!hasAuth) {
            // OkHttp decodes `parsed.username` / `parsed.password` from percent
            // encoding (matching whatwg URL semantics), so special chars round-trip
            // correctly into the Basic credential.
            val token = Base64.getEncoder().encodeToString(
                "$username:$password".toByteArray(Charsets.UTF_8),
            )
            headers["Authorization"] = "Basic $token"
        }
        return parsed.newBuilder()
            .username("")
            .password("")
            .build()
            .toString()
    }

    private fun extractMutableHeaders(config: Map<*, *>?): MutableMap<String, String> {
        val headers = config?.get("headers") as? Map<*, *> ?: return mutableMapOf()
        val result = LinkedHashMap<String, String>(headers.size)
        for ((k, v) in headers) {
            val key = k?.toString() ?: continue
            val value = v?.toString() ?: continue
            result[key] = value
        }
        return result
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

    private fun resolveContentType(headers: Map<String, String>, bodyArg: Any?): String {
        val explicitContentType = headers.entries.firstOrNull { (k, _) ->
            k.equals("content-type", ignoreCase = true)
        }?.value
        if (!explicitContentType.isNullOrBlank()) {
            return explicitContentType
        }
        return when (bodyArg) {
            is String -> "application/x-www-form-urlencoded; charset=utf-8"
            is Map<*, *> -> "application/json; charset=utf-8"
            else -> "text/plain; charset=utf-8"
        }
    }

    private fun toFormUrlEncoded(data: Map<*, *>): String {
        return data.entries.mapNotNull { (k, v) ->
            val key = k?.toString() ?: return@mapNotNull null
            val value = v?.toString() ?: ""
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }.joinToString("&")
    }

    private fun readResponseBody(response: Response): String? {
        val body: okhttp3.ResponseBody? = response.body
        if (body == null) return null
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
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "axios_response_decode_failed",
                throwable = e,
                fields = mapOf(
                    "status" to response.code,
                    "statusCode" to response.code,
                    "encoding" to encoding,
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "response_decode_failed",
                ),
            )
            bytes
        }
        return decodedBytes.toString(charset)
    }

    private fun buildRequestOrNull(
        method: String,
        url: String,
        bodyString: String?,
        requestBody: okhttp3.RequestBody? = null,
        headers: Map<String, String>,
    ): Request? {
        return try {
            val builder = Request.Builder().url(url)
            if (method == "POST") {
                builder.post(requestBody ?: (bodyString ?: "").toRequestBody(resolveContentType(headers, bodyString).toMediaTypeOrNull()))
            } else {
                builder.get()
            }
            applyHeaders(builder, headers)
            builder.build()
        } catch (e: IllegalArgumentException) {
            logRequestFailed(
                method = method,
                url = url,
                reason = LogFields.Reason.INVALID_URL,
                throwable = e,
                bodyLength = bodyString?.length ?: 0,
            )
            null
        } catch (e: Exception) {
            logRequestFailed(
                method = method,
                url = url,
                reason = "request_build_failed",
                throwable = e,
                bodyLength = bodyString?.length ?: 0,
            )
            null
        }
    }

    private fun logRequest(method: String, url: String, headers: Headers, bodyPreview: String? = null) {
        val headerPreview = headersPreview(headers)
        val sanitizedPreview = bodyPreview?.takePreview(REQUEST_BODY_PREVIEW_CHARS)
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "axios_request",
            fields = mapOf(
                "method" to method,
                "url" to url,
                "host" to LogFields.host(url),
                "status" to "start",
                "headerCount" to headers.size,
                "headerPreviewLength" to headerPreview.length,
                "headerPreview" to headerPreview,
                "bodyLength" to (bodyPreview?.length ?: 0),
            ) + (sanitizedPreview?.let { mapOf("bodyPreview" to it) }.orEmpty()),
        )
    }

    private fun logResponse(
        method: String,
        url: String,
        status: Int,
        headers: Headers,
        body: String?,
        durationMs: Long,
    ) {
        val preview = body
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.takePreview(RESPONSE_BODY_PREVIEW_CHARS)
            ?: ""
        val headerPreview = headersPreview(headers)
        val successful = status in 200..299
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "axios_response",
            fields = mapOf(
                "method" to method,
                "url" to url,
                "host" to LogFields.host(url),
                "status" to status,
                "statusCode" to status,
                "durationMs" to durationMs,
                "result" to if (successful) LogFields.Result.SUCCESS else LogFields.Result.FAILURE,
                "headerCount" to headers.size,
                "headerPreviewLength" to headerPreview.length,
                "headerPreview" to headerPreview,
                "responseLength" to (body?.length ?: 0),
                "bodyPreview" to preview,
            ) + if (successful) emptyMap() else mapOf("reason" to "http_status"),
        )
    }

    private fun logRequestFailed(
        method: String,
        url: String?,
        reason: String,
        throwable: Throwable? = null,
        durationMs: Long? = null,
        bodyLength: Int = 0,
    ) {
        MfLog.error(
            category = LogCategory.PLUGIN,
            event = "axios_request_failed",
            throwable = throwable,
            fields = mapOf(
                "method" to method,
                "url" to url.orEmpty(),
                "host" to LogFields.host(url),
                "status" to "failed",
                "result" to LogFields.Result.FAILURE,
                "reason" to reason,
                "bodyLength" to bodyLength,
            ) + (durationMs?.let { mapOf("durationMs" to it) }.orEmpty()),
        )
    }

    private fun logRequestCancelled(
        method: String,
        url: String?,
        durationMs: Long,
        bodyLength: Int = 0,
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "axios_request_cancelled",
            fields = mapOf(
                "method" to method,
                "url" to url.orEmpty(),
                "host" to LogFields.host(url),
                "status" to "cancelled",
                "result" to LogFields.Result.CANCELLED,
                "reason" to LogFields.Reason.CANCELLED,
                "durationMs" to durationMs,
                "bodyLength" to bodyLength,
            ),
        )
    }

    private fun headersPreview(headers: Headers): String {
        return headers.names().joinToString(",") { name ->
            val values = headers.values(name)
            val joined = values.joinToString("|") { value ->
                value.takePreview(HEADER_VALUE_PREVIEW_CHARS)
            }
            "$name=$joined"
        }.takePreview(HEADER_PREVIEW_CHARS)
    }

    private fun String.takePreview(maxChars: Int): String {
        return if (length <= maxChars) this else "${take(maxChars)}..."
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

    private fun applyHeaders(builder: Request.Builder, headers: Map<String, String>) {
        for ((k, v) in headers) {
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
                    trimmed.startsWith("{") -> obj.put("data", normalizeProviderPayload(JSONObject(trimmed)))
                    trimmed.startsWith("[") -> obj.put("data", normalizeProviderPayload(JSONArray(trimmed)))
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

    private fun normalizeProviderPayload(value: Any): Any {
        return when (value) {
            is JSONObject -> {
                backfillNeteasePicUrl(value)
                val keys = value.keys().asSequence().toList()
                for (key in keys) {
                    val child = value.opt(key)
                    if (child != null && child != JSONObject.NULL) {
                        value.put(key, normalizeProviderPayload(child))
                    }
                }
                value
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val child = value.opt(i)
                    if (child != null && child != JSONObject.NULL) {
                        value.put(i, normalizeProviderPayload(child))
                    }
                }
                value
            }
            else -> value
        }
    }

    private fun backfillNeteasePicUrl(obj: JSONObject) {
        if (obj.optString("picUrl").isNotBlank()) return

        val picId = neteasePicId(obj) ?: return
        obj.put("picUrl", neteaseImageUrl(picId))
    }

    private fun neteasePicId(obj: JSONObject): String? {
        val raw = obj.opt("picId")
            ?.takeUnless { it == JSONObject.NULL }
            ?: return null
        val value = when (raw) {
            is Number -> raw.toLong().toString()
            else -> raw.toString().substringBefore(".")
        }
        return value.takeIf { id ->
            id.isNotBlank() && id != "0" && id.all(Char::isDigit)
        }
    }

    private fun neteaseImageUrl(picId: String): String {
        val encryptedSource = ByteArray(picId.length) { index ->
            (picId[index].code xor NETEASE_IMAGE_MAGIC[index % NETEASE_IMAGE_MAGIC.length].code).toByte()
        }
        val digest = MessageDigest.getInstance("MD5").digest(encryptedSource)
        val encoded = Base64.getEncoder()
            .encodeToString(digest)
            .replace('/', '_')
            .replace('+', '-')
        return "$NETEASE_IMAGE_HOST/$encoded/$picId.jpg"
    }

    private fun jsonStringify(map: Map<*, *>): String {
        return (toJsonValue(map) as JSONObject).toString()
    }

    /**
     * Recursively convert Kotlin/quickjs-kt values (Map / JsObject / List / JsArray /
     * primitives) into org.json types so that nested structures serialize correctly.
     * Plain `JSONObject.put(k, v)` does not auto-wrap, so nested `JsObject` values
     * would otherwise be serialized as `Object.toString()` (e.g.
     * `"com.dokar.quickjs.binding.JsObject@..."`).
     */
    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((k, v) in value) {
                    val key = k?.toString() ?: continue
                    obj.put(key, toJsonValue(v))
                }
                obj
            }
            is Iterable<*> -> {
                val arr = JSONArray()
                for (item in value) arr.put(toJsonValue(item))
                arr
            }
            is Array<*> -> {
                val arr = JSONArray()
                for (item in value) arr.put(toJsonValue(item))
                arr
            }
            is Number, is Boolean, is String -> value
            else -> value.toString()
        }
    }
}
