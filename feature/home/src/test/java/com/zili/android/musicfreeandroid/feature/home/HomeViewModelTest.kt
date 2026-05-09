package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val appPreferences: AppPreferences = mock()
    private val downloader: Downloader = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
        whenever(appPreferences.defaultDownloadQuality).thenReturn(flowOf(PlayQuality.STANDARD))
        whenever(downloader.tasks).thenReturn(MutableStateFlow(emptyList()))
        whenever(downloader.downloadedKeys).thenReturn(MutableStateFlow(emptySet()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading before scan`() = runTest {
        whenever(appPreferences.storageDirectoryUri).thenReturn(flowOf(null))

        val viewModel = createViewModel()
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `scanLocalMusic updates state to Success`() = runTest {
        val items = listOf(
            MusicItem(id = "1", platform = "local", title = "Song 1", artist = "Artist", album = "Album", duration = 180_000L, url = null, artwork = null, qualities = null),
        )
        whenever(appPreferences.storageDirectoryUri).thenReturn(flowOf(null))
        whenever(scanner.scan(null)).thenReturn(flowOf(items))

        val viewModel = createViewModel()
        viewModel.scanLocalMusic()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertEquals(1, (state as HomeUiState.Success).musicItems.size)
    }

    @Test
    fun `scanLocalMusic uses configured storage directory when available`() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"
        whenever(appPreferences.storageDirectoryUri).thenReturn(flowOf(treeUri))
        whenever(scanner.scan(treeUri)).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        viewModel.scanLocalMusic()
        advanceUntilIdle()

        verify(scanner).scan(treeUri)
    }

    @Test
    fun `playItem calls playerController playQueue`() = runTest {
        val items = listOf(
            MusicItem(id = "1", platform = "local", title = "Song 1", artist = "Artist", album = "Album", duration = 180_000L, url = null, artwork = null, qualities = null),
        )
        whenever(appPreferences.storageDirectoryUri).thenReturn(flowOf(null))
        whenever(scanner.scan(null)).thenReturn(flowOf(items))

        val viewModel = createViewModel()
        viewModel.scanLocalMusic()
        advanceUntilIdle()

        viewModel.playItem(items[0], items)
        verify(playerController).playQueue(items, 0)
    }

    @Test
    fun `download enqueues selected item with requested quality`() = runTest {
        whenever(appPreferences.storageDirectoryUri).thenReturn(flowOf(null))
        val item = MusicItem(id = "1", platform = "local", title = "Song 1", artist = "Artist", album = "Album", duration = 180_000L, url = null, artwork = null, qualities = null)

        val viewModel = createViewModel()

        viewModel.download(item, PlayQuality.HIGH)

        verify(downloader).enqueue(listOf(item), PlayQuality.HIGH)
    }

    private fun createViewModel(): HomeViewModel = HomeViewModel(
        scanner = scanner,
        playerController = playerController,
        playlistRepository = playlistRepository,
        musicRepository = musicRepository,
        appPreferences = appPreferences,
        downloader = downloader,
    )
}
