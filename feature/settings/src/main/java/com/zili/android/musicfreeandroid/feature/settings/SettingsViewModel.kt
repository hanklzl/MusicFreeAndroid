package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.storage.DocumentTreeDirectory
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.logging.FeedbackLogExporterContract
import com.zili.android.musicfreeandroid.logging.FeedbackPackage
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

data class BasicSettingsUiState(
    val maxDownload: Int = 3,
    val defaultDownloadQuality: PlayQuality = PlayQuality.STANDARD,
    val useCellularDownload: Boolean = false,
    val lyricAutoSearchEnabled: Boolean = true,
    val storageAccessState: StorageAccessState = StorageAccessState(),
)

data class StorageAccessState(
    val selectedDirectory: DocumentTreeDirectory? = null,
) {
    val isConfigured: Boolean
        get() = selectedDirectory != null
}

data class FeedbackExportUiState(
    val isExporting: Boolean = false,
    val isClearing: Boolean = false,
    val pendingPackage: FeedbackPackage? = null,
    val errorMessage: String? = null,
) {
    val isOperationInProgress: Boolean
        get() = isExporting || isClearing
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val feedbackLogExporter: FeedbackLogExporterContract,
) : ViewModel() {

    val storageAccessState: StateFlow<StorageAccessState> = appPreferences.storageDirectoryUri
        .map { uri -> StorageAccessState(uri?.let(DocumentTreeDirectory::fromTreeUri)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageAccessState())

    private val feedbackActionLock = Mutex()

    private val _feedbackExportUiState = MutableStateFlow(FeedbackExportUiState())
    val feedbackExportUiState: StateFlow<FeedbackExportUiState> = _feedbackExportUiState.asStateFlow()

    fun setStorageDirectory(treeUri: String) {
        viewModelScope.launch {
            appPreferences.setStorageDirectoryUri(treeUri)
        }
    }

    val maxDownload: StateFlow<Int> = appPreferences.maxDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val useCellularDownload: StateFlow<Boolean> = appPreferences.useCellularDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultDownloadQuality: StateFlow<PlayQuality> = appPreferences.defaultDownloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    val lyricAutoSearchEnabled: StateFlow<Boolean> = appPreferences.lyricAutoSearchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val basicSettingsUiState: StateFlow<BasicSettingsUiState> = combine(
        maxDownload,
        defaultDownloadQuality,
        useCellularDownload,
        lyricAutoSearchEnabled,
        storageAccessState,
    ) { maxDownload, defaultDownloadQuality, useCellularDownload, lyricAutoSearchEnabled, storageAccessState ->
        BasicSettingsUiState(
            maxDownload = maxDownload,
            defaultDownloadQuality = defaultDownloadQuality,
            useCellularDownload = useCellularDownload,
            lyricAutoSearchEnabled = lyricAutoSearchEnabled,
            storageAccessState = storageAccessState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BasicSettingsUiState())

    fun setMaxDownload(value: Int) = viewModelScope.launch {
        appPreferences.setMaxDownload(value)
    }

    fun setUseCellularDownload(value: Boolean) = viewModelScope.launch {
        appPreferences.setUseCellularDownload(value)
    }

    fun setDefaultDownloadQuality(quality: PlayQuality) = viewModelScope.launch {
        appPreferences.setDefaultDownloadQuality(quality)
    }

    fun setLyricAutoSearchEnabled(value: Boolean) = viewModelScope.launch {
        appPreferences.setLyricAutoSearchEnabled(value)
    }

    fun createFeedbackPackage() {
        viewModelScope.launch {
            if (!feedbackActionLock.tryLock()) {
                return@launch
            }
            try {
                _feedbackExportUiState.update {
                    it.copy(
                        isExporting = true,
                        isClearing = false,
                        errorMessage = null,
                        pendingPackage = null,
                    )
                }
                val feedbackPackage = feedbackLogExporter.createPackage()
                _feedbackExportUiState.update { it.copy(pendingPackage = feedbackPackage) }
            } catch (error: Throwable) {
                MfLog.error(LogCategory.FEEDBACK, "feedback_package_create_failed", error)
                _feedbackExportUiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "生成日志包失败")
                }
            } finally {
                _feedbackExportUiState.update { it.copy(isExporting = false) }
                feedbackActionLock.unlock()
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            if (!feedbackActionLock.tryLock()) {
                return@launch
            }
            try {
                _feedbackExportUiState.update {
                    it.copy(
                        isClearing = true,
                        isExporting = false,
                        errorMessage = null,
                    )
                }
                feedbackLogExporter.clearLogs()
            } catch (error: Throwable) {
                MfLog.error(LogCategory.FEEDBACK, "feedback_logs_clear_failed", error)
                _feedbackExportUiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "清空日志失败")
                }
            } finally {
                _feedbackExportUiState.update { it.copy(isClearing = false) }
                feedbackActionLock.unlock()
            }
        }
    }

    fun onFeedbackPackageShared() {
        _feedbackExportUiState.update { it.copy(pendingPackage = null) }
    }

    fun onFeedbackExportError(errorMessage: String) {
        _feedbackExportUiState.update { it.copy(errorMessage = errorMessage) }
    }

    fun clearFeedbackError() {
        _feedbackExportUiState.update { it.copy(errorMessage = null) }
    }
}
