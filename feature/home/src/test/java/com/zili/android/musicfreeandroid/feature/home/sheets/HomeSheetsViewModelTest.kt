package com.zili.android.musicfreeandroid.feature.home.sheets

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.data.repository.StarredSheetRepository
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSheetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playlistRepository: PlaylistRepository = mock()
    private val starredSheetRepository: StarredSheetRepository = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switchTab exposes starred rows without mutating mine rows`() = runTest {
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(listOf(
            Playlist(id = "pl-1", name = "Mine A", coverUri = null),
        )))
        whenever(playlistRepository.countMusicInPlaylist("pl-1")).thenReturn(12)
        whenever(starredSheetRepository.observeAll()).thenReturn(flowOf(listOf(
            StarredSheet(id = "sheet-1", platform = "demo", title = "Starred A", artist = "Demo", coverUri = null, sourceUrl = null),
        )))

        val viewModel = HomeSheetsViewModel(playlistRepository, starredSheetRepository)
        advanceUntilIdle()
        assertEquals(HomeSheetTab.Mine, viewModel.uiState.value.selectedTab)
        assertEquals(1, viewModel.uiState.value.mineCount)
        assertEquals(1, viewModel.uiState.value.starredCount)
        assertEquals("12首", viewModel.uiState.value.items.single().subtitle)
        val mineTitles = viewModel.uiState.value.items.map { it.title }

        viewModel.selectTab(HomeSheetTab.Starred)
        advanceUntilIdle()

        assertEquals(HomeSheetTab.Starred, viewModel.uiState.value.selectedTab)
        assertEquals(1, viewModel.uiState.value.mineCount)
        assertEquals(1, viewModel.uiState.value.starredCount)
        assertEquals(listOf("Starred A"), viewModel.uiState.value.items.map { it.title })
        assertEquals("sheet-1", viewModel.uiState.value.items.single().id)
        assertEquals("demo", viewModel.uiState.value.items.single().platform)

        viewModel.selectTab(HomeSheetTab.Mine)
        advanceUntilIdle()

        assertEquals(HomeSheetTab.Mine, viewModel.uiState.value.selectedTab)
        assertEquals(1, viewModel.uiState.value.mineCount)
        assertEquals(1, viewModel.uiState.value.starredCount)
        assertEquals(mineTitles, viewModel.uiState.value.items.map { it.title })
        assertEquals("12首", viewModel.uiState.value.items.single().subtitle)
    }
}
