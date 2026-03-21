package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
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
class SearchMusicListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playlistRepository: PlaylistRepository = mock()
    private val playerController: PlayerController = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `playlist source filters items locally by trimmed case insensitive query`() = runTest {
        val items = listOf(
            track(id = "1", title = "Alpha", artist = "First Artist"),
            track(id = "2", title = "Bravo", artist = "Second Artist"),
        )
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(MutableStateFlow(items))
        val viewModel = SearchMusicListViewModel(
            route = SearchMusicListRoute.playlist("playlist-1"),
            sourceLoader = SearchMusicListSourceLoader(playlistRepository, playerController),
            playerController = playerController,
        )

        advanceUntilIdle()
        viewModel.updateQuery("  bRaVo  ")
        advanceUntilIdle()

        assertEquals(listOf(items[1]), viewModel.uiState.value.filteredItems)
    }

    @Test
    fun `history source keeps full list when query is blank`() = runTest {
        val historyItems = listOf(
            track(id = "1", title = "Alpha"),
            track(id = "2", title = "Beta"),
        )
        whenever(playerController.playHistory).thenReturn(MutableStateFlow(historyItems))
        val viewModel = SearchMusicListViewModel(
            route = SearchMusicListRoute.history(),
            sourceLoader = SearchMusicListSourceLoader(playlistRepository, playerController),
            playerController = playerController,
        )

        advanceUntilIdle()
        viewModel.updateQuery("   ")
        advanceUntilIdle()

        assertEquals(historyItems, viewModel.uiState.value.filteredItems)
    }

    @Test
    fun `playFilteredItem plays tapped row within filtered list`() = runTest {
        val historyItems = listOf(
            track(id = "1", title = "Alpha", artist = "Band A"),
            track(id = "2", title = "Beta", artist = "Band B"),
            track(id = "3", title = "Gamma", artist = "Band B"),
        )
        whenever(playerController.playHistory).thenReturn(MutableStateFlow(historyItems))
        val viewModel = SearchMusicListViewModel(
            route = SearchMusicListRoute.history(),
            sourceLoader = SearchMusicListSourceLoader(playlistRepository, playerController),
            playerController = playerController,
        )

        advanceUntilIdle()
        viewModel.updateQuery("band b")
        advanceUntilIdle()

        assertTrue(viewModel.playFilteredItem(index = 1))
        verify(playerController).playQueue(listOf(historyItems[1], historyItems[2]), 1)
    }

    private fun track(
        id: String,
        title: String = "Song $id",
        artist: String = "Artist $id",
        album: String = "Album $id",
        platform: String = "demo",
    ): MusicItem = MusicItem(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        album = album,
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
