package com.hank.musicfree.feature.home.recommendsheets

import com.hank.musicfree.plugin.api.MusicSheetItemBase

data class RecommendTag(
    val id: String,
    val title: String,
    val payload: Map<String, Any?>,
)

data class RecommendSheetsUiState(
    val tags: List<RecommendTag> = emptyList(),
    val selectedTagId: String? = null,
    val sheets: List<MusicSheetItemBase> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = true,
    val errorMessage: String? = null,
    val emptyMessage: String? = null,
)
