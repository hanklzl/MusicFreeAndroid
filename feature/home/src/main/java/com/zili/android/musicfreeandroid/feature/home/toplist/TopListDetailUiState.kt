package com.zili.android.musicfreeandroid.feature.home.toplist

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

data class TopListDetailUiState(
    val title: String = "榜单详情",
    val topListItem: MusicSheetItemBase? = null,
    val musicList: List<MusicItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val errorMessage: String? = null,
)
