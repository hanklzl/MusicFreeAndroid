package com.hank.musicfree.downloader

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.downloader.engine.DownloadEngine
import com.hank.musicfree.downloader.engine.DownloadEvent
import com.hank.musicfree.downloader.model.DownloadTaskUi
import com.hank.musicfree.downloader.model.MediaKey
import com.hank.musicfree.downloader.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloaderImpl @Inject constructor(
    private val engine: DownloadEngine,
    @ApplicationContext private val context: Context,
) : Downloader {

    init { engine.start() }

    override val tasks: StateFlow<List<DownloadTaskUi>> get() = engine.tasks
    override val downloadedKeys: StateFlow<Set<MediaKey>> get() = engine.downloadedKeys
    override val events: SharedFlow<DownloadEvent> get() = engine.events

    override fun enqueue(items: List<MusicItem>, quality: PlayQuality?) {
        engine.enqueue(items, quality)
        startServiceIfNeeded()
    }

    override fun cancel(key: MediaKey) = engine.cancel(key)
    override fun cancelAllInflight() = engine.cancelAllInflight()
    override fun retry(key: MediaKey) { engine.retry(key); startServiceIfNeeded() }
    override fun retryAllFailed() { engine.retryAllFailed(); startServiceIfNeeded() }
    override fun clearFailed() = engine.clearFailed()

    private fun startServiceIfNeeded() {
        val intent = Intent(context, DownloadService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }
}
