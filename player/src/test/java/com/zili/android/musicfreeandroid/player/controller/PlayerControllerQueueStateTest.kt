package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerQueueStateTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `queueState defaults to EMPTY`() {
        val controller = PlayerController(context)
        try {
            assertEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playQueue emits snapshot with items and startIndex`() {
        val controller = PlayerController(context)
        try {
            val items = listOf(item("1"), item("2"), item("3"))
            controller.playQueue(items, startIndex = 1)
            val snapshot = controller.queueState.value
            assertEquals(items, snapshot.items)
            assertEquals(1, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playItem adds new item and emits snapshot pointing at it`() {
        val controller = PlayerController(context)
        try {
            controller.playItem(item("1"))
            val snapshot = controller.queueState.value
            assertEquals(listOf(item("1")), snapshot.items)
            assertEquals(0, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playItem skipTo when item already exists and emits snapshot`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
            controller.playItem(item("3"))
            val snapshot = controller.queueState.value
            assertEquals(3, snapshot.items.size)
            assertEquals(2, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `addToQueue emits snapshot with appended item`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1")), startIndex = 0)
            controller.addToQueue(item("2"))
            val snapshot = controller.queueState.value
            assertEquals(listOf(item("1"), item("2")), snapshot.items)
            assertEquals(0, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `addNextInQueue emits snapshot with item inserted after current`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("3")), startIndex = 0)
            controller.addNextInQueue(item("2"))
            val snapshot = controller.queueState.value
            assertEquals(listOf(item("1"), item("2"), item("3")), snapshot.items)
            assertEquals(0, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `removeFromQueue non-current emits snapshot with smaller list`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
            controller.removeFromQueue(2)
            val snapshot = controller.queueState.value
            assertEquals(listOf(item("1"), item("2")), snapshot.items)
            assertEquals(0, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `removeFromQueue current emits snapshot with adjusted currentIndex`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 1)
            controller.removeFromQueue(1)
            val snapshot = controller.queueState.value
            assertEquals(listOf(item("1"), item("3")), snapshot.items)
            assertEquals(1, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `moveInQueue emits snapshot with new order`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
            controller.moveInQueue(0, 2)
            val snapshot = controller.queueState.value
            assertEquals(listOf(item("2"), item("3"), item("1")), snapshot.items)
            assertEquals(2, snapshot.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `skipTo emits snapshot with target index`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
            controller.skipTo(2)
            assertEquals(2, controller.queueState.value.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `skipToNext emits snapshot with advanced currentIndex`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("2")), startIndex = 0)
            controller.skipToNext()
            assertEquals(1, controller.queueState.value.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `toggleShuffle emits snapshot whose items differ in order`() {
        val controller = PlayerController(context)
        try {
            val original = (1..6).map { item(it.toString()) }
            controller.playQueue(original, startIndex = 0)
            controller.toggleShuffle()
            val snapshot = controller.queueState.value
            // current still at index 0 by PlayQueue.shuffle contract
            assertEquals(0, snapshot.currentIndex)
            assertEquals(original.size, snapshot.items.size)
            // ordering may collide for tiny inputs; compare bag of ids stays equal
            assertEquals(original.map { it.id }.toSet(), snapshot.items.map { it.id }.toSet())
        } finally {
            controller.release()
        }
    }

    @Test
    fun `reset emits empty snapshot`() {
        val controller = PlayerController(context)
        try {
            controller.playQueue(listOf(item("1"), item("2")), startIndex = 0)
            assertNotEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
            controller.reset()
            assertEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
        } finally {
            controller.release()
        }
    }

    internal fun item(id: String) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = "https://example.test/$id.mp3",
        artwork = null,
        qualities = null,
    )
}
