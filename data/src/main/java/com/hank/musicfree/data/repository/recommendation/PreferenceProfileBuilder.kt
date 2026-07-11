package com.hank.musicfree.data.repository.recommendation

import com.hank.musicfree.core.util.splitArtists
import com.hank.musicfree.data.repository.listenstats.model.RecommendationListenEvent
import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.data.repository.recommendation.model.WeightedPreference
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import kotlin.math.exp

class PreferenceProfileBuilder @Inject constructor() {

    fun build(
        events: List<RecommendationListenEvent>,
        nowEpochMs: Long,
    ): PreferenceProfile {
        val artists = linkedMapOf<String, Double>()
        val genres = linkedMapOf<String, Double>()
        val languages = linkedMapOf<String, Double>()
        val platforms = linkedMapOf<String, Double>()

        events.forEach { event ->
            val ageDays = (nowEpochMs - event.playedAtMs).coerceAtLeast(0L) / DAY_MS.toDouble()
            val recency = 0.35 + 0.65 * exp(-ageDays / RECENCY_HALF_LIFE_DAYS)
            val duration = (event.playedSeconds.coerceIn(0, 300) / 60.0).coerceAtLeast(0.2)
            val completion = if (event.completed) 1.7 else 1.0
            val weight = recency * duration * completion

            splitArtists(event.artistRaw).forEach { artists.addWeight(it, weight) }
            event.genre?.normalized()?.let { genres.addWeight(it, weight) }
            event.language?.normalized()?.let { languages.addWeight(it, weight) }
            event.platform.normalized()?.let { platforms.addWeight(it, weight) }
        }

        val distinctSongCount = events
            .asSequence()
            .map { "${it.platform}:${it.musicId}" }
            .distinct()
            .count()
        val confidence = when {
            distinctSongCount < 10 -> ProfileConfidence.LOW
            distinctSongCount < 30 -> ProfileConfidence.DEVELOPING
            else -> ProfileConfidence.ESTABLISHED
        }
        val rankedArtists = artists.ranked(MAX_ARTISTS)
        val rankedGenres = genres.ranked(MAX_GENRES)
        val rankedLanguages = languages.ranked(MAX_LANGUAGES)
        val rankedPlatforms = platforms.ranked(MAX_PLATFORMS)
        val signatureSource = buildString {
            append("v1|").append(distinctSongCount).append('|').append(confidence.name)
            listOf(rankedArtists, rankedGenres, rankedLanguages, rankedPlatforms).forEach { list ->
                append('|')
                append(list.joinToString(",") { "${it.value}:${"%.4f".format(Locale.ROOT, it.weight)}" })
            }
        }

        return PreferenceProfile(
            artists = rankedArtists,
            genres = rankedGenres,
            languages = rankedLanguages,
            platforms = rankedPlatforms,
            distinctSongCount = distinctSongCount,
            confidence = confidence,
            signature = signatureSource.sha256(),
        )
    }

    private fun MutableMap<String, Double>.addWeight(value: String, weight: Double) {
        val normalized = value.normalized() ?: return
        this[normalized] = getOrDefault(normalized, 0.0) + weight
    }

    private fun Map<String, Double>.ranked(limit: Int): List<WeightedPreference> =
        entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { WeightedPreference(it.key, it.value) }

    private fun String.normalized(): String? = trim().takeIf { it.isNotEmpty() }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1_000L
        const val RECENCY_HALF_LIFE_DAYS = 21.0
        const val MAX_ARTISTS = 8
        const val MAX_GENRES = 5
        const val MAX_LANGUAGES = 4
        const val MAX_PLATFORMS = 4
    }
}
