package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val playlistRepository: PlaylistRepository = mock()
    private val playerStateFlow = MutableStateFlow(PlayerState.EMPTY)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerState).thenReturn(playerStateFlow)
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PlayerViewModel(playerController, playlistRepository)

    @Test
    fun `playerState reflects controller state`() = runTest {
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify(playerController).play()
    }

    @Test
    fun `togglePlayPause calls pause when playing`() = runTest {
        playerStateFlow.value = PlayerState.EMPTY.copy(isPlaying = true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify(playerController).pause()
    }

    @Test
    fun `skipToNext calls controller`() {
        val viewModel = createViewModel()
        viewModel.skipToNext()
        verify(playerController).skipToNext()
    }

    @Test
    fun `skipToPrevious calls controller`() {
        val viewModel = createViewModel()
        viewModel.skipToPrevious()
        verify(playerController).skipToPrevious()
    }

    @Test
    fun `seekTo calls controller`() {
        val viewModel = createViewModel()
        viewModel.seekTo(5000L)
        verify(playerController).seekTo(5000L)
    }

    @Test
    fun `cycleRepeatMode calls controller`() {
        val viewModel = createViewModel()
        viewModel.cycleRepeatMode()
        verify(playerController).cycleRepeatMode()
    }

    @Test
    fun `toggleShuffle calls controller`() {
        val viewModel = createViewModel()
        viewModel.toggleShuffle()
        verify(playerController).toggleShuffle()
    }

    @Test
    fun `isCurrentFavorite is false when no current item`() = runTest {
        playerStateFlow.value = PlayerState.EMPTY
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.isCurrentFavorite.value)
    }

    @Test
    fun `isCurrentFavorite reflects repository when item present`() = runTest {
        val item = MusicItem(id = "1", platform = "local", title = "Song", artist = "A", album = null, duration = 180_000L, url = null, artwork = null, qualities = null)
        val favFlow = MutableStateFlow(false)
        whenever(playlistRepository.isFavorite(item)).thenReturn(favFlow)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        // Subscribe to activate WhileSubscribed flow
        val collected = mutableListOf<Boolean>()
        val job = backgroundScope.launch { viewModel.isCurrentFavorite.toList(collected) }
        advanceUntilIdle()

        assertFalse(viewModel.isCurrentFavorite.value)

        favFlow.value = true
        advanceUntilIdle()

        assertTrue(viewModel.isCurrentFavorite.value)
        job.cancel()
    }

    @Test
    fun `showAddToPlaylistSheet sets visible with current item`() = runTest {
        val item = MusicItem(id = "2", platform = "local", title = "Track", artist = "B", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddToPlaylistSheet()

        val s = viewModel.sheetState.value
        assertTrue(s.visible)
        assertEquals(item, s.pendingItem)
        assertEquals(listOf(item), s.pendingItems)
    }

    @Test
    fun `hideAddToPlaylistSheet clears state`() = runTest {
        val item = MusicItem(id = "2", platform = "local", title = "Track", artist = "B", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddToPlaylistSheet()
        viewModel.hideAddToPlaylistSheet()

        assertFalse(viewModel.sheetState.value.visible)
    }

    @Test
    fun `allPlaylists reflects repository`() = runTest {
        val playlistsFlow = MutableStateFlow(listOf(Playlist(id = "p1", name = "My List", coverUri = null)))
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(playlistsFlow)

        val viewModel = createViewModel()
        // Subscribe to activate WhileSubscribed flow
        val job = backgroundScope.launch { viewModel.allPlaylists.collect {} }
        advanceUntilIdle()

        assertEquals(1, viewModel.allPlaylists.value.size)
        assertEquals("My List", viewModel.allPlaylists.value[0].name)
        job.cancel()
    }
}
