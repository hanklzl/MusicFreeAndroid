package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.turbineScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.player.model.PlaybackState
import kotlinx.coroutines.test.runTest
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

    @Before
    fun setUp() = runTest {
        controller = PlayerController(context)
        controller.connect()
    }

    @After
    fun tearDown() {
        controller.release()
    }

    @Test
    fun initialStateIsEmpty() {
        val state = controller.playerState.value
        assertNull(state.currentItem)
        assertFalse(state.isPlaying)
        assertEquals(PlaybackState.IDLE, state.playbackState)
    }

    @Test
    fun playQueueSetsCurrentItemAndPlays() = runTest {
        turbineScope {
            val states = controller.playerState.testIn(this)
            states.skipItems(1)

            controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 0)

            val playingState = states.awaitItem()
            assertNotNull(playingState.currentItem)
            assertEquals("1", playingState.currentItem?.id)

            states.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pauseAndResumeWork() = runTest {
        controller.playQueue(listOf(testItem("1")), startIndex = 0)
        kotlinx.coroutines.delay(500)

        controller.pause()
        kotlinx.coroutines.delay(100)
        assertFalse(controller.playerState.value.isPlaying)

        controller.play()
        kotlinx.coroutines.delay(100)
        assertTrue(controller.playerState.value.isPlaying)
    }

    @Test
    fun skipToNextAdvancesQueue() = runTest {
        controller.playQueue(
            listOf(testItem("1"), testItem("2"), testItem("3")),
            startIndex = 0,
        )
        kotlinx.coroutines.delay(500)

        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("2", controller.playerState.value.currentItem?.id)

        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("3", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun skipToPreviousGoesBack() = runTest {
        controller.playQueue(
            listOf(testItem("1"), testItem("2"), testItem("3")),
            startIndex = 2,
        )
        kotlinx.coroutines.delay(500)

        controller.skipToPrevious()
        kotlinx.coroutines.delay(200)
        assertEquals("2", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun repeatModeAffectsNavigation() = runTest {
        controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 1)
        kotlinx.coroutines.delay(500)

        controller.setRepeatMode(RepeatMode.OFF)
        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("2", controller.playerState.value.currentItem?.id)

        controller.setRepeatMode(RepeatMode.ALL)
        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("1", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun shuffleToggleShufflesAndRestoresQueue() = runTest {
        val items = (1..10).map { testItem(it.toString()) }
        controller.playQueue(items, startIndex = 0)
        kotlinx.coroutines.delay(200)

        controller.toggleShuffle()
        assertTrue(controller.playerState.value.shuffleEnabled)
        assertEquals("1", controller.playerState.value.currentItem?.id)

        controller.toggleShuffle()
        assertFalse(controller.playerState.value.shuffleEnabled)
        assertEquals(items.map { it.id }, controller.playQueue.items.map { it.id })
    }
}
