package com.zili.android.musicfreeandroid.plugin.api

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.LyricLine

data class MusicSheetItemBase(
    val id: String,
    val platform: String,
    val title: String?,
    val artist: String?,
    val description: String?,
    val coverImg: String?,
    val artwork: String?,
    val worksNum: Int?,
    val raw: Map<String, Any?>,
)

data class MusicSheetGroupItem(
    val title: String?,
    val data: List<MusicSheetItemBase>,
)

data class TopListDetailResult(
    val isEnd: Boolean,
    val topListItem: MusicSheetItemBase?,
    val musicList: List<MusicItem>,
)

data class MusicSheetInfoResult(
    val isEnd: Boolean,
    val sheetItem: MusicSheetItemBase?,
    val musicList: List<MusicItem>,
)

data class RecommendSheetTagsResult(
    val pinned: List<MusicSheetItemBase>,
    val data: List<MusicSheetGroupItem>,
)

data class PaginationResult<T>(
    val isEnd: Boolean,
    val data: List<T>,
)

data class LyricResult(
    val rawLrc: String?,
    val rawLrcTxt: String?,
    val lines: List<LyricLine>,
)
