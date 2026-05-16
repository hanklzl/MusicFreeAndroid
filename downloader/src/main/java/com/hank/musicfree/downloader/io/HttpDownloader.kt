package com.hank.musicfree.downloader.io

import java.io.File

data class HttpDownloadProgress(val downloaded: Long, val total: Long)

class HttpDownloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface HttpDownloader {
    /**
     * Downloads [url] to [target]; emits progress through [onProgress].
     * Throws [HttpDownloadException] on non-2xx or IO failure.
     * Caller is responsible for cleaning up [target] on cancellation.
     */
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (HttpDownloadProgress) -> Unit,
    )
}
