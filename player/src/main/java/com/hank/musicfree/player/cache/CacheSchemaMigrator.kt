package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache

/**
 * One-shot migrator for the media-cache key scheme change (Task 3).
 *
 * Legacy keys have the format `"<platform>:<id>"`. New keys append a
 * PlayQuality suffix: `"<platform>:<id>:<quality>"`. Legacy entries
 * cached under the old scheme must be evicted on first launch so the
 * new scheme starts clean — reading mismatched entries would skip the
 * quality path entirely.
 *
 * This object is intentionally pure (no Android deps, no Hilt) so it
 * can be tested with a plain JVM JUnit runner.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
object CacheSchemaMigrator {

    private val QUALITY_SUFFIXES = setOf("low", "standard", "high", "super", "unknown")

    /**
     * Returns true if [key] is a legacy key — i.e., it does NOT end with
     * one of the known quality suffixes (`:low|:standard|:high|:super|:unknown`).
     */
    fun isLegacyKey(key: String): Boolean {
        val lower = key.lowercase()
        return QUALITY_SUFFIXES.none { suffix -> lower.endsWith(":$suffix") }
    }

    data class Result(val removedCount: Int, val freedBytes: Long)

    /**
     * Walks all keys in [cache], removes legacy entries, and returns the
     * number of removed entries and bytes freed.
     */
    fun migrate(cache: Cache): Result {
        var removedCount = 0
        var freedBytes = 0L
        val keys = cache.keys.toList()
        for (key in keys) {
            if (!isLegacyKey(key)) continue
            val spans = cache.getCachedSpans(key)
            val keyBytes = spans.sumOf { it.length }
            cache.removeResource(key)
            removedCount++
            freedBytes += keyBytes
        }
        return Result(removedCount, freedBytes)
    }
}
