package com.zili.android.musicfreeandroid.feature.home.musicdetail

import com.zili.android.musicfreeandroid.core.model.LyricLine
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.MusicComment

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
