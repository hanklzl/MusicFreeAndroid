package com.hank.musicfree.data.repository.recommendation.model

data class WeightedPreference(
    val value: String,
    val weight: Double,
)

enum class ProfileConfidence {
    LOW,
    DEVELOPING,
    ESTABLISHED,
}

data class PreferenceProfile(
    val artists: List<WeightedPreference>,
    val genres: List<WeightedPreference>,
    val languages: List<WeightedPreference>,
    val platforms: List<WeightedPreference>,
    val distinctSongCount: Int,
    val confidence: ProfileConfidence,
    val signature: String,
)
