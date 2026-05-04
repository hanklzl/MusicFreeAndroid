package com.zili.android.musicfreeandroid.core.ui

import com.zili.android.musicfreeandroid.core.model.MusicItem

/**
 * UI state for the "Add to playlist" bottom sheet, shared by all surfaces that
 * trigger it (Playlist detail rows, Search results, Plugin sheet detail, Player).
 */
data class AddToPlaylistSheetState(
    val visible: Boolean = false,
    val pendingItem: MusicItem? = null,
)
