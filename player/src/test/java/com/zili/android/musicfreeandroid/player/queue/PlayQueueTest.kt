package com.zili.android.musicfreeandroid.player.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayQueueTest {

    private lateinit var queue: PlayQueue

    private fun item(
        id: String,
        platform: String = "test",
        title: String = "Song $id",
    ) = MusicItem(
        id = id, platform = platform, title = title,
        artist = "Artist", album = null, duration = 180_000L,
        url = "https://example.com/$id.mp3", artwork = null, qualities = null,
    )

    private val song1 = item("1")
    private val song2 = item("2")
    private val song3 = item("3")
    private val song4 = item("4")

    @Before
    fun setUp() {
        queue = PlayQueue()
    }

    // --- isEmpty / size ---

    @Test
    fun `new queue is empty`() {
        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)
        assertNull(queue.currentItem)
        assertEquals(-1, queue.currentIndex)
    }

    // --- setQueue ---

    @Test
    fun `setQueue replaces all items and sets currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 1)
        assertEquals(3, queue.size)
        assertEquals(1, queue.currentIndex)
        assertEquals(song2, queue.currentItem)
    }

    @Test
    fun `setQueue with empty list clears queue`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        queue.setQueue(emptyList(), startIndex = 0)
        assertTrue(queue.isEmpty)
        assertEquals(-1, queue.currentIndex)
    }

    @Test
    fun `setQueue clamps startIndex to valid range`() {
        queue.setQueue(listOf(song1, song2), startIndex = 5)
        assertEquals(1, queue.currentIndex)
    }

    // --- add / addNext ---

    @Test
    fun `add appends item to end of queue`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        queue.add(song2)
        assertEquals(2, queue.size)
        assertEquals(listOf(song1, song2), queue.items)
    }

    @Test
    fun `add to empty queue sets currentIndex to 0`() {
        queue.add(song1)
        assertEquals(0, queue.currentIndex)
        assertEquals(song1, queue.currentItem)
    }

    @Test
    fun `addNext inserts after currentIndex`() {
        queue.setQueue(listOf(song1, song3), startIndex = 0)
        queue.addNext(song2)
        assertEquals(listOf(song1, song2, song3), queue.items)
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `addNext moves existing target after current without duplicating`() {
        val updatedSong3 = item("3", title = "Updated Song 3")
        queue.setQueue(listOf(song1, song2, song3, song4), startIndex = 1)

        queue.addNext(updatedSong3)

        assertEquals(listOf(song1, song2, updatedSong3, song4), queue.items)
        assertEquals(1, queue.currentIndex)
        assertEquals(song2, queue.currentItem)
        assertEquals(1, queue.items.count { it.id == "3" && it.platform == "test" })
    }

    @Test
    fun `addNext moves existing target before current and keeps current item`() {
        val updatedSong1 = item("1", title = "Updated Song 1")
        queue.setQueue(listOf(song1, song2, song3), startIndex = 1)

        queue.addNext(updatedSong1)

        assertEquals(listOf(song2, updatedSong1, song3), queue.items)
        assertEquals(0, queue.currentIndex)
        assertEquals(song2, queue.currentItem)
        assertEquals(1, queue.items.count { it.id == "1" && it.platform == "test" })
    }

    @Test
    fun `addNext does not duplicate current item`() {
        val updatedCurrent = item("2", title = "Updated Song 2")
        queue.setQueue(listOf(song1, song2, song3), startIndex = 1)

        queue.addNext(updatedCurrent)

        assertEquals(listOf(song1, song2, song3), queue.items)
        assertEquals(1, queue.currentIndex)
        assertEquals(song2, queue.currentItem)
        assertEquals(1, queue.items.count { it.id == "2" && it.platform == "test" })
    }

    @Test
    fun `addNext deduplicates by id and platform`() {
        val otherPlatformSong2 = item("2", platform = "other")
        val updatedSong2 = item("2", platform = "test", title = "Updated Song 2")
        queue.setQueue(listOf(song1, otherPlatformSong2, song2), startIndex = 0)

        queue.addNext(updatedSong2)

        assertEquals(listOf(song1, updatedSong2, otherPlatformSong2), queue.items)
        assertEquals(0, queue.currentIndex)
        assertEquals(1, queue.items.count { it.id == "2" && it.platform == "test" })
        assertEquals(1, queue.items.count { it.id == "2" && it.platform == "other" })
    }

    // --- remove ---

    @Test
    fun `remove item before currentIndex decrements currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 2)
        queue.remove(0)
        assertEquals(listOf(song2, song3), queue.items)
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `remove item after currentIndex keeps currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        queue.remove(2)
        assertEquals(listOf(song1, song2), queue.items)
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `remove current item moves to next available`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 1)
        val newCurrent = queue.remove(1)
        assertEquals(listOf(song1, song3), queue.items)
        assertEquals(1, queue.currentIndex)
        assertEquals(song3, newCurrent)
    }

    @Test
    fun `remove last item when it is current wraps index`() {
        queue.setQueue(listOf(song1, song2), startIndex = 1)
        val newCurrent = queue.remove(1)
        assertEquals(listOf(song1), queue.items)
        assertEquals(0, queue.currentIndex)
        assertEquals(song1, newCurrent)
    }

    @Test
    fun `remove only item empties queue`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        val newCurrent = queue.remove(0)
        assertTrue(queue.isEmpty)
        assertNull(newCurrent)
    }

    // --- move ---

    @Test
    fun `move item updates order and adjusts currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        queue.move(fromIndex = 0, toIndex = 2)
        assertEquals(listOf(song2, song3, song1), queue.items)
        assertEquals(2, queue.currentIndex)
    }

    // --- next / previous with RepeatMode ---

    @Test
    fun `next advances to next item in OFF mode`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        assertEquals(song2, queue.next(RepeatMode.OFF))
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `next at end returns null in OFF mode`() {
        queue.setQueue(listOf(song1, song2), startIndex = 1)
        assertNull(queue.next(RepeatMode.OFF))
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `next at end wraps to first in ALL mode`() {
        queue.setQueue(listOf(song1, song2), startIndex = 1)
        assertEquals(song1, queue.next(RepeatMode.ALL))
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `next in ONE mode returns same item`() {
        queue.setQueue(listOf(song1, song2), startIndex = 0)
        assertEquals(song1, queue.next(RepeatMode.ONE))
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `previous goes to previous item in OFF mode`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 2)
        assertEquals(song2, queue.previous(RepeatMode.OFF))
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `previous at start returns null in OFF mode`() {
        queue.setQueue(listOf(song1, song2), startIndex = 0)
        assertNull(queue.previous(RepeatMode.OFF))
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `previous at start wraps to last in ALL mode`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        assertEquals(song3, queue.previous(RepeatMode.ALL))
        assertEquals(2, queue.currentIndex)
    }

    @Test
    fun `previous in ONE mode returns same item`() {
        queue.setQueue(listOf(song1, song2), startIndex = 1)
        assertEquals(song2, queue.previous(RepeatMode.ONE))
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `next on empty queue returns null`() {
        assertNull(queue.next(RepeatMode.ALL))
    }

    @Test
    fun `previous on empty queue returns null`() {
        assertNull(queue.previous(RepeatMode.ALL))
    }

    @Test
    fun `next with single item in ALL mode returns same item`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        assertEquals(song1, queue.next(RepeatMode.ALL))
        assertEquals(0, queue.currentIndex)
    }

    // --- skipTo ---

    @Test
    fun `skipTo sets currentIndex and returns item`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        assertEquals(song3, queue.skipTo(2))
        assertEquals(2, queue.currentIndex)
    }

    @Test
    fun `skipTo with invalid index returns null`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        assertNull(queue.skipTo(5))
        assertEquals(0, queue.currentIndex)
    }

    // --- shuffle / unshuffle ---

    @Test
    fun `shuffle randomizes order but keeps currentItem the same`() {
        val items = (1..20).map { item(it.toString()) }
        queue.setQueue(items, startIndex = 5)
        val currentBefore = queue.currentItem

        queue.shuffle()

        assertEquals(currentBefore, queue.currentItem)
        assertEquals(20, queue.size)
        assertEquals(0, queue.currentIndex)
        assertEquals(currentBefore, queue.items[0])
    }

    @Test
    fun `unshuffle restores original order and finds current item`() {
        val items = listOf(song1, song2, song3, song4)
        queue.setQueue(items, startIndex = 1)

        queue.shuffle()
        val currentAfterShuffle = queue.currentItem
        assertEquals(song2, currentAfterShuffle)

        queue.unshuffle()
        assertEquals(items, queue.items)
        assertEquals(song2, queue.currentItem)
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `shuffle on empty queue does nothing`() {
        queue.shuffle()
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `shuffle single item queue keeps it`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        queue.shuffle()
        assertEquals(listOf(song1), queue.items)
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `items added after shuffle are included in unshuffle`() {
        queue.setQueue(listOf(song1, song2), startIndex = 0)
        queue.shuffle()
        queue.add(song3)
        queue.unshuffle()
        assertEquals(song1, queue.items[0])
        assertEquals(song2, queue.items[1])
        assertEquals(song3, queue.items[2])
    }

    @Test
    fun `items removed during shuffle stay removed after unshuffle`() {
        queue.setQueue(listOf(song1, song2, song3, song4), startIndex = 0)
        queue.shuffle()
        val song3Index = queue.items.indexOfFirst { it.id == "3" }
        queue.remove(song3Index)
        assertEquals(3, queue.size)

        queue.unshuffle()
        assertEquals(3, queue.size)
        assertEquals(song1, queue.items[0])
        assertEquals(song2, queue.items[1])
        assertEquals(song4, queue.items[2])
    }
}
