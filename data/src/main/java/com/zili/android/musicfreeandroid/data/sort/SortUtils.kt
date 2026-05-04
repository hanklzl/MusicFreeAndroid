package com.zili.android.musicfreeandroid.data.sort

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.SortMode
import java.text.Collator
import java.util.Locale

/**
 * Applies the given [SortMode] to a list of [MusicItem]s.
 *
 * [SortMode.Manual] preserves the existing order (as stored by sortOrder in the cross-ref table).
 * Title/Artist/Album use [Collator] for proper Chinese pinyin ordering.
 * Newest/Oldest order by [MusicItem.addedAt].
 *
 * Note: [Collator] is not thread-safe, so we instantiate a fresh one per sort to avoid
 * data races when this is invoked concurrently from multiple Flow collectors.
 */
fun List<MusicItem>.applySort(mode: SortMode): List<MusicItem> = when (mode) {
    SortMode.Manual -> this
    SortMode.Title -> {
        val collator = Collator.getInstance(Locale.CHINESE)
        sortedWith(compareBy(collator) { it.title })
    }
    SortMode.Artist -> {
        val collator = Collator.getInstance(Locale.CHINESE)
        sortedWith(compareBy(collator) { it.artist })
    }
    SortMode.Album -> {
        val collator = Collator.getInstance(Locale.CHINESE)
        sortedWith(compareBy(collator) { it.album.orEmpty() })
    }
    SortMode.Newest -> sortedByDescending { it.addedAt }
    SortMode.Oldest -> sortedBy { it.addedAt }
}
