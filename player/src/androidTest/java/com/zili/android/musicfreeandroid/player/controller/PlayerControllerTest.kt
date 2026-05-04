package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import java.util.concurrent.CountDownLatch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: PlayerController

    private fun testItem(id: String) = MusicItem(
        id = id, platform = "test", title = "Song $id",
        artist = "Artist", album = null, duration = 1_000L,
        url = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }",
        artwork = null, qualities = null,
    )

    private fun runOnAppThread(block: () -> Unit) {
        val latch = CountDownLatch(1)
        context.mainExecutor.execute {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

    @Before
    fun setUp() {
        controller = PlayerController(context)
        kotlinx.coroutines.runBlocking {
            controller.connect()
        }
    }

    @After
    fun tearDown() {
        runOnAppThread {
            controller.release()
        }
    }

    @Test
    fun initialStateIsEmpty() {
        val state = controller.playerState.value
        assertNull(state.currentItem)
        assertFalse(state.isPlaying)
    }

    @Test
    fun playQueueSetsCurrentItemAndPlays() {
        runOnAppThread {
            controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 0)
        }
        waitUntil("controller loads the queued item") {
            controller.playerState.value.currentItem?.id == "1"
        }

        val playingState = controller.playerState.value
        assertNotNull(playingState.currentItem)
        assertEquals("1", playingState.currentItem?.id)
    }

    @Test
    fun playQueueConnectsOnDemandAndEmitsState() {
        val unconnectedController = PlayerController(context)

        try {
            runOnAppThread {
                unconnectedController.playQueue(listOf(testItem("1")), startIndex = 0)
            }

            waitUntil("on-demand connect publishes the queued item") {
                unconnectedController.playerState.value.currentItem?.id == "1"
            }

            assertTrue(unconnectedController.playerState.value.hasMedia)
        } finally {
            unconnectedController.release()
        }
    }

    @Test
    fun pauseAndResumeWork() {
        runOnAppThread {
            controller.playQueue(listOf(testItem("1")), startIndex = 0)
        }
        waitUntil("controller starts playback") {
            controller.playerState.value.isPlaying
        }

        runOnAppThread {
            controller.pause()
        }
        waitUntil("controller pauses playback") {
            !controller.playerState.value.isPlaying
        }
        assertFalse(controller.playerState.value.isPlaying)

        runOnAppThread {
            controller.play()
        }
        waitUntil("controller resumes playback") {
            controller.playerState.value.isPlaying
        }
        assertTrue(controller.playerState.value.isPlaying)
    }

    @Test
    fun skipToNextAdvancesQueue() {
        runOnAppThread {
            controller.playQueue(
                listOf(testItem("1"), testItem("2"), testItem("3")),
                startIndex = 0,
            )
        }
        waitUntil("controller starts first queued item") {
            controller.playerState.value.currentItem?.id == "1"
        }

        runOnAppThread {
            controller.skipToNext()
        }
        waitUntil("controller advances to second item") {
            controller.playerState.value.currentItem?.id == "2"
        }
        assertEquals("2", controller.playerState.value.currentItem?.id)

        runOnAppThread {
            controller.skipToNext()
        }
        waitUntil("controller advances to third item") {
            controller.playerState.value.currentItem?.id == "3"
        }
        assertEquals("3", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun skipToPreviousGoesBack() {
        runOnAppThread {
            controller.playQueue(
                listOf(testItem("1"), testItem("2"), testItem("3")),
                startIndex = 2,
            )
        }
        waitUntil("controller starts third item") {
            controller.playerState.value.currentItem?.id == "3"
        }

        runOnAppThread {
            controller.skipToPrevious()
        }
        waitUntil("controller moves to previous item") {
            controller.playerState.value.currentItem?.id == "2"
        }
        assertEquals("2", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun repeatModeAffectsNavigation() {
        runOnAppThread {
            controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 1)
        }
        waitUntil("controller starts second item") {
            controller.playerState.value.currentItem?.id == "2"
        }

        controller.setRepeatMode(RepeatMode.OFF)
        runOnAppThread {
            controller.skipToNext()
        }
        Thread.sleep(200)
        assertEquals("2", controller.playerState.value.currentItem?.id)

        controller.setRepeatMode(RepeatMode.ALL)
        runOnAppThread {
            controller.skipToNext()
        }
        waitUntil("controller wraps to first item") {
            controller.playerState.value.currentItem?.id == "1"
        }
        assertEquals("1", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun shuffleToggleShufflesAndRestoresQueue() {
        val items = (1..10).map { testItem(it.toString()) }
        runOnAppThread {
            controller.playQueue(items, startIndex = 0)
        }
        waitUntil("controller starts shuffled queue seed item") {
            controller.playerState.value.currentItem?.id == "1"
        }

        controller.toggleShuffle()
        assertTrue(controller.playerState.value.shuffleEnabled)
        assertEquals("1", controller.playerState.value.currentItem?.id)

        controller.toggleShuffle()
        assertFalse(controller.playerState.value.shuffleEnabled)
        assertEquals(items.map { it.id }, controller.playQueue.items.map { it.id })
    }

    @Test
    fun resetClearsQueueAndCurrentItem() {
        runOnAppThread {
            controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 0)
        }
        waitUntil("controller starts first item") {
            controller.playerState.value.currentItem?.id == "1"
        }

        runOnAppThread {
            controller.reset()
        }

        waitUntil("controller clears playback state") {
            controller.playerState.value.currentItem == null && !controller.playerState.value.hasMedia
        }
        assertTrue(controller.playQueue.items.isEmpty())
    }

    private fun waitUntil(
        description: String,
        timeoutMs: Long = 3_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for $description")
    }
}
