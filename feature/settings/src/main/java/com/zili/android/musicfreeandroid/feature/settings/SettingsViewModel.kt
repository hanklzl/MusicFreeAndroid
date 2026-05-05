package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.storage.DocumentTreeDirectory
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    val storageAccessState: StateFlow<StorageAccessState> = appPreferences.storageDirectoryUri
        .map { uri -> StorageAccessState(uri?.let(DocumentTreeDirectory::fromTreeUri)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageAccessState())

    fun setStorageDirectory(treeUri: String) {
        viewModelScope.launch {
            appPreferences.setStorageDirectoryUri(treeUri)
        }
    }

    // ── Download Settings ──

    val maxDownload: StateFlow<Int> = appPreferences.maxDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val useCellularDownload: StateFlow<Boolean> = appPreferences.useCellularDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultDownloadQuality: StateFlow<PlayQuality> = appPreferences.defaultDownloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    fun setMaxDownload(value: Int) = viewModelScope.launch { appPreferences.setMaxDownload(value) }
    fun setUseCellularDownload(v: Boolean) = viewModelScope.launch { appPreferences.setUseCellularDownload(v) }
    fun setDefaultDownloadQuality(q: PlayQuality) = viewModelScope.launch { appPreferences.setDefaultDownloadQuality(q) }
}
