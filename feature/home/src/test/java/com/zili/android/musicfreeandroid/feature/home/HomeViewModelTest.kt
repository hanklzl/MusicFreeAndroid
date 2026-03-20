package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val scanner: LocalMusicScanner = mock()
    private val playerController: PlayerController = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val musicRepository: MusicRepository = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading before scan`() = runTest {
        val viewModel = HomeViewModel(scanner, playerController, playlistRepository, musicRepository)
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `scanLocalMusic updates state to Success`() = runTest {
        val items = listOf(
            MusicItem(id = "1", platform = "local", title = "Song 1", artist = "Artist", album = "Album", duration = 180_000L, url = null, artwork = null, qualities = null),
        )
        whenever(scanner.scan()).thenReturn(flowOf(items))

        val viewModel = HomeViewModel(scanner, playerController, playlistRepository, musicRepository)
        viewModel.scanLocalMusic()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertEquals(1, (state as HomeUiState.Success).musicItems.size)
    }

    @Test
    fun `playItem calls playerController playQueue`() = runTest {
        val items = listOf(
            MusicItem(id = "1", platform = "local", title = "Song 1", artist = "Artist", album = "Album", duration = 180_000L, url = null, artwork = null, qualities = null),
        )
        whenever(scanner.scan()).thenReturn(flowOf(items))

        val viewModel = HomeViewModel(scanner, playerController, playlistRepository, musicRepository)
        viewModel.scanLocalMusic()
        advanceUntilIdle()

        viewModel.playItem(items[0], items)
        verify(playerController).playQueue(items, 0)
    }
}
