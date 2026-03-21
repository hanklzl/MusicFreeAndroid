package com.zili.android.musicfreeandroid.feature.home.musiclisteditor

import androidx.lifecycle.SavedStateHandle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
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

        val viewModel = MusicListEditorLiteViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to "playlist-1")),
            playlistRepository = playlistRepository,
            playerController = playerController,
        )
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

        val viewModel = MusicListEditorLiteViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to "playlist-1")),
            playlistRepository = playlistRepository,
            playerController = playerController,
        )
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

        val viewModel = MusicListEditorLiteViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to "playlist-1")),
            playlistRepository = playlistRepository,
            playerController = playerController,
        )
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

        val viewModel = MusicListEditorLiteViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to "playlist-1")),
            playlistRepository = playlistRepository,
            playerController = playerController,
        )
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
