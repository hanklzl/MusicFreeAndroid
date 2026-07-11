package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.listenstats.model.RecommendationListenEvent
import com.hank.musicfree.data.repository.recommendation.PreferenceProfileBuilder
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceProfileBuilderTest {

    private val now = 1_720_000_000_000L
    private val builder = PreferenceProfileBuilder()

    @Test
    fun `completed recent listens dominate older partial listens`() {
        val events = buildList {
            repeat(3) { index ->
                add(
                    event(
                        musicId = "recent-$index",
                        artist = "陈奕迅",
                        platform = "netease",
                        playedAtMs = now - index * 60_000L,
                        completed = true,
                        playedSeconds = 220,
                        genre = "pop",
                        language = "zh-CN",
                    ),
                )
            }
            repeat(5) { index ->
                add(
                    event(
                        musicId = "old-$index",
                        artist = "Other",
                        platform = "qq",
                        playedAtMs = now - 28L * DAY_MS,
                        completed = false,
                        playedSeconds = 12,
                        genre = "rock",
                        language = "en",
                    ),
                )
            }
        }

        val profile = builder.build(events, now)

        assertEquals("陈奕迅", profile.artists.first().value)
        assertEquals("netease", profile.platforms.first().value)
        assertEquals("pop", profile.genres.first().value)
        assertEquals("zh-CN", profile.languages.first().value)
    }

    @Test
    fun `few distinct songs produce low confidence cold start profile`() {
        val profile = builder.build(
            events = listOf(
                event("one", "A", "qq", now, completed = true),
                event("one", "A", "qq", now - 1_000L, completed = true),
            ),
            nowEpochMs = now,
        )

        assertEquals(ProfileConfidence.LOW, profile.confidence)
        assertEquals(1, profile.distinctSongCount)
        assertTrue(profile.signature.isNotBlank())
    }

    private fun event(
        musicId: String,
        artist: String,
        platform: String,
        playedAtMs: Long,
        completed: Boolean,
        playedSeconds: Int = 180,
        genre: String? = null,
        language: String? = null,
    ) = RecommendationListenEvent(
        playedAtMs = playedAtMs,
        musicId = musicId,
        platform = platform,
        title = musicId,
        artistRaw = artist,
        playedSeconds = playedSeconds,
        completed = completed,
        language = language,
        genre = genre,
    )

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1_000L
    }
}
