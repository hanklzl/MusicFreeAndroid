package com.hank.musicfree.feature.home.toplist

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.MusicSheetItemBase

data class TopListDetailUiState(
    val title: String = "榜单详情",
    val topListItem: MusicSheetItemBase? = null,
    val musicList: List<MusicItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val errorMessage: String? = null,
)
