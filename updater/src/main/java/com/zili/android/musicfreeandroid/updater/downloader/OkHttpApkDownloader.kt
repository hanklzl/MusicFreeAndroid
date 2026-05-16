package com.zili.android.musicfreeandroid.updater.downloader

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.checker.ResolvedUpdate
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
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
        update: ResolvedUpdate,
        onProgress: (Long, Long, Float) -> Unit,
    ): ApkDownloader.Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val versionCode = update.info.versionCode
        val abi = update.abi
        val variant = update.variant
        val dir = cacheRoot().apply { mkdirs() }
        val finalFile = File(dir, "musicfree-${versionCode}-${abi}.apk")
        val partFile = File(dir, "musicfree-${versionCode}-${abi}.apk.part")
        partFile.delete()
        finalFile.delete()

        for ((index, url) in variant.download.withIndex()) {
            val outcome = tryDownload(
                variantSize = variant.size,
                variantSha256 = variant.sha256,
                url = url,
                target = partFile,
                onProgress = onProgress,
            )
            when (outcome) {
                is StepOutcome.Ok -> {
                    if (!partFile.renameTo(finalFile)) {
                        partFile.delete()
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "apk_download_failed",
                            fields = mapOf(
                                "cause" to UpdateError.Network.name,
                                "versionCode" to versionCode,
                                "abi" to abi,
                                "reason" to "rename_failed",
                            ),
                        )
                        return@withContext ApkDownloader.Result.Failure(UpdateError.Network)
                    }
                    MfLog.detail(
                        category = LogCategory.UPDATE,
                        event = "apk_download_complete",
                        fields = mapOf(
                            "versionCode" to versionCode,
                            "abi" to abi,
                            "bytes" to variant.size,
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
                            "versionCode" to versionCode,
                            "abi" to abi,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                        ),
                    )
                    return@withContext ApkDownloader.Result.Failure(outcome.cause)
                }
                is StepOutcome.SoftFail -> {
                    partFile.delete()
                    if (index == variant.download.lastIndex) {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "apk_download_failed",
                            fields = mapOf(
                                "cause" to UpdateError.Network.name,
                                "versionCode" to versionCode,
                                "abi" to abi,
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
        variantSize: Long,
        variantSha256: String,
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
                val body = response.body
                val advertised = body.contentLength()
                if (advertised >= 0 && advertised != variantSize) {
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
                            val fraction = if (variantSize > 0) (written.toFloat() / variantSize) else 0f
                            onProgress(written, variantSize, fraction.coerceIn(0f, 1f))
                        }
                    }
                }
                if (written != variantSize) return StepOutcome.HardFail(UpdateError.SizeMismatch)
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(variantSha256, ignoreCase = true)) {
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
