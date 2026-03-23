package com.zili.android.musicfreeandroid.feature.settings.fileselector

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

data class FileSelectorLiteUiState(
    val selectedDirectory: DocumentTreeDirectory? = null,
) {
    val isConfigured: Boolean
        get() = selectedDirectory != null
}

@HiltViewModel
class FileSelectorLiteViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<FileSelectorLiteUiState> = appPreferences.storageDirectoryUri
        .map { uri ->
            FileSelectorLiteUiState(
                selectedDirectory = uri?.let(DocumentTreeDirectory::fromTreeUri),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FileSelectorLiteUiState())

    fun onDirectorySelected(treeUri: String) {
        viewModelScope.launch {
            appPreferences.setStorageDirectoryUri(treeUri)
        }
    }
}
