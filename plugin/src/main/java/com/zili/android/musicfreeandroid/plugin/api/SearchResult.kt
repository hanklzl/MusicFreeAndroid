package com.zili.android.musicfreeandroid.plugin.api

import com.zili.android.musicfreeandroid.core.model.MusicItem

data class SearchResult(
    val isEnd: Boolean,
    val data: List<MusicItem>,
)
