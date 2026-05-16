package com.hank.musicfree.feature.home.searchmusiclist

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.navigation.SearchMusicListRoute
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.feature.home.collection.CollectionSource
import com.hank.musicfree.player.controller.PlayerController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchMusicListSourceLoaderTest {

    private val playlistRepository: PlaylistRepository = mock()
    private val musicRepository: MusicRepository = mock()
    private val playerController: PlayerController = mock()

    @Test
    fun `playlist source loads playlist items from repository`() = runTest {
        val playlistItems = listOf(track(id = "playlist-song"))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(flowOf(playlistItems))

        val loader = SearchMusicListSourceLoader(playlistRepository, playerController, musicRepository)

        val actual = loader.observe(SearchMusicListRoute.playlist("playlist-1")).first()

        assertEquals(playlistItems, actual)
        verify(playlistRepository).observeMusicInPlaylist("playlist-1")
    }

    @Test
    fun `history source loads current play history from player controller`() = runTest {
        val historyItems = listOf(track(id = "history-song"))
        whenever(playerController.playHistory).thenReturn(MutableStateFlow(historyItems))

        val loader = SearchMusicListSourceLoader(playlistRepository, playerController, musicRepository)

        val actual = loader.observe(SearchMusicListRoute.history()).first()

        assertEquals(historyItems, actual)
    }

    @Test
    fun `local library source uses unified local library repository`() = runTest {
        val localItems = listOf(track(id = "local-song", platform = "plugin-platform"))
        whenever(musicRepository.observeLocalLibrary())
            .thenReturn(flowOf(localItems))

        val loader = SearchMusicListSourceLoader(playlistRepository, playerController, musicRepository)

        val actual = loader.observe(CollectionSource.LocalLibrary).first()

        assertEquals(localItems, actual)
        verify(musicRepository).observeLocalLibrary()
    }

    @Test
    fun `transient source returns empty list in minimal foundation implementation`() = runTest {
        val loader = SearchMusicListSourceLoader(playlistRepository, playerController, musicRepository)

        val actual = loader.observe(CollectionSource.Transient(sourceId = "session-42")).first()

        assertEquals(emptyList<MusicItem>(), actual)
    }

    private fun track(
        id: String,
        title: String = "Song $id",
        artist: String = "Artist $id",
        album: String = "Album $id",
        platform: String = "demo",
    ) = MusicItem(
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
