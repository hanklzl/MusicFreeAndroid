package com.zili.android.musicfreeandroid.feature.home.local

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.DownloadFailReason
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
class LocalMusicViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val scanner: LocalMusicScanner = mock()
    private val playerController: PlayerController = mock()
    private val musicRepository: MusicRepository = mock()
    private val appPreferences: AppPreferences = mock()
    private val downloader: Downloader = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
            .thenReturn(MutableStateFlow(emptyList()))
        whenever(appPreferences.defaultDownloadQuality).thenReturn(flowOf(PlayQuality.STANDARD))
        whenever(downloader.tasks).thenReturn(MutableStateFlow(emptyList()))
        whenever(downloader.downloadedKeys).thenReturn(MutableStateFlow(emptySet()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `items come from persisted local repository`() = runTest(testDispatcher) {
        val items = listOf(track("1"))
        whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
            .thenReturn(MutableStateFlow(items))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LocalMusicUiState.Success)
        assertEquals(items, (state as LocalMusicUiState.Success).musicItems)
        verify(musicRepository).observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)
    }

    @Test
    fun `scanLocalMusic persists scanned items`() = runTest(testDispatcher) {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val items = listOf(track("1"), track("2"))
        whenever(scanner.scan(treeUri)).thenReturn(flowOf(items))

        val viewModel = createViewModel()
        viewModel.scanLocalMusic(treeUri)
        advanceUntilIdle()

        verify(scanner).scan(treeUri)
        verify(musicRepository).insertAll(items)
    }

    @Test
    fun `scanLocalMusic exits Loading when scanner emits empty list without repository update`() = runTest(testDispatcher) {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AEmpty"
        val repositoryItems = MutableStateFlow(emptyList<MusicItem>())
        whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
            .thenReturn(repositoryItems)
        whenever(scanner.scan(treeUri)).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.scanLocalMusic(treeUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LocalMusicUiState.Success)
        assertEquals(emptyList<MusicItem>(), (state as LocalMusicUiState.Success).musicItems)
    }

    @Test
    fun `removeFromLocalLibrary deletes persisted item`() = runTest(testDispatcher) {
        val item = track("1")

        val viewModel = createViewModel()
        viewModel.removeFromLocalLibrary(item)
        advanceUntilIdle()

        verify(musicRepository).delete(item)
    }

    @Test
    fun `playItem plays selected item in current list`() = runTest(testDispatcher) {
        val items = listOf(track("1"), track("2"), track("3"))

        val viewModel = createViewModel()
        viewModel.playItem(items[1], items)

        verify(playerController).playQueue(items, 1)
    }

    @Test
    fun `download enqueues selected item with requested quality`() = runTest(testDispatcher) {
        val item = track("1")

        val viewModel = createViewModel()
        viewModel.download(item, PlayQuality.HIGH)

        verify(downloader).enqueue(listOf(item), PlayQuality.HIGH)
    }

    @Test
    fun `download state mirrors downloader and preferences`() = runTest(testDispatcher) {
        val downloaded = setOf(MediaKey.of("1", LocalMusicScanner.PLATFORM_LOCAL))
        whenever(downloader.downloadedKeys).thenReturn(MutableStateFlow(downloaded))
        whenever(downloader.tasks)
            .thenReturn(
                MutableStateFlow(
                    listOf(
                        downloadTask("1", DownloadStatus.DOWNLOADING),
                        downloadTask("2", DownloadStatus.FAILED),
                    )
                )
            )
        whenever(appPreferences.defaultDownloadQuality).thenReturn(flowOf(PlayQuality.HIGH))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.downloadActiveCount.collect() }
        advanceUntilIdle()

        assertEquals(1, viewModel.downloadActiveCount.value)
        assertEquals(downloaded, viewModel.downloadedKeys.value)
        assertEquals(PlayQuality.HIGH, viewModel.defaultDownloadQuality.first())
    }

    private fun createViewModel(): LocalMusicViewModel = LocalMusicViewModel(
        scanner = scanner,
        playerController = playerController,
        musicRepository = musicRepository,
        appPreferences = appPreferences,
        downloader = downloader,
    )

    private fun track(
        id: String,
        title: String = "Song $id",
    ): MusicItem = MusicItem(
        id = id,
        platform = LocalMusicScanner.PLATFORM_LOCAL,
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
    )

    private fun downloadTask(id: String, status: DownloadStatus): DownloadTaskUi = DownloadTaskUi(
        key = MediaKey.of(id, LocalMusicScanner.PLATFORM_LOCAL),
        title = "Song $id",
        artist = "Artist $id",
        artwork = null,
        status = status,
        targetQuality = PlayQuality.STANDARD.name,
        downloadedBytes = null,
        totalBytes = null,
        errorReason = DownloadFailReason.NetworkOffline,
    )
}
