package com.zili.android.musicfreeandroid.feature.search

import com.zili.android.musicfreeandroid.core.model.MusicItem

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val items: List<MusicItem>,
        val isEnd: Boolean,
        val query: String,
        val page: Int,
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
