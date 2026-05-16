package com.hank.musicfree.feature.home.sheets

import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.core.model.StarredKind
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.data.repository.StarredSheetRepository
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
import org.mockito.kotlin.verify
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

    @Test
    fun `unstar invokes repository deleteByIdAndPlatform for starred row`() = runTest {
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
        whenever(starredSheetRepository.observeAll()).thenReturn(flowOf(listOf(
            com.hank.musicfree.core.model.StarredSheet(
                id = "alb-1", platform = "qq",
                title = "AlbumOne", artist = null, coverUri = null, sourceUrl = null,
                kind = com.hank.musicfree.core.model.StarredKind.ALBUM,
            ),
        )))

        val viewModel = HomeSheetsViewModel(playlistRepository, starredSheetRepository)
        advanceUntilIdle()
        viewModel.selectTab(com.hank.musicfree.feature.home.sheets.HomeSheetTab.Starred)
        advanceUntilIdle()

        val row = viewModel.uiState.value.items.single()
        viewModel.unstar(row)
        advanceUntilIdle()

        verify(starredSheetRepository).deleteByIdAndPlatform(id = "alb-1", platform = "qq")
    }

    @Test
    fun `unstar ignores rows without platform`() = runTest {
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
        whenever(starredSheetRepository.observeAll()).thenReturn(flowOf(emptyList()))

        val viewModel = HomeSheetsViewModel(playlistRepository, starredSheetRepository)
        advanceUntilIdle()

        val mineRow = HomeSheetUiModel(
            id = "fav", platform = null, tab = com.hank.musicfree.feature.home.sheets.HomeSheetTab.Mine,
            title = "我喜欢", subtitle = "0首", coverUri = null,
        )
        viewModel.unstar(mineRow)
        advanceUntilIdle()

        org.mockito.kotlin.verify(starredSheetRepository, org.mockito.kotlin.never())
            .deleteByIdAndPlatform(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
}
