package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.data.repository.recommendation.model.WeightedPreference
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRankerTest {

    private val ranker = PlaylistRanker(maxResults = 20, maxPerPlatform = 6)

    @Test
    fun `deduplicates by platform and id while preserving personalized reason`() {
        val preference = WeightedPreference("摇滚", 10.0)
        val duplicate = sheet("same", "qq", "摇滚现场精选")
        val results = ranker.rank(
            candidates = listOf(
                RecommendationCandidate(duplicate, CandidateSource.SEARCH, preference),
                RecommendationCandidate(duplicate.copy(description = "duplicate"), CandidateSource.TAG, preference),
            ),
            profile = profile(genres = listOf(preference)),
            recentlyExposedKeys = emptySet(),
        )

        assertEquals(1, results.size)
        assertEquals("qq:same", results.single().key)
        assertEquals("因为你常听摇滚", results.single().reason)
    }

    @Test
    fun `recent exposure is downranked and one platform cannot crowd out results`() {
        val candidates = buildList {
            repeat(8) { index ->
                add(RecommendationCandidate(sheet("qq-$index", "qq", "热门 $index"), CandidateSource.SEARCH))
            }
            add(RecommendationCandidate(sheet("fresh", "netease", "热门 新发现"), CandidateSource.SEARCH))
        }

        val results = ranker.rank(
            candidates = candidates,
            profile = profile(),
            recentlyExposedKeys = setOf("qq:qq-0"),
        )

        assertTrue(results.count { it.sheet.platform == "qq" } <= 6)
        val freshIndex = results.indexOfFirst { it.key == "netease:fresh" }
        val exposedIndex = results.indexOfFirst { it.key == "qq:qq-0" }
        assertTrue(exposedIndex == -1 || freshIndex < exposedIndex)
    }

    private fun profile(genres: List<WeightedPreference> = emptyList()) = PreferenceProfile(
        artists = emptyList(),
        genres = genres,
        languages = emptyList(),
        platforms = emptyList(),
        distinctSongCount = 20,
        confidence = ProfileConfidence.ESTABLISHED,
        signature = "profile",
    )

    private fun sheet(id: String, platform: String, title: String) = MusicSheetItemBase(
        id = id,
        platform = platform,
        title = title,
        artist = null,
        description = null,
        coverImg = "https://example.com/$id.jpg",
        artwork = null,
        worksNum = 30,
        raw = mapOf("id" to id),
    )
}
