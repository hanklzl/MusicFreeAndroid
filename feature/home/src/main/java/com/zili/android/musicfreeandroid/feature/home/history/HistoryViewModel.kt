package com.zili.android.musicfreeandroid.feature.home.history

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val playerController: PlayerController,
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
}

