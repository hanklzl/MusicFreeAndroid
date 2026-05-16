package com.hank.musicfree.downloader.quality

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.model.fallbackSequence

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
