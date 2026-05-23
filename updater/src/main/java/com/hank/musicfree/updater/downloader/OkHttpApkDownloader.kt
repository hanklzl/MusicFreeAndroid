package com.hank.musicfree.updater.downloader

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.ResolvedUpdate
import com.hank.musicfree.updater.checker.UpdateError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        cleanupStaleFiles(dir, finalFile.name, partFile.name)

        if (finalFile.exists()) {
            if (isExpectedApk(finalFile, variant.size, variant.sha256)) {
                onProgress(variant.size, variant.size, 1f)
                MfLog.detail(
                    category = LogCategory.UPDATE,
                    event = "apk_download_cache_hit",
                    fields = mapOf(
                        "versionCode" to versionCode,
                        "abi" to abi,
                        "bytes" to variant.size,
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
                return@withContext ApkDownloader.Result.Success(finalFile, fromCache = true)
            }
            finalFile.delete()
        }

        if (partFile.exists() && partFile.length() == variant.size) {
            if (isExpectedApk(partFile, variant.size, variant.sha256) && partFile.renameTo(finalFile)) {
                onProgress(variant.size, variant.size, 1f)
                return@withContext ApkDownloader.Result.Success(
                    finalFile,
                    fromCache = true,
                    resumedFromBytes = variant.size,
                )
            }
            partFile.delete()
        } else if (partFile.length() > variant.size) {
            partFile.delete()
        }

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
                            "resumedFromBytes" to outcome.resumedFromBytes,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                    return@withContext ApkDownloader.Result.Success(
                        finalFile,
                        resumedFromBytes = outcome.resumedFromBytes,
                    )
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
                    return@withContext ApkDownloader.Result.Failure(UpdateError.Canceled)
                }
                StepOutcome.Restart -> {
                    // Restart is consumed inside tryDownload(); seeing it here means this mirror is unusable.
                    if (index == variant.download.lastIndex) {
                        return@withContext ApkDownloader.Result.Failure(UpdateError.Network)
                    }
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
        var resumedFromBytes = resumableLength(target, variantSize)
        var retriedAfterInvalidRange = false
        while (true) {
            val outcome = tryDownloadOnce(
                variantSize = variantSize,
                variantSha256 = variantSha256,
                url = url,
                target = target,
                resumedFromBytes = resumedFromBytes,
                onProgress = onProgress,
            )
            if (outcome is StepOutcome.Restart && !retriedAfterInvalidRange) {
                target.delete()
                resumedFromBytes = 0L
                retriedAfterInvalidRange = true
                continue
            }
            return outcome
        }
    }

    private fun tryDownloadOnce(
        variantSize: Long,
        variantSha256: String,
        url: String,
        target: File,
        resumedFromBytes: Long,
        onProgress: (Long, Long, Float) -> Unit,
    ): StepOutcome {
        val requestBuilder = Request.Builder().url(url).get()
        if (resumedFromBytes > 0L) {
            requestBuilder.header("Range", "bytes=$resumedFromBytes-")
        }
        val request = requestBuilder.build()
        val call = http.newCall(request)
        currentCall.set(call)
        return try {
            call.execute().use { response ->
                if (response.code == 416 && resumedFromBytes > 0L) {
                    return StepOutcome.Restart
                }
                if (!response.isSuccessful) return StepOutcome.SoftFail
                val body = response.body
                val append = resumedFromBytes > 0L && response.code == 206
                if (resumedFromBytes > 0L && !append) {
                    target.delete()
                }
                val startingBytes = if (append) resumedFromBytes else 0L
                val advertised = body.contentLength()
                val expectedResponseBytes = (variantSize - startingBytes).coerceAtLeast(0L)
                if (advertised >= 0 && advertised != expectedResponseBytes) {
                    return StepOutcome.HardFail(UpdateError.SizeMismatch)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                if (append) {
                    target.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            digest.update(buf, 0, n)
                        }
                    }
                }
                var written = startingBytes
                onProgress(written, variantSize, progressFraction(written, variantSize))
                FileOutputStream(target, append).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            written += n
                            onProgress(written, variantSize, progressFraction(written, variantSize))
                        }
                    }
                }
                if (written != variantSize) return StepOutcome.HardFail(UpdateError.SizeMismatch)
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(variantSha256, ignoreCase = true)) {
                    return StepOutcome.HardFail(UpdateError.Sha256Mismatch)
                }
                StepOutcome.Ok(resumedFromBytes = startingBytes)
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

    private fun resumableLength(file: File, expectedSize: Long): Long {
        val length = file.takeIf { it.exists() }?.length() ?: 0L
        return if (length in 1 until expectedSize) length else 0L
    }

    private fun isExpectedApk(file: File, expectedSize: Long, expectedSha256: String): Boolean {
        if (!file.exists() || file.length() != expectedSize) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private fun progressFraction(bytes: Long, total: Long): Float =
        if (total > 0L) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f

    private fun cleanupStaleFiles(dir: File, finalName: String, partName: String) {
        dir.listFiles()
            ?.filter { it.isFile && it.name != finalName && it.name != partName }
            ?.forEach { it.delete() }
    }

    private sealed interface StepOutcome {
        data class Ok(val resumedFromBytes: Long) : StepOutcome
        data object SoftFail : StepOutcome
        data class HardFail(val cause: UpdateError) : StepOutcome
        data object Canceled : StepOutcome
        data object Restart : StepOutcome
    }
}
