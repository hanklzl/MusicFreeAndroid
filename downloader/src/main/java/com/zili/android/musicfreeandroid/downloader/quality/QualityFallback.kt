package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import com.zili.android.musicfreeandroid.core.model.fallbackSequence

typealias QualityResolver = suspend (MusicItem, qualityWire: String) -> MediaSourceResult?

object QualityFallback {

    private fun PlayQuality.wireName(): String = name.lowercase()

    suspend fun resolve(
        item: MusicItem,
        target: PlayQuality,
        order: QualityFallbackOrder = QualityFallbackOrder.Asc,
        resolver: QualityResolver,
    ): Pair<PlayQuality, MediaSourceResult>? {
        for (q in target.fallbackSequence(order)) {
            val r = runCatching { resolver(item, q.wireName()) }.getOrNull()
            if (r != null && r.url.isNotBlank()) {
                return q to r
            }
        }
        return null
    }
}
