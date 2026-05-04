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

data class AlbumItemBase(
    val id: String,
    val platform: String,
    val title: String?,
    val date: String?,
    val artist: String?,
    val description: String?,
    val artwork: String?,
    val worksNum: Int?,
    val raw: Map<String, Any?>,
)

data class ArtistItemBase(
    val id: String,
    val platform: String,
    val name: String?,
    val avatar: String?,
    val fans: Int?,
    val description: String?,
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

data class AlbumInfoResult(
    val isEnd: Boolean,
    val albumItem: AlbumItemBase?,
    val musicList: List<MusicItem>,
)

data class ArtistWorksResult(
    val isEnd: Boolean,
    val type: String,
    val musicList: List<MusicItem>,
    val rawData: List<Map<String, Any?>>,
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
    val translation: String?,
    val lines: List<LyricLine>,
)

data class MusicComment(
    val id: String?,
    val nickName: String,
    val avatar: String?,
    val comment: String,
    val likeCount: Int?,
    val createAt: Long?,
    val location: String?,
    val replies: List<MusicComment>,
    val raw: Map<String, Any?>,
)
