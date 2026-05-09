package com.zili.android.musicfreeandroid.feature.home.musiclisteditor

import androidx.lifecycle.SavedStateHandle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.navigation.MusicListEditorLiteRoute
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MusicListEditorLiteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playlistRepository: PlaylistRepository = mock()
    private val musicRepository: MusicRepository = mock()
    private val playerController: PlayerController = mock()
    private val downloader: Downloader = mock()
    private val appPreferences: AppPreferences = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(appPreferences.defaultDownloadQuality).thenReturn(flowOf(PlayQuality.HIGH))
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(MutableStateFlow(emptyList()))
        whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
            .thenReturn(MutableStateFlow(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `removeSelected stages deletions until save`() = runTest {
        val playlistItems = MutableStateFlow(
            listOf(
                track(id = "1"),
                track(id = "2"),
                track(id = "3"),
            )
        )
        whenever(playlistRepository.getPlaylistById("playlist-1"))
            .thenReturn(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(playlistItems)
        whenever(playlistRepository.observeAllPlaylists())
            .thenReturn(
                MutableStateFlow(
                    listOf(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
                )
            )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection(playlistItems.value[0])
        viewModel.toggleSelection(playlistItems.value[2])
        viewModel.removeSelectedFromPlaylist()
        advanceUntilIdle()

        assertEquals(listOf(playlistItems.value[1]), viewModel.uiState.value.items)
        assertTrue(viewModel.uiState.value.hasPendingChanges)
        assertEquals(0, viewModel.uiState.value.selectedCount)
        verify(playlistRepository, never()).removeMusicFromPlaylist("playlist-1", playlistItems.value[0])
        verify(playlistRepository, never()).removeMusicFromPlaylist("playlist-1", playlistItems.value[2])

        viewModel.saveChanges()
        advanceUntilIdle()

        verify(playlistRepository).removeMusicFromPlaylist("playlist-1", playlistItems.value[0])
        verify(playlistRepository).removeMusicFromPlaylist("playlist-1", playlistItems.value[2])
        assertEquals(listOf(playlistItems.value[1]), viewModel.uiState.value.items)
        assertFalse(viewModel.uiState.value.hasPendingChanges)
    }

    @Test
    fun `save reconciles staged deletions against latest playlist snapshot`() = runTest {
        val playlistItems = MutableStateFlow(
            listOf(
                track(id = "1"),
                track(id = "2"),
                track(id = "3"),
            )
        )
        whenever(playlistRepository.getPlaylistById("playlist-1"))
            .thenReturn(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(playlistItems)
        whenever(playlistRepository.observeAllPlaylists())
            .thenReturn(
                MutableStateFlow(
                    listOf(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
                )
            )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection(playlistItems.value[0])
        viewModel.removeSelectedFromPlaylist()
        advanceUntilIdle()

        val externalTrack = track(id = "4")
        playlistItems.value = playlistItems.value + externalTrack
        advanceUntilIdle()

        assertEquals(
            listOf(
                playlistItems.value[1],
                playlistItems.value[2],
                externalTrack,
            ),
            viewModel.uiState.value.items,
        )
        assertTrue(viewModel.uiState.value.hasPendingChanges)

        viewModel.saveChanges()
        advanceUntilIdle()

        verify(playlistRepository).removeMusicFromPlaylist("playlist-1", track(id = "1"))
        verify(playlistRepository, never()).removeMusicFromPlaylist("playlist-1", externalTrack)
        assertEquals(
            listOf(
                track(id = "2"),
                track(id = "3"),
                externalTrack,
            ),
            viewModel.uiState.value.items,
        )
        assertFalse(viewModel.uiState.value.hasPendingChanges)
    }

    @Test
    fun `addSelectedToNextQueue preserves selected order in upcoming queue`() = runTest {
        val items = listOf(
            track(id = "1"),
            track(id = "2"),
            track(id = "3"),
        )
        whenever(playlistRepository.getPlaylistById("playlist-1"))
            .thenReturn(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(MutableStateFlow(items))
        whenever(playlistRepository.observeAllPlaylists())
            .thenReturn(
                MutableStateFlow(
                    listOf(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
                )
            )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection(items[0])
        viewModel.toggleSelection(items[1])
        viewModel.addSelectedToNextQueue()

        inOrder(playerController) {
            verify(playerController).addNextInQueue(items[1])
            verify(playerController).addNextInQueue(items[0])
        }
    }

    @Test
    fun `selectAll and clearSelection update selected count`() = runTest {
        val items = listOf(
            track(id = "1"),
            track(id = "2"),
        )
        whenever(playlistRepository.getPlaylistById("playlist-1"))
            .thenReturn(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(MutableStateFlow(items))
        whenever(playlistRepository.observeAllPlaylists())
            .thenReturn(
                MutableStateFlow(
                    listOf(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
                )
            )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectAll()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.selectedCount)

        viewModel.clearSelection()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.selectedCount)
    }

    @Test
    fun `addSelectedToPlaylist uses source order and excludes current playlist from targets`() = runTest {
        val items = listOf(
            track(id = "1"),
            track(id = "2"),
        )
        whenever(playlistRepository.getPlaylistById("playlist-1"))
            .thenReturn(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(MutableStateFlow(items))
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(
            MutableStateFlow(
                listOf(
                    Playlist(id = "playlist-1", name = "Favorites", coverUri = null),
                    Playlist(id = "playlist-2", name = "Road Trip", coverUri = null),
                    Playlist(id = "playlist-3", name = "Late Night", coverUri = null),
                )
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("playlist-2", "playlist-3"),
            viewModel.uiState.value.availableTargetPlaylists.map { it.id },
        )

        viewModel.selectAll()
        viewModel.addSelectedToPlaylist("playlist-2")
        advanceUntilIdle()

        inOrder(playlistRepository) {
            verify(playlistRepository).addMusicToPlaylist("playlist-2", items[0])
            verify(playlistRepository).addMusicToPlaylist("playlist-2", items[1])
        }
    }

    @Test
    fun `downloadSelected enqueues selected items in display order with default quality`() = runTest {
        val items = listOf(
            track(id = "1"),
            track(id = "2"),
            track(id = "3"),
        )
        whenever(playlistRepository.getPlaylistById("playlist-1"))
            .thenReturn(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(MutableStateFlow(items))
        whenever(playlistRepository.observeAllPlaylists())
            .thenReturn(
                MutableStateFlow(
                    listOf(Playlist(id = "playlist-1", name = "Favorites", coverUri = null))
                )
            )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection(items[2])
        viewModel.toggleSelection(items[0])
        viewModel.downloadSelected()
        advanceUntilIdle()

        verify(downloader).enqueue(listOf(items[0], items[2]), PlayQuality.HIGH)
    }

    @Test
    fun `local library route loads persisted local music with local title`() = runTest {
        val items = listOf(
            track(id = "1", platform = LocalMusicScanner.PLATFORM_LOCAL),
            track(id = "2", platform = LocalMusicScanner.PLATFORM_LOCAL),
        )
        whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
            .thenReturn(MutableStateFlow(items))
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(
            MutableStateFlow(
                listOf(Playlist(id = "playlist-2", name = "Road Trip", coverUri = null))
            )
        )

        val viewModel = createLocalLibraryViewModel()
        advanceUntilIdle()

        assertEquals("本地音乐", viewModel.uiState.value.playlistName)
        assertEquals(items, viewModel.uiState.value.items)
        assertEquals(
            listOf("playlist-2"),
            viewModel.uiState.value.availableTargetPlaylists.map { it.id },
        )
        verify(musicRepository).observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)
        verify(playlistRepository, never()).observeMusicInPlaylist("local")
    }

    @Test
    fun `local library save deletes removed items from music repository`() = runTest {
        val items = listOf(
            track(id = "1", platform = LocalMusicScanner.PLATFORM_LOCAL),
            track(id = "2", platform = LocalMusicScanner.PLATFORM_LOCAL),
        )
        whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
            .thenReturn(MutableStateFlow(items))

        val viewModel = createLocalLibraryViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection(items[0])
        viewModel.removeSelectedFromPlaylist()
        viewModel.saveChanges()
        advanceUntilIdle()

        verify(musicRepository).delete(items[0])
        verify(playlistRepository, never()).removeMusicFromPlaylist("local", items[0])
        assertEquals(listOf(items[1]), viewModel.uiState.value.items)
        assertFalse(viewModel.uiState.value.hasPendingChanges)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("playlistId" to "playlist-1")),
    ): MusicListEditorLiteViewModel = MusicListEditorLiteViewModel(
        savedStateHandle = savedStateHandle,
        playlistRepository = playlistRepository,
        musicRepository = musicRepository,
        playerController = playerController,
        downloader = downloader,
        appPreferences = appPreferences,
    )

    private fun createLocalLibraryViewModel(): MusicListEditorLiteViewModel = createViewModel(
        SavedStateHandle(
            mapOf(
                "sourceType" to MusicListEditorLiteRoute.SOURCE_TYPE_LOCAL_LIBRARY,
                "sourceId" to MusicListEditorLiteRoute.LOCAL_LIBRARY_SOURCE_ID,
            )
        )
    )

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
