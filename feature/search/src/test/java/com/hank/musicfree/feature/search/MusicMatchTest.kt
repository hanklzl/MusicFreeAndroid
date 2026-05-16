package com.hank.musicfree.feature.search

import com.hank.musicfree.core.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicMatchTest {

    @Test
    fun `buildFallbackQuery joins title and artist`() {
        val query = MusicMatch.buildFallbackQuery(
            item = item(
                id = "1",
                platform = "qq",
                title = "In The End",
                artist = "Linkin Park",
            ),
        )

        assertEquals("In The End Linkin Park", query)
    }

    @Test
    fun `pickBestCandidate prefers exact title artist and close duration`() {
        val target = item(
            id = "306948",
            platform = "元力QQ",
            title = "In The End",
            artist = "Linkin Park",
            duration = 216_000L,
        )
        val best = item(
            id = "wy-1",
            platform = "元力WY",
            title = "In The End",
            artist = "Linkin Park",
            duration = 215_000L,
        )
        val distractor = item(
            id = "wy-2",
            platform = "元力WY",
            title = "In The Beginning",
            artist = "Linkin",
            duration = 250_000L,
        )

        val matched = MusicMatch.pickBestCandidate(target, listOf(distractor, best))

        assertEquals(best.id, matched?.id)
    }

    @Test
    fun `pickBestCandidate returns null when candidates are unrelated`() {
        val target = item(
            id = "target",
            platform = "元力QQ",
            title = "In The End",
            artist = "Linkin Park",
        )
        val unrelated = item(
            id = "wy-404",
            platform = "元力WY",
            title = "Random Song",
            artist = "Unknown Artist",
        )

        val matched = MusicMatch.pickBestCandidate(target, listOf(unrelated))

        assertNull(matched)
    }

    private fun item(
        id: String,
        platform: String,
        title: String,
        artist: String,
        duration: Long = 0L,
    ): MusicItem {
        return MusicItem(
            id = id,
            platform = platform,
            title = title,
            artist = artist,
            album = null,
            duration = duration,
            url = null,
            artwork = null,
            qualities = null,
        )
    }
}
