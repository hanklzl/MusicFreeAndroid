package com.zili.android.musicfreeandroid.feature.home.toplist

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetGroupItem

sealed interface TopListUiState {
    data object Idle : TopListUiState
    data object Loading : TopListUiState
    data class Success(val groups: List<MusicSheetGroupItem>) : TopListUiState
    data class Error(val message: String) : TopListUiState
}
