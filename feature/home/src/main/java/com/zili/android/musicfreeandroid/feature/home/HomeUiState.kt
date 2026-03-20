package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.model.MusicItem

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val musicItems: List<MusicItem>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
