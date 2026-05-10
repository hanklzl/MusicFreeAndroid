package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality

typealias QualityResolver = suspend (MusicItem, qualityWire: String) -> MediaSourceResult?

object QualityFallback {

    private val DESC = listOf(PlayQuality.SUPER, PlayQuality.HIGH, PlayQuality.STANDARD, PlayQuality.LOW)

    private fun PlayQuality.wireName(): String = name.lowercase()

    suspend fun resolve(
        item: MusicItem,
        target: PlayQuality,
        resolver: QualityResolver,
    ): Pair<PlayQuality, MediaSourceResult>? {
        val startIdx = DESC.indexOf(target).coerceAtLeast(0)
        for (q in DESC.subList(startIdx, DESC.size)) {
            val r = runCatching { resolver(item, q.wireName()) }.getOrNull()
            if (r != null && r.url.isNotBlank()) {
                return q to r
            }
        }
        return null
    }
}
