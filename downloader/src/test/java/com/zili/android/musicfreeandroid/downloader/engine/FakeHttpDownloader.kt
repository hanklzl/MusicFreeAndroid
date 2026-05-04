package com.zili.android.musicfreeandroid.downloader.engine

import com.zili.android.musicfreeandroid.downloader.io.HttpDownloadProgress
import com.zili.android.musicfreeandroid.downloader.io.HttpDownloader
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class FakeHttpDownloader : HttpDownloader {
    private val _inflight = AtomicInteger(0)
    val inflight: Int get() = _inflight.get()

    // Holds gates that will block download calls in order.
    // All gates remain accessible so releaseAll() can complete them
    // even after individual download() calls have captured their reference.
    private val allGates = mutableListOf<CompletableDeferred<Unit>>()
    private val nextGateIndex = AtomicInteger(0)

    fun holdNextN(n: Int) {
        repeat(n) { allGates += CompletableDeferred() }
    }

    fun releaseAll() {
        allGates.forEach { it.complete(Unit) }
        allGates.clear()
        nextGateIndex.set(0)
    }

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (HttpDownloadProgress) -> Unit,
    ) {
        _inflight.incrementAndGet()
        // Claim a gate by index (does not remove from list, so releaseAll can still complete it)
        val idx = nextGateIndex.getAndIncrement()
        val gate: CompletableDeferred<Unit>? = if (idx < allGates.size) allGates[idx] else null
        try {
            target.parentFile?.mkdirs()
            target.writeBytes("ok".toByteArray())
            onProgress(HttpDownloadProgress(2, 2))
            gate?.await()
        } finally {
            _inflight.decrementAndGet()
        }
    }
}
