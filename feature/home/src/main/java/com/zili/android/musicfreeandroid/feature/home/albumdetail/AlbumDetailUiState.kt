package com.zili.android.musicfreeandroid.feature.home.albumdetail

import com.zili.android.musicfreeandroid.core.model.MusicItem

data class AlbumDetailUiState(
    val title: String = "专辑详情",
    val loading: Boolean = true,
    val musicList: List<MusicItem> = emptyList(),
    val isEnd: Boolean = true,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)
