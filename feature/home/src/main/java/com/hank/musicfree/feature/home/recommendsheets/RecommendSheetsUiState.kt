package com.hank.musicfree.feature.home.recommendsheets

import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiModel

data class RecommendTag(
    val id: String,
    val title: String,
    val payload: Map<String, Any?>,
)

data class RecommendSheetsPagerUiState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginCapabilityUiModel> = emptyList(),
    val scenes: Map<String, RecommendSheetsSceneState> = emptyMap(),
)

data class RecommendSheetsSceneState(
    val tags: List<RecommendTag> = emptyList(),
    val selectedTagId: String? = null,
    val sheets: List<MusicSheetItemBase> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loadingMorePage: Int? = null,
    val isEnd: Boolean = true,
    val errorMessage: String? = null,
    val emptyMessage: String? = null,
    val loaded: Boolean = false,
    val firstPageInFlight: Boolean = false,
)

typealias RecommendSheetsUiState = RecommendSheetsSceneState
