package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.model.WeightedPreference
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import java.time.LocalDate

data class RecommendationQuery(
    val query: String,
    val preference: WeightedPreference? = null,
)

enum class CandidateSource { TAG, SEARCH }

data class RecommendationCandidate(
    val sheet: MusicSheetItemBase,
    val source: CandidateSource,
    val preference: WeightedPreference? = null,
)

data class RecommendedSheet(
    val sheet: MusicSheetItemBase,
    val reason: String,
    val score: Double,
) {
    val key: String get() = "${sheet.platform}:${sheet.id}"
}

data class DailyRecommendationSnapshot(
    val date: LocalDate,
    val profileSignature: String,
    val confidence: ProfileConfidence,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val items: List<RecommendedSheet>,
)
