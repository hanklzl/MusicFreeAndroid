package com.zili.android.musicfreeandroid.feature.home.artistdetail

import com.zili.android.musicfreeandroid.core.model.MusicItem

data class ArtistDetailUiState(
    val title: String = "歌手详情",
    val loading: Boolean = true,
    val musicList: List<MusicItem> = emptyList(),
    val isEnd: Boolean = true,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)
