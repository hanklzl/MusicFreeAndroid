package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.logging.FeedbackLogExporterContract
import com.zili.android.musicfreeandroid.logging.FeedbackPackage
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.core.storage.DocumentTreeDirectory
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

data class StorageAccessState(
    val selectedDirectory: DocumentTreeDirectory? = null,
) {
    val isConfigured: Boolean
        get() = selectedDirectory != null
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val feedbackLogExporter: FeedbackLogExporterContract,
) : ViewModel() {

    val storageAccessState: StateFlow<StorageAccessState> = appPreferences.storageDirectoryUri
        .map { uri -> StorageAccessState(uri?.let(DocumentTreeDirectory::fromTreeUri)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageAccessState())

    private val _feedbackPackage = MutableSharedFlow<FeedbackPackage>(extraBufferCapacity = 1)
    val feedbackPackage: SharedFlow<FeedbackPackage> = _feedbackPackage.asSharedFlow()

    fun setStorageDirectory(treeUri: String) {
        viewModelScope.launch {
            appPreferences.setStorageDirectoryUri(treeUri)
        }
    }

    fun createFeedbackPackage() {
        viewModelScope.launch {
            try {
                _feedbackPackage.emit(feedbackLogExporter.createPackage())
            } catch (error: Throwable) {
                MfLog.error(LogCategory.FEEDBACK, "feedback_package_create_failed", error)
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                feedbackLogExporter.clearLogs()
            } catch (error: Throwable) {
                MfLog.error(LogCategory.FEEDBACK, "feedback_logs_clear_failed", error)
            }
        }
    }
}
