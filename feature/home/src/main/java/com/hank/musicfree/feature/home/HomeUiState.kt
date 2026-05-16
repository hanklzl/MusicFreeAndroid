package com.hank.musicfree.feature.home

import com.hank.musicfree.core.model.MusicItem

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val musicItems: List<MusicItem>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
