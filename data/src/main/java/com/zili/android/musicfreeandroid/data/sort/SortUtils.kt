package com.zili.android.musicfreeandroid.data.sort

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.SortMode

/**
 * Applies the given [SortMode] to a list of [MusicItem]s.
 *
 * [SortMode.Manual] preserves the existing order (as stored by sortOrder in the cross-ref table).
 * All other modes perform an in-memory sort on the retrieved list.
 */
fun List<MusicItem>.applySort(mode: SortMode): List<MusicItem> = when (mode) {
    SortMode.Manual -> this
    SortMode.Title -> sortedBy { it.title.lowercase() }
    SortMode.Artist -> sortedBy { it.artist.lowercase() }
    SortMode.Album -> sortedBy { it.album?.lowercase() ?: "" }
    SortMode.Newest -> sortedByDescending { it.addedAt }
    SortMode.Oldest -> sortedBy { it.addedAt }
}
