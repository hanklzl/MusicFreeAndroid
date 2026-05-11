package com.zili.android.musicfreeandroid.downloader.io

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.io.IOException

@Singleton
class OkHttpDownloader @Inject constructor(
    private val client: OkHttpClient,
) : HttpDownloader {

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (HttpDownloadProgress) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        MfLog.detail(
            category = LogCategory.DOWNLOAD,
            event = "download_http_start",
            fields = downloadFields(url = url, target = target) + mapOf(
                "headerCount" to headers.size,
            ),
        )
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            MfLog.error(
                category = LogCategory.DOWNLOAD,
                event = "download_http_failed",
                throwable = e,
                fields = downloadFields(url = url, target = target) + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "network_error",
                ),
            )
            throw HttpDownloadException("network error", e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                MfLog.error(
                    category = LogCategory.DOWNLOAD,
                    event = "download_http_failed",
                    fields = downloadFields(url = url, target = target) + mapOf(
                        "statusCode" to resp.code,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                        "reason" to "http_error",
                    ),
                )
                throw HttpDownloadException("HTTP ${resp.code}")
            }
            val total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val body = resp.body ?: run {
                MfLog.error(
                    category = LogCategory.DOWNLOAD,
                    event = "download_http_failed",
                    fields = downloadFields(url = url, target = target) + mapOf(
                        "statusCode" to resp.code,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                        "reason" to "empty_body",
                    ),
                )
                throw HttpDownloadException("empty body")
            }
            target.parentFile?.mkdirs()
            val source = body.source()
            var downloaded = 0L
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var lastEmit = 0L
                var lastTimeMs = 0L
                while (true) {
                    val n = source.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    downloaded += n
                    val now = System.currentTimeMillis()
                    if (downloaded - lastEmit >= 64 * 1024 || now - lastTimeMs >= 250) {
                        onProgress(HttpDownloadProgress(downloaded, total))
                        lastEmit = downloaded
                        lastTimeMs = now
                    }
                }
                onProgress(HttpDownloadProgress(downloaded, if (total > 0) total else downloaded))
            }
            MfLog.detail(
                category = LogCategory.DOWNLOAD,
                event = "download_http_success",
                fields = downloadFields(url = url, target = target) + mapOf(
                    "statusCode" to resp.code,
                    "durationMs" to elapsedMs(startedAt),
                    "bytes" to downloaded,
                    "totalBytes" to if (total > 0) total else downloaded,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        }
    }

    private fun downloadFields(url: String, target: File): Map<String, Any?> = mapOf(
        "operation" to "download_http",
        "url" to url,
        "host" to LogFields.host(url),
        "fileName" to target.name,
    )

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
