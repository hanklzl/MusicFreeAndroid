package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import javax.inject.Inject

class PlaylistRanker internal constructor(
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
    private val maxPerPlatform: Int = DEFAULT_MAX_PER_PLATFORM,
) {
    @Inject
    constructor() : this(DEFAULT_MAX_RESULTS, DEFAULT_MAX_PER_PLATFORM)

    fun rank(
        candidates: List<RecommendationCandidate>,
        profile: PreferenceProfile,
        recentlyExposedKeys: Set<String>,
    ): List<RecommendedSheet> {
        val ranked = candidates
            .asSequence()
            .filter { it.sheet.id.isNotBlank() && it.sheet.platform.isNotBlank() }
            .groupBy { "${it.sheet.platform}:${it.sheet.id}" }
            .mapNotNull { (_, duplicates) ->
                duplicates.map { score(it, profile, recentlyExposedKeys) }.maxByOrNull { it.score }
            }
            .sortedWith(compareByDescending<RecommendedSheet> { it.score }.thenBy { it.key })

        val platformCounts = mutableMapOf<String, Int>()
        return ranked.filter { item ->
            val count = platformCounts.getOrDefault(item.sheet.platform, 0)
            if (count >= maxPerPlatform) {
                false
            } else {
                platformCounts[item.sheet.platform] = count + 1
                true
            }
        }.take(maxResults)
    }

    private fun score(
        candidate: RecommendationCandidate,
        profile: PreferenceProfile,
        exposed: Set<String>,
    ): RecommendedSheet {
        val sheet = candidate.sheet
        val searchableText = listOfNotNull(sheet.title, sheet.artist, sheet.description)
            .joinToString(" ")
            .lowercase()
        val preference = candidate.preference
        var score = when (candidate.source) {
            CandidateSource.TAG -> 30.0
            CandidateSource.SEARCH -> 20.0
        }
        if (preference != null && preference.value.lowercase() in searchableText) score += 40.0
        if (profile.platforms.firstOrNull()?.value == sheet.platform) score += 12.0
        if (!sheet.coverImg.isNullOrBlank() || !sheet.artwork.isNullOrBlank()) score += 5.0
        if ((sheet.worksNum ?: 0) > 0) score += 3.0
        val key = "${sheet.platform}:${sheet.id}"
        if (key in exposed) score -= 35.0
        val reason = preference?.let { "因为你常听${it.value}" }
            ?: if (candidate.source == CandidateSource.TAG) "来自 ${sheet.platform} 的精选歌单" else "为你发现的热门歌单"
        return RecommendedSheet(sheet = sheet, reason = reason, score = score)
    }

    private companion object {
        const val DEFAULT_MAX_RESULTS = 12
        const val DEFAULT_MAX_PER_PLATFORM = 4
    }
}
