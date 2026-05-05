package com.zili.android.musicfreeandroid.feature.home.downloading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DownloadingUiState(
    val active: List<DownloadTaskUi>,
    val failed: List<DownloadTaskUi>,
)

@HiltViewModel
class DownloadingViewModel @Inject constructor(
    private val downloader: Downloader,
) : ViewModel() {

    val state: StateFlow<DownloadingUiState> = downloader.tasks
        .map { tasks ->
            DownloadingUiState(
                active = tasks.filter { it.status != DownloadStatus.FAILED },
                failed = tasks.filter { it.status == DownloadStatus.FAILED },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadingUiState(emptyList(), emptyList()))

    fun cancel(key: MediaKey) = downloader.cancel(key)
    fun retry(key: MediaKey) = downloader.retry(key)
    fun retryAllFailed() = downloader.retryAllFailed()
    fun clearFailed() = downloader.clearFailed()
    fun cancelAllInflight() = downloader.cancelAllInflight()
}
