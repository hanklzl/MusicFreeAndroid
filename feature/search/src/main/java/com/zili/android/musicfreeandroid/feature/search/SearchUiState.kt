package com.zili.android.musicfreeandroid.feature.search

import com.zili.android.musicfreeandroid.plugin.api.PluginSearchItem

/** 搜索页整体页面状态 */
enum class SearchPageStatus {
    /** 初始/编辑态：显示搜索历史 */
    EDITING,
    /** 正在搜索中（至少一个源在加载） */
    SEARCHING,
    /** 有结果展示 */
    RESULT,
    /** 没有可用插件 */
    NO_PLUGIN,
}

/** 单个插件在单个媒体类型下的搜索状态 */
sealed interface PluginSearchState {
    data object Idle : PluginSearchState
    data object Loading : PluginSearchState
    data class Success(
        val items: List<PluginSearchItem>,
        val isEnd: Boolean,
        val page: Int,
    ) : PluginSearchState
    data class Error(val message: String) : PluginSearchState
}

/** 媒体搜索类型，对齐 RN 的 supportedSearchType */
enum class SearchMediaType(val key: String, val label: String) {
    MUSIC("music", "单曲"),
    ALBUM("album", "专辑"),
    ARTIST("artist", "歌手"),
    SHEET("sheet", "歌单"),
}
