package com.zili.android.musicfreeandroid.feature.home.downloading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
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

    fun cancel(key: MediaKey) {
        logDownloadIntent("cancel", key)
        downloader.cancel(key)
    }

    fun retry(key: MediaKey) {
        logDownloadIntent("retry", key)
        downloader.retry(key)
    }

    fun retryAllFailed() {
        logDownloadIntent("retry_all_failed", count = state.value.failed.size)
        downloader.retryAllFailed()
    }

    fun clearFailed() {
        logDownloadIntent("clear_failed", count = state.value.failed.size)
        downloader.clearFailed()
    }

    fun cancelAllInflight() {
        logDownloadIntent("cancel_all_inflight", count = state.value.active.size)
        downloader.cancelAllInflight()
    }

    private fun logDownloadIntent(
        operation: String,
        key: MediaKey? = null,
        count: Int? = null,
    ) {
        MfLog.detail(
            LogCategory.DOWNLOAD,
            "download_intent",
            mapOf(
                "screen" to "downloading",
                "operation" to operation,
                "platform" to key?.platform.orEmpty(),
                "itemId" to key?.id.orEmpty(),
                "count" to (count ?: 1),
                "result" to LogFields.Result.SUCCESS,
            ),
        )
    }
}
