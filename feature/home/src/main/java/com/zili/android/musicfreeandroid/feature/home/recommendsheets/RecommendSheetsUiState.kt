package com.zili.android.musicfreeandroid.feature.home.recommendsheets

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

data class RecommendTag(
    val id: String,
    val title: String,
    val payload: Map<String, Any?>,
)

data class RecommendSheetsUiState(
    val tags: List<RecommendTag> = emptyList(),
    val selectedTagId: String = "",
    val sheets: List<MusicSheetItemBase> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val errorMessage: String? = null,
)
