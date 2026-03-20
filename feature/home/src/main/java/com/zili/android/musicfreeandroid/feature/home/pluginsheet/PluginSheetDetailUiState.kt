package com.zili.android.musicfreeandroid.feature.home.pluginsheet

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

data class PluginSheetDetailUiState(
    val title: String = "歌单详情",
    val sheetItem: MusicSheetItemBase? = null,
    val musicList: List<MusicItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val errorMessage: String? = null,
)
