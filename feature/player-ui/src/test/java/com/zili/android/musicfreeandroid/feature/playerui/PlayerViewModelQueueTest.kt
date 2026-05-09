package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueUiModel
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricLoadState
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricLoader
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelQueueTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playerController: PlayerController = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val playerLyricLoader: PlayerLyricLoader = mock()
    private val appPreferences: AppPreferences = mock()
    private val playerStateFlow = MutableStateFlow(PlayerState.EMPTY)
    private val queueStateFlow = MutableStateFlow(PlayQueueSnapshot.EMPTY)
    private val lyricShowTranslationFlow = MutableStateFlow(false)
    private val lyricDetailFontSizeFlow = MutableStateFlow(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerState).thenReturn(playerStateFlow)
        whenever(playerController.queueState).thenReturn(queueStateFlow)
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
        whenever(playerLyricLoader.observeLyrics(anyOrNull()))
            .thenReturn(flowOf(LyricLoadState.NoTrack))
        whenever(appPreferences.lyricShowTranslation).thenReturn(lyricShowTranslationFlow)
        whenever(appPreferences.lyricDetailFontSize).thenReturn(lyricDetailFontSizeFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlayerViewModel(
        playerController,
        playlistRepository,
        playerLyricLoader,
        appPreferences,
    )

    private fun item(id: String) = MusicItem(
        id = id, platform = "test", title = "Song $id",
        artist = "Artist", album = null, duration = 1_000L,
        url = null, artwork = null, qualities = null,
    )

    @Test
    fun `queueUiModel initial value is EMPTY`() = runTest(testDispatcher) {
        val vm = viewModel()
        // Trigger collection to start the stateIn pipeline.
        val collector = backgroundScope.launch { vm.queueUiModel.collect { } }
        advanceUntilIdle()
        assertEquals(PlayQueueUiModel.EMPTY, vm.queueUiModel.value)
        collector.cancel()
    }

    @Test
    fun `queueUiModel reflects queueState items and currentIndex`() = runTest(testDispatcher) {
        val vm = viewModel()
        val collector = backgroundScope.launch { vm.queueUiModel.collect { } }
        val items = listOf(item("1"), item("2"))
        queueStateFlow.value = PlayQueueSnapshot(items = items, currentIndex = 1)
        advanceUntilIdle()
        val ui = vm.queueUiModel.value
        assertEquals(items, ui.items)
        assertEquals(1, ui.currentIndex)
        assertEquals(2, ui.count)
        collector.cancel()
    }

    @Test
    fun `queueUiModel includes repeatMode from playerState`() = runTest(testDispatcher) {
        val vm = viewModel()
        val collector = backgroundScope.launch { vm.queueUiModel.collect { } }
        playerStateFlow.value = PlayerState.EMPTY.copy(repeatMode = RepeatMode.ONE)
        advanceUntilIdle()
        assertEquals(RepeatMode.ONE, vm.queueUiModel.value.repeatMode)
        collector.cancel()
    }

    @Test
    fun `playQueueIndex delegates to PlayerController_skipTo`() {
        viewModel().playQueueIndex(2)
        verify(playerController).skipTo(eq(2))
    }

    @Test
    fun `removeFromQueue delegates to PlayerController_removeFromQueue`() {
        viewModel().removeFromQueue(1)
        verify(playerController).removeFromQueue(eq(1))
    }

    @Test
    fun `clearQueue delegates to PlayerController_reset`() {
        viewModel().clearQueue()
        verify(playerController).reset()
    }
}
