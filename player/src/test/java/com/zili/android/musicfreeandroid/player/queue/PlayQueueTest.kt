package com.zili.android.musicfreeandroid.player.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayQueueTest {

    private lateinit var queue: PlayQueue

    private fun item(id: String) = MusicItem(
        id = id, platform = "test", title = "Song $id",
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
}
