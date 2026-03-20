package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playerController: PlayerController = mock()
    private val playerStateFlow = MutableStateFlow(PlayerState.EMPTY)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerState).thenReturn(playerStateFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `playerState reflects controller state`() = runTest {
        val viewModel = PlayerViewModel(playerController)
        advanceUntilIdle()

        assertEquals(PlayerState.EMPTY, viewModel.playerState.value)

        val item = MusicItem(id = "1", platform = "local", title = "Song", artist = "A", album = null, duration = 180_000L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, isPlaying = true)
        advanceUntilIdle()

        assertTrue(viewModel.playerState.value.isPlaying)
        assertEquals("Song", viewModel.playerState.value.currentItem?.title)
    }

    @Test
    fun `togglePlayPause calls play when paused`() = runTest {
        playerStateFlow.value = PlayerState.EMPTY.copy(isPlaying = false)
        val viewModel = PlayerViewModel(playerController)
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify(playerController).play()
    }

    @Test
    fun `togglePlayPause calls pause when playing`() = runTest {
        playerStateFlow.value = PlayerState.EMPTY.copy(isPlaying = true)
        val viewModel = PlayerViewModel(playerController)
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify(playerController).pause()
    }

    @Test
    fun `skipToNext calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.skipToNext()
        verify(playerController).skipToNext()
    }

    @Test
    fun `skipToPrevious calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.skipToPrevious()
        verify(playerController).skipToPrevious()
    }

    @Test
    fun `seekTo calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.seekTo(5000L)
        verify(playerController).seekTo(5000L)
    }

    @Test
    fun `cycleRepeatMode calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.cycleRepeatMode()
        verify(playerController).cycleRepeatMode()
    }

    @Test
    fun `toggleShuffle calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.toggleShuffle()
        verify(playerController).toggleShuffle()
    }
}
