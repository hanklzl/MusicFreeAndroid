package com.hank.musicfree.feature.home.history

import androidx.lifecycle.ViewModel
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {

    val history: StateFlow<List<MusicItem>> = playerController.playHistory

    fun clearHistory() {
        playerController.clearHistory()
    }

    fun playAt(index: Int): Boolean {
        val list = history.value
        if (index !in list.indices) return false
        playerController.playQueue(list, index)
        return true
    }

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }
}

