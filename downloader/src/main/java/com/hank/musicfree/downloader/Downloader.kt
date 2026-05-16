package com.hank.musicfree.downloader

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.downloader.engine.DownloadEvent
import com.hank.musicfree.downloader.model.DownloadTaskUi
import com.hank.musicfree.downloader.model.MediaKey
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface Downloader {
    val tasks: StateFlow<List<DownloadTaskUi>>
    val downloadedKeys: StateFlow<Set<MediaKey>>
    val events: SharedFlow<DownloadEvent>

    fun enqueue(items: List<MusicItem>, quality: PlayQuality? = null)
    fun cancel(key: MediaKey)
    fun cancelAllInflight()
    fun retry(key: MediaKey)
    fun retryAllFailed()
    fun clearFailed()
}
