package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PermissionsUiState(
    val overlayGranted: Boolean = false,
    val storageAudioGranted: Boolean = false,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    fun updateUiState(uiState: PermissionsUiState) {
        _uiState.value = uiState
    }
}
