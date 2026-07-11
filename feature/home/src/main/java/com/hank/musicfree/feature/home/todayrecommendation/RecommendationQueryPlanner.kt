package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import javax.inject.Inject

class RecommendationQueryPlanner @Inject constructor() {
    fun plan(profile: PreferenceProfile): List<RecommendationQuery> {
        if (profile.confidence == ProfileConfidence.LOW) {
            return COLD_START_QUERIES.map(::RecommendationQuery)
        }
        return buildList {
            profile.artists.take(2).forEach { add(RecommendationQuery("${it.value} 歌单", it)) }
            profile.genres.take(1).forEach { add(RecommendationQuery("${it.value} 歌单", it)) }
            profile.languages.take(1).forEach { add(RecommendationQuery("${languageLabel(it.value)} 歌单", it)) }
        }.distinctBy { it.query }.take(MAX_QUERIES).ifEmpty {
            COLD_START_QUERIES.map(::RecommendationQuery)
        }
    }

    private fun languageLabel(value: String): String = when (value) {
        "zh-CN" -> "国语"
        "en" -> "英语"
        "yue" -> "粤语"
        "ja" -> "日语"
        "ko" -> "韩语"
        else -> value
    }

    private companion object {
        const val MAX_QUERIES = 4
        val COLD_START_QUERIES = listOf("热门歌单", "每日推荐", "宝藏歌单")
    }
}
