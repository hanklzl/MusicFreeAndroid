package com.hank.musicfree.feature.home.musiclisteditor

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist

data class MusicListEditorLiteUiState(
    val playlistName: String = "",
    val items: List<MusicItem> = emptyList(),
    val selectedItemKeys: Set<String> = emptySet(),
    val selectedCount: Int = 0,
    val hasPendingChanges: Boolean = false,
    val availableTargetPlaylists: List<Playlist> = emptyList(),
)
