package com.zili.android.musicfreeandroid.downloader.io

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
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw HttpDownloadException("network error", e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw HttpDownloadException("HTTP ${resp.code}")
            }
            val total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val body = resp.body ?: throw HttpDownloadException("empty body")
            target.parentFile?.mkdirs()
            val source = body.source()
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
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
        }
    }
}
