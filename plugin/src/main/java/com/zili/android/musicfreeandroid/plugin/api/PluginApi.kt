package com.zili.android.musicfreeandroid.plugin.api

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem

interface PluginApi {
    val info: PluginInfo
    suspend fun search(query: String, page: Int, type: String = "music"): SearchResult
    suspend fun getMediaSource(musicItem: MusicItem, quality: String = "standard"): MediaSourceResult?
    suspend fun getMusicInfo(musicItem: MusicItem): MusicItem?
    suspend fun getLyric(musicItem: MusicItem): LyricResult?
    suspend fun getAlbumInfo(albumItem: AlbumItemBase, page: Int): AlbumInfoResult?
    suspend fun getArtistWorks(artistItem: ArtistItemBase, page: Int, type: String = "music"): ArtistWorksResult?
    suspend fun importMusicSheet(urlLike: String): List<MusicItem>?
    suspend fun importMusicItem(urlLike: String): MusicItem?
    suspend fun getTopLists(): List<MusicSheetGroupItem>
    suspend fun getTopListDetail(topListItem: MusicSheetItemBase, page: Int): TopListDetailResult?
    suspend fun getMusicSheetInfo(sheetItem: MusicSheetItemBase, page: Int): MusicSheetInfoResult?
    suspend fun getRecommendSheetTags(): RecommendSheetTagsResult?
    suspend fun getRecommendSheetsByTag(
        tag: Map<String, Any?>,
        page: Int = 1,
    ): PaginationResult<MusicSheetItemBase>?
    suspend fun getMusicComments(musicItem: MusicItem, page: Int = 1): PaginationResult<MusicComment>?
}
