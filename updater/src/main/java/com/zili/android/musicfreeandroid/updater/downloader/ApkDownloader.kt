package com.zili.android.musicfreeandroid.updater.downloader

import com.zili.android.musicfreeandroid.updater.checker.ResolvedUpdate
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import java.io.File

interface ApkDownloader {

    sealed interface Result {
        data class Success(val apkFile: File) : Result
        data class Failure(val cause: UpdateError) : Result
    }

    /**
     * progress 回调签名 (bytes, total, fraction)；fraction ∈ [0,1]，total≤0 时 fraction 取 0。
     */
    suspend fun download(
        update: ResolvedUpdate,
        onProgress: (bytes: Long, total: Long, fraction: Float) -> Unit,
    ): Result

    fun cancel()
}
