package com.zili.android.musicfreeandroid.plugin.manager

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.AlbumInfoResult
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistWorksResult
import com.zili.android.musicfreeandroid.plugin.api.LyricResult
import com.zili.android.musicfreeandroid.plugin.api.MusicComment
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetGroupItem
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetInfoResult
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PaginationResult
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.RecommendSheetTagsResult
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.api.TopListDetailResult
import com.zili.android.musicfreeandroid.plugin.local.LocalFilePlugin

/**
 * In-process Kotlin-backed [LoadedPlugin] that adapts [LocalFilePlugin] (the
 * built-in "本地" plugin) to [com.zili.android.musicfreeandroid.plugin.api.PluginApi].
 *
 * Only `getMediaSource` / `getMusicInfo` / `getLyric` / `importMusicItem` are
 * supported; the remaining PluginApi methods return empty / null no-ops so this
 * plugin can sit safely inside the shared `LoadedPlugin` list without crashing
 * any consumer that iterates over all plugins.
 *
 * Lifecycle: there is no QuickJS engine to close and no user-configurable
 * variables, so [destroy] and [updateUserVariables] are no-ops. The plugin is
 * not produced by the install pipeline, so [filePath] and [installSource] are
 * always null — `PluginManager.uninstall("本地")` must defensively short-circuit.
 */
class LocalLoadedPlugin(
    override val info: PluginInfo,
    private val delegate: LocalFilePlugin,
) : LoadedPlugin {
    override val filePath: String? = null
    override val installSource: PluginInstallSource? = null

    override suspend fun search(query: String, page: Int, type: String): SearchResult =
        SearchResult(isEnd = true, data = emptyList())

    override suspend fun getMediaSource(musicItem: MusicItem, quality: String): MediaSourceResult? =
        delegate.getMediaSource(musicItem, quality)

    override suspend fun getMusicInfo(musicItem: MusicItem): MusicItem? =
        delegate.getMusicInfo(musicItem)

    override suspend fun getLyric(musicItem: MusicItem): LyricResult? =
        delegate.getLyric(musicItem)?.let { local ->
            LyricResult(
                rawLrc = local.rawLrc,
                rawLrcTxt = null,
                translation = null,
                lines = emptyList(),
            )
        }

    override suspend fun getAlbumInfo(albumItem: AlbumItemBase, page: Int): AlbumInfoResult? = null

    override suspend fun getArtistWorks(
        artistItem: ArtistItemBase,
        page: Int,
        type: String,
    ): ArtistWorksResult? = null

    override suspend fun importMusicSheet(urlLike: String): List<MusicItem>? = null

    override suspend fun importMusicItem(urlLike: String): MusicItem? =
        delegate.importMusicItem(urlLike)

    override suspend fun getTopLists(): List<MusicSheetGroupItem> = emptyList()

    override suspend fun getTopListDetail(
        topListItem: MusicSheetItemBase,
        page: Int,
    ): TopListDetailResult? = null

    override suspend fun getMusicSheetInfo(
        sheetItem: MusicSheetItemBase,
        page: Int,
    ): MusicSheetInfoResult? = null

    override suspend fun getRecommendSheetTags(): RecommendSheetTagsResult? = null

    override suspend fun getRecommendSheetsByTag(
        tag: Map<String, Any?>,
        page: Int,
    ): PaginationResult<MusicSheetItemBase>? = null

    override suspend fun getMusicComments(
        musicItem: MusicItem,
        page: Int,
    ): PaginationResult<MusicComment>? = null

    override suspend fun updateUserVariables(values: Map<String, String>) {
        // 本地 plugin has no user variables — silently ignore.
    }

    override suspend fun destroy() {
        // 本地 plugin has no JS engine or external resources to release.
    }
}
