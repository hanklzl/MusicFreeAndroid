package com.hank.musicfree.feature.home.musicdetail

import com.hank.musicfree.core.model.LyricLine
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.MusicComment

data class MusicDetailUiState(
    val loading: Boolean = true,
    val musicItem: MusicItem? = null,
    val lyricLines: List<LyricLine> = emptyList(),
    val comments: List<MusicComment> = emptyList(),
    val commentsIsEnd: Boolean = true,
    val albumPreviewCount: Int? = null,
    val artistPreviewCount: Int? = null,
    val errorMessage: String? = null,
)
