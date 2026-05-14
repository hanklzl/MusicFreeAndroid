package com.zili.android.musicfreeandroid.updater.downloader

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

class OkHttpApkDownloader constructor(
    private val http: OkHttpClient,
    private val cacheRoot: () -> File,
) : ApkDownloader {

    private val currentCall = AtomicReference<Call?>(null)

    override suspend fun download(
        info: UpdateInfo,
        onProgress: (Long, Long, Float) -> Unit,
    ): ApkDownloader.Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val dir = cacheRoot().apply { mkdirs() }
        val finalFile = File(dir, "musicfree-${info.versionCode}.apk")
        val partFile = File(dir, "musicfree-${info.versionCode}.apk.part")
        partFile.delete()
        finalFile.delete()

        for ((index, url) in info.download.withIndex()) {
            val outcome = tryDownload(info = info, url = url, target = partFile, onProgress = onProgress)
            when (outcome) {
                is StepOutcome.Ok -> {
                    if (!partFile.renameTo(finalFile)) {
                        partFile.delete()
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "apk_download_failed",
                            fields = mapOf(
                                "cause" to UpdateError.Network.name,
                                "versionCode" to info.versionCode,
                                "reason" to "rename_failed",
                            ),
                        )
                        return@withContext ApkDownloader.Result.Failure(UpdateError.Network)
                    }
                    MfLog.detail(
                        category = LogCategory.UPDATE,
                        event = "apk_download_complete",
                        fields = mapOf(
                            "versionCode" to info.versionCode,
                            "bytes" to info.size,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                    return@withContext ApkDownloader.Result.Success(finalFile)
                }
                is StepOutcome.HardFail -> {
                    partFile.delete()
                    MfLog.error(
                        category = LogCategory.UPDATE,
                        event = "apk_download_failed",
                        fields = mapOf(
                            "cause" to outcome.cause.name,
                            "versionCode" to info.versionCode,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                        ),
                    )
                    return@withContext ApkDownloader.Result.Failure(outcome.cause)
                }
                is StepOutcome.SoftFail -> {
                    partFile.delete()
                    if (index == info.download.lastIndex) {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "apk_download_failed",
                            fields = mapOf(
                                "cause" to UpdateError.Network.name,
                                "versionCode" to info.versionCode,
                                "durationMs" to (System.currentTimeMillis() - startedAt),
                                "mirrorsExhausted" to true,
                            ),
                        )
                        return@withContext ApkDownloader.Result.Failure(UpdateError.Network)
                    }
                    // try next mirror
                }
                is StepOutcome.Canceled -> {
                    partFile.delete()
                    return@withContext ApkDownloader.Result.Failure(UpdateError.Canceled)
                }
            }
        }
        ApkDownloader.Result.Failure(UpdateError.Network)
    }

    override fun cancel() {
        currentCall.getAndSet(null)?.cancel()
    }

    private fun tryDownload(
        info: UpdateInfo,
        url: String,
        target: File,
        onProgress: (Long, Long, Float) -> Unit,
    ): StepOutcome {
        val request = Request.Builder().url(url).get().build()
        val call = http.newCall(request)
        currentCall.set(call)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return StepOutcome.SoftFail
                // OkHttp 5.x: response.body is non-null
                val body = response.body
                val advertised = body.contentLength()
                if (advertised >= 0 && advertised != info.size) {
                    return StepOutcome.HardFail(UpdateError.SizeMismatch)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            written += n
                            val fraction = if (info.size > 0) (written.toFloat() / info.size) else 0f
                            onProgress(written, info.size, fraction.coerceIn(0f, 1f))
                        }
                    }
                }
                if (written != info.size) return StepOutcome.HardFail(UpdateError.SizeMismatch)
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    return StepOutcome.HardFail(UpdateError.Sha256Mismatch)
                }
                StepOutcome.Ok
            }
        } catch (t: java.io.IOException) {
            if (call.isCanceled()) {
                StepOutcome.Canceled
            } else {
                MfLog.error(
                    category = LogCategory.UPDATE,
                    event = "apk_download_error",
                    throwable = t,
                    fields = mapOf(
                        "url" to url,
                        "host" to LogFields.host(url),
                        "error" to (t.message ?: t.javaClass.simpleName),
                    ),
                )
                StepOutcome.SoftFail
            }
        } finally {
            currentCall.compareAndSet(call, null)
        }
    }

    private sealed interface StepOutcome {
        data object Ok : StepOutcome
        data object SoftFail : StepOutcome
        data class HardFail(val cause: UpdateError) : StepOutcome
        data object Canceled : StepOutcome
    }
}
