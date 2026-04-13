package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
}
