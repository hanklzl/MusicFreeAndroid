package com.zili.android.musicfreeandroid.data.sort

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.SortMode
import java.text.Collator
import java.util.Locale

private val chineseCollator: Collator = Collator.getInstance(Locale.CHINESE)

/**
 * Applies the given [SortMode] to a list of [MusicItem]s.
 *
 * [SortMode.Manual] preserves the existing order (as stored by sortOrder in the cross-ref table).
 * Title/Artist/Album use [Collator] for proper Chinese pinyin ordering.
 * Newest/Oldest order by [MusicItem.addedAt].
 */
fun List<MusicItem>.applySort(mode: SortMode): List<MusicItem> = when (mode) {
    SortMode.Manual -> this
    SortMode.Title -> sortedWith(compareBy(chineseCollator) { it.title })
    SortMode.Artist -> sortedWith(compareBy(chineseCollator) { it.artist })
    SortMode.Album -> sortedWith(compareBy(chineseCollator) { it.album.orEmpty() })
    SortMode.Newest -> sortedByDescending { it.addedAt }
    SortMode.Oldest -> sortedBy { it.addedAt }
}
