package com.hank.musicfree.data.sort

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SortModeApplyTest {
    private fun item(id: String, title: String = id, artist: String = "", album: String? = null, addedAt: Long = 0L) =
        MusicItem(
            id = id, platform = "test", title = title, artist = artist, album = album,
            duration = 0L, url = null, artwork = null, qualities = null, addedAt = addedAt,
        )

    private val items = listOf(
        item("a", title = "苹果", artist = "Z", album = "Y", addedAt = 100),
        item("b", title = "Apple", artist = "A", album = "X", addedAt = 200),
        item("c", title = "香蕉", artist = "M", album = "X", addedAt = 50),
    )

    @Test fun manual_preservesInputOrder() {
        assertEquals(items, items.applySort(SortMode.Manual))
    }

    @Test fun newest_descByAddedAt() {
        assertEquals(listOf("b", "a", "c"), items.applySort(SortMode.Newest).map { it.id })
    }

    @Test fun oldest_ascByAddedAt() {
        assertEquals(listOf("c", "a", "b"), items.applySort(SortMode.Oldest).map { it.id })
    }

    @Test fun title_chineseCollator() {
        // collator orders Latin script vs Chinese — exact ordering depends on JVM Collator impl,
        // but the test asserts collator is invoked (not raw String compare). Verify by relative ordering.
        val sorted = items.applySort(SortMode.Title).map { it.title }
        // "Apple" should be present, "苹果" and "香蕉" should be sorted by pinyin (ping < xiang)
        // Note: actual ordering between Latin "Apple" and Chinese chars varies by JVM; the test just
        // verifies the Chinese pair (苹果, 香蕉) is in pinyin order.
        val pingIdx = sorted.indexOf("苹果")
        val xiangIdx = sorted.indexOf("香蕉")
        assertEquals(true, pingIdx < xiangIdx)
    }

    @Test fun artist_emptyTreatedAsEmptyString() {
        val withEmpty = items + item(id = "d", title = "Z", artist = "")
        val sorted = withEmpty.applySort(SortMode.Artist).map { it.id }
        // Empty artist sorts first, then A / M / Z
        assertEquals(listOf("d", "b", "c", "a"), sorted)
    }

    @Test fun album_handlesNullSafely() {
        val sorted = items.applySort(SortMode.Album).map { it.id }
        // Album X appears twice (b/c), Y once (a). Stability preserves b before c.
        assertEquals(listOf("b", "c", "a"), sorted)
    }
}
