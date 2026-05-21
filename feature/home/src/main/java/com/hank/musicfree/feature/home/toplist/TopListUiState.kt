package com.hank.musicfree.feature.home.toplist

import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiModel
import com.hank.musicfree.plugin.api.MusicSheetGroupItem

sealed interface TopListUiState {
    data object Idle : TopListUiState
    data object Loading : TopListUiState
    data class Success(val groups: List<MusicSheetGroupItem>) : TopListUiState
    data class Error(val message: String) : TopListUiState
}

data class TopListPagerUiState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginCapabilityUiModel> = emptyList(),
    val scenes: Map<String, TopListUiState> = emptyMap(),
) {
    fun selectedScene(): TopListUiState {
        val selectedPlatform = selectedPlatform ?: return TopListUiState.Error("当前没有支持榜单的插件")
        return scenes[selectedPlatform] ?: TopListUiState.Idle
    }
}
