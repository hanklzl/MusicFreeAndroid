package com.hank.musicfree.feature.home.albumdetail

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.AlbumItemBase

data class AlbumDetailUiState(
    val title: String = "专辑详情",
    val albumItem: AlbumItemBase? = null,
    val loading: Boolean = true,
    val musicList: List<MusicItem> = emptyList(),
    val isEnd: Boolean = true,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)
