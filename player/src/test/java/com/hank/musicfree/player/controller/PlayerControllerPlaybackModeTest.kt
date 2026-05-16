package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RepeatMode
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import org.mockito.kotlin.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerPlaybackModeTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `cyclePlaybackMode enters shuffle from queue mode`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())

        try {
            controller.playQueue.setQueue(testItems(), startIndex = 1)
            controller.setRepeatMode(RepeatMode.ALL)

            controller.cyclePlaybackMode()

            assertTrue(controller.playerState.value.shuffleEnabled)
            assertEquals(RepeatMode.ALL, controller.playerState.value.repeatMode)
            assertTrue(controller.playQueue.isShuffled)
            assertEquals("2", controller.playQueue.currentItem?.id)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `cyclePlaybackMode treats repeat off as queue mode`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())

        try {
            controller.playQueue.setQueue(testItems(), startIndex = 1)

            controller.cyclePlaybackMode()

            assertTrue(controller.playerState.value.shuffleEnabled)
            assertEquals(RepeatMode.ALL, controller.playerState.value.repeatMode)
            assertTrue(controller.playQueue.isShuffled)
            assertEquals("2", controller.playQueue.currentItem?.id)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `cyclePlaybackMode exits shuffle and enters single mode`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        val originalItems = testItems()

        try {
            controller.playQueue.setQueue(originalItems, startIndex = 1)
            controller.setRepeatMode(RepeatMode.ALL)
            controller.toggleShuffle()
            assertTrue(controller.playerState.value.shuffleEnabled)
            assertTrue(controller.playQueue.isShuffled)

            controller.cyclePlaybackMode()

            assertFalse(controller.playerState.value.shuffleEnabled)
            assertEquals(RepeatMode.ONE, controller.playerState.value.repeatMode)
            assertFalse(controller.playQueue.isShuffled)
            assertEquals(originalItems, controller.playQueue.items)
            assertEquals("2", controller.playQueue.currentItem?.id)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `cyclePlaybackMode enters queue mode from single mode`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())

        try {
            controller.playQueue.setQueue(testItems(), startIndex = 0)
            controller.setRepeatMode(RepeatMode.ONE)

            controller.cyclePlaybackMode()

            assertFalse(controller.playerState.value.shuffleEnabled)
            assertEquals(RepeatMode.ALL, controller.playerState.value.repeatMode)
            assertFalse(controller.playQueue.isShuffled)
        } finally {
            controller.release()
        }
    }

    private fun testItems(): List<MusicItem> = listOf(
        testItem("1"),
        testItem("2"),
        testItem("3"),
        testItem("4"),
    )

    private fun testItem(id: String) = MusicItem(
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
