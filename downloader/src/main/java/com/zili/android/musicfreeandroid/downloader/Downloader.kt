package com.zili.android.musicfreeandroid.downloader

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEvent
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
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
