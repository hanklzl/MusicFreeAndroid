package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.data.repository.recommendation.model.WeightedPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationQueryPlannerTest {

    private val planner = RecommendationQueryPlanner()

    @Test
    fun `established profile plans bounded personalized sheet queries`() {
        val profile = profile(
            confidence = ProfileConfidence.ESTABLISHED,
            artists = listOf(WeightedPreference("陈奕迅", 10.0)),
            genres = listOf(WeightedPreference("pop", 8.0)),
            languages = listOf(WeightedPreference("zh-CN", 6.0)),
        )

        val queries = planner.plan(profile)

        assertTrue(queries.size <= 4)
        assertEquals("陈奕迅 歌单", queries.first().query)
        assertTrue(queries.any { it.preference?.value == "pop" })
        assertTrue(queries.all { it.query.isNotBlank() })
    }

    @Test
    fun `cold start uses stable popular fallbacks`() {
        val queries = planner.plan(profile(confidence = ProfileConfidence.LOW))

        assertEquals(listOf("热门歌单", "每日推荐", "宝藏歌单"), queries.map { it.query })
        assertTrue(queries.all { it.preference == null })
    }

    private fun profile(
        confidence: ProfileConfidence,
        artists: List<WeightedPreference> = emptyList(),
        genres: List<WeightedPreference> = emptyList(),
        languages: List<WeightedPreference> = emptyList(),
    ) = PreferenceProfile(
        artists = artists,
        genres = genres,
        languages = languages,
        platforms = emptyList(),
        distinctSongCount = if (confidence == ProfileConfidence.LOW) 2 else 20,
        confidence = confidence,
        signature = "profile",
    )
}
