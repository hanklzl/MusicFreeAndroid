package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerLazyRestoreTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `play after lazy restoreQueue keeps pendingRestorePosition set`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1"), item("2")),
                startIndex = 0,
                savedPositionMs = 30_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            // controller.play() should be a no-op against the (unconnected) MediaController
            // but must NOT clear pendingRestorePosition.
            controller.play()
            assertEquals(30_000L, controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `user-initiated skipToNext clears pendingRestorePosition`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1"), item("2")),
                startIndex = 0,
                savedPositionMs = 30_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.skipToNext()
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `consumePendingRestoreForTest seeks to pending and clears it`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 45_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            var lastSeek: Long? = null
            controller.consumePendingRestoreForTest(durationMs = 60_000L) { target ->
                lastSeek = target
            }
            assertEquals(45_000L, lastSeek)
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `consumePendingRestoreForTest coerces pending into 0 to duration`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 999_999L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            var lastSeek: Long? = null
            controller.consumePendingRestoreForTest(durationMs = 50_000L) { target ->
                lastSeek = target
            }
            assertEquals(50_000L, lastSeek)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `seekTo before activation updates pendingRestorePosition and playerState position`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 10_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.seekTo(25_000L)
            assertEquals(25_000L, controller.pendingRestorePositionForTest)
            assertEquals(25_000L, controller.playerState.value.position)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `consumePendingRestoreForTest second call after consumption is no-op`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 10_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            var seekCount = 0
            controller.consumePendingRestoreForTest(durationMs = 60_000L) { seekCount++ }
            controller.consumePendingRestoreForTest(durationMs = 60_000L) { seekCount++ }
            assertEquals(1, seekCount)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `skipToPrevious clears pendingRestorePosition`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1"), item("2")),
                startIndex = 1,
                savedPositionMs = 10_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.skipToPrevious()
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `skipTo clears pendingRestorePosition`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1"), item("2"), item("3")),
                startIndex = 0,
                savedPositionMs = 10_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.skipTo(2)
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playItem clears pendingRestorePosition`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 10_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.playItem(item("99"))
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playQueue clears pendingRestorePosition`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 10_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.playQueue(listOf(item("9"), item("8")), startIndex = 1)
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `reset clears pendingRestorePosition`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1")),
                startIndex = 0,
                savedPositionMs = 10_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.reset()
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    private fun item(id: String) = MusicItem(
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
