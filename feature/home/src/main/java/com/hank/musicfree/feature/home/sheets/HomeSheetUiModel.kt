package com.hank.musicfree.feature.home.sheets

import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.StarredKind
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.MusicSheetItemBase

data class HomeSheetUiModel(
    val id: String,
    val platform: String?,
    val tab: HomeSheetTab,
    val title: String,
    val subtitle: String,
    val coverUri: String?,
    val isDefault: Boolean = false,
    val kind: String = StarredKind.SHEET,
    val artist: String? = null,
    val sourceUrl: String? = null,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val raw: Map<String, Any?> = emptyMap(),
) {
    companion object {
        fun fromPlaylist(playlist: Playlist, musicCount: Int, isDefault: Boolean = false): HomeSheetUiModel = HomeSheetUiModel(
            id = playlist.id,
            platform = null,
            tab = HomeSheetTab.Mine,
            title = playlist.name,
            subtitle = "${musicCount}首",
            coverUri = playlist.coverUri,
            isDefault = isDefault,
        )

        fun fromStarredSheet(sheet: StarredSheet): HomeSheetUiModel = HomeSheetUiModel(
            id = sheet.id,
            platform = sheet.platform,
            tab = HomeSheetTab.Starred,
            title = sheet.title,
            subtitle = sheet.artist ?: sheet.platform,
            coverUri = sheet.coverUri,
            kind = sheet.kind,
            artist = sheet.artist,
            sourceUrl = sheet.sourceUrl,
            description = sheet.description,
            artwork = sheet.artwork,
            worksNum = sheet.worksNum,
            raw = sheet.raw,
        )
    }
}

fun HomeSheetUiModel.toMusicSheetItemBase(): MusicSheetItemBase {
    val platform = requireNotNull(platform) { "Starred sheet rows require a plugin platform" }
    val mergedRaw = raw.toMutableMap()
    sourceUrl?.let { mergedRaw.putIfAbsent("sourceUrl", it) }
    return MusicSheetItemBase(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        description = description,
        coverImg = coverUri,
        artwork = artwork,
        worksNum = worksNum,
        raw = mergedRaw,
    )
}

fun HomeSheetUiModel.toAlbumItemBase(): AlbumItemBase {
    val platform = requireNotNull(platform) { "Starred album rows require a plugin platform" }
    val mergedRaw = raw.toMutableMap()
    sourceUrl?.let { mergedRaw.putIfAbsent("sourceUrl", it) }
    return AlbumItemBase(
        id = id,
        platform = platform,
        title = title,
        date = raw["date"] as? String,
        artist = artist,
        description = description,
        artwork = artwork,
        worksNum = worksNum,
        raw = mergedRaw,
    )
}
