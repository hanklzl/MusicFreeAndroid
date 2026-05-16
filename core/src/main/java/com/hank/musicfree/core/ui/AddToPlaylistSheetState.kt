package com.hank.musicfree.core.ui

import com.hank.musicfree.core.model.MusicItem

/**
 * UI state for the "Add to playlist" bottom sheet, shared by all surfaces that
 * trigger it. Single-song callers use [single]; playlist import uses [batch].
 */
data class AddToPlaylistSheetState(
    val visible: Boolean = false,
    val pendingItems: List<MusicItem> = emptyList(),
) {
    val pendingItem: MusicItem?
        get() = pendingItems.singleOrNull()

    companion object {
        fun single(item: MusicItem): AddToPlaylistSheetState =
            AddToPlaylistSheetState(visible = true, pendingItems = listOf(item))

        fun batch(items: List<MusicItem>): AddToPlaylistSheetState =
            AddToPlaylistSheetState(visible = items.isNotEmpty(), pendingItems = items)
    }
}
