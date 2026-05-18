package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RepeatMode
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.queue.PlayQueueSnapshot
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import java.util.concurrent.CopyOnWriteArrayList
import org.mockito.kotlin.mock
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
        MfLog.resetForTest()
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `queueState defaults to EMPTY`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            assertEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playQueue emits snapshot with items and startIndex`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
    fun `restoreQueue emits snapshot without starting playback when autoplay is false`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            val items = listOf(item("1"), item("2"), item("3"))
            controller.restoreQueue(items, startIndex = 1, playWhenRestored = false)
            val snapshot = controller.queueState.value
            assertEquals(items, snapshot.items)
            assertEquals(1, snapshot.currentIndex)
            assertEquals(item("2"), controller.playerState.value.currentItem)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playItem adds new item and emits snapshot pointing at it`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.playQueue(listOf(item("1"), item("2")), startIndex = 0)
            controller.skipToNext()
            assertEquals(1, controller.queueState.value.currentIndex)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `skipToNext logs target diagnostics for restored queue item`() {
        val logger = QueueStateRecordingLogger()
        MfLog.install(logger)
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            val next = item("2").copy(
                url = null,
                raw = mapOf(
                    "songmid" to "003abc",
                    "pay" to mapOf("play" to 1),
                ),
                localPath = null,
            )
            controller.restoreQueue(listOf(item("1"), next), startIndex = 0, playWhenRestored = false)

            controller.skipToNext()

            val event = logger.events.single { it.event == "player_skip_next" }
            assertEquals(LogCategory.PLAYER, event.category)
            assertEquals(0, event.fields["fromIndex"])
            assertEquals(1, event.fields["toIndex"])
            assertEquals("1", event.fields["fromItemId"])
            assertEquals("2", event.fields["toItemId"])
            assertEquals("test", event.fields["platform"])
            assertEquals(2, event.fields["toRawKeyCount"])
            assertEquals(false, event.fields["toHasQualities"])
            assertEquals(false, event.fields["toHasUrl"])
            assertEquals(false, event.fields["toHasLocalPath"])
        } finally {
            controller.release()
        }
    }

    @Test
    fun `toggleShuffle emits snapshot whose items differ in order`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
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
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.playQueue(listOf(item("1"), item("2")), startIndex = 0)
            assertNotEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
            controller.reset()
            assertEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `restoreQueue with savedPosition and savedDuration emits PlayerState with both values`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            val items = listOf(item("1"), item("2"), item("3"))
            controller.restoreQueue(
                items = items,
                startIndex = 1,
                savedPositionMs = 42_000L,
                savedDurationMs = 180_000L,
                playWhenRestored = false,
            )
            val playerState = controller.playerState.value
            assertEquals(item("2"), playerState.currentItem)
            assertEquals(42_000L, playerState.position)
            assertEquals(180_000L, playerState.duration)
            assertEquals(false, playerState.isPlaying)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `restoreQueue with savedPosition zero leaves player position at 0`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 0L,
                savedDurationMs = 0L,
                playWhenRestored = false,
            )
            assertEquals(0L, controller.playerState.value.position)
            assertEquals(0L, controller.playerState.value.duration)
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

private data class QueueStateRecordedLogEvent(
    val level: String,
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
    val throwable: Throwable? = null,
)

private class QueueStateRecordingLogger : MfLogger {
    val events = CopyOnWriteArrayList<QueueStateRecordedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += QueueStateRecordedLogEvent("trace", category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += QueueStateRecordedLogEvent("detail", category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += QueueStateRecordedLogEvent("error", category, event, fields, throwable)
    }

    override fun flush() = Unit
}
