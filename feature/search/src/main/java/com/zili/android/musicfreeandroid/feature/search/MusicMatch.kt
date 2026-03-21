package com.zili.android.musicfreeandroid.feature.search

import com.zili.android.musicfreeandroid.core.model.MusicItem
import kotlin.math.abs

internal object MusicMatch {

    private const val MIN_ACCEPTABLE_SCORE = 6

    fun buildFallbackQuery(item: MusicItem): String {
        return listOf(item.title, item.artist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
    }

    fun pickBestCandidate(target: MusicItem, candidates: List<MusicItem>): MusicItem? {
        val targetTitle = normalize(target.title)
        val targetArtist = normalize(target.artist)
        val scored = candidates
            .map { candidate -> candidate to score(target, candidate, targetTitle, targetArtist) }
            .maxByOrNull { (_, score) -> score }
            ?: return null
        return scored.first.takeIf { scored.second >= MIN_ACCEPTABLE_SCORE }
    }

    private fun score(
        target: MusicItem,
        candidate: MusicItem,
        targetTitle: String,
        targetArtist: String,
    ): Int {
        val candidateTitle = normalize(candidate.title)
        val candidateArtist = normalize(candidate.artist)
        var score = 0

        score += when {
            candidateTitle == targetTitle -> 8
            candidateTitle.contains(targetTitle) || targetTitle.contains(candidateTitle) -> 4
            else -> 0
        }

        score += when {
            targetArtist.isBlank() -> 0
            candidateArtist == targetArtist -> 6
            candidateArtist.contains(targetArtist) || targetArtist.contains(candidateArtist) -> 3
            else -> 0
        }

        if (target.duration > 0 && candidate.duration > 0) {
            val deltaMs = abs(target.duration - candidate.duration)
            score += when {
                deltaMs <= 3_000L -> 2
                deltaMs <= 10_000L -> 1
                else -> 0
            }
        }
        return score
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
            .trim()
    }
}
