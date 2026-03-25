package com.zili.android.musicfreeandroid.feature.home.sheets

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.StarredSheet

data class HomeSheetUiModel(
    val id: String,
    val tab: HomeSheetTab,
    val title: String,
    val subtitle: String,
    val coverUri: String?,
) {
    companion object {
        fun fromPlaylist(playlist: Playlist, musicCount: Int): HomeSheetUiModel = HomeSheetUiModel(
            id = playlist.id,
            tab = HomeSheetTab.Mine,
            title = playlist.name,
            subtitle = "${musicCount} 首歌曲",
            coverUri = playlist.coverUri,
        )

        fun fromStarredSheet(sheet: StarredSheet): HomeSheetUiModel = HomeSheetUiModel(
            id = "${sheet.platform}:${sheet.id}",
            tab = HomeSheetTab.Starred,
            title = sheet.title,
            subtitle = sheet.artist ?: sheet.platform,
            coverUri = sheet.coverUri,
        )
    }
}
