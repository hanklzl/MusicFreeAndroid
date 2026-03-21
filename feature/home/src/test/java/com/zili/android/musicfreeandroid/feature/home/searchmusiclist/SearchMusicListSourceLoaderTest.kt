package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListSource
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
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
    private val playerController: PlayerController = mock()

    @Test
    fun `playlist source loads playlist items from repository`() = runTest {
        val playlistItems = listOf(track(id = "playlist-song"))
        whenever(playlistRepository.observeMusicInPlaylist("playlist-1"))
            .thenReturn(flowOf(playlistItems))

        val loader = SearchMusicListSourceLoader(playlistRepository, playerController)

        val actual = loader.observe(SearchMusicListSource.Playlist("playlist-1")).first()

        assertEquals(playlistItems, actual)
        verify(playlistRepository).observeMusicInPlaylist("playlist-1")
    }

    @Test
    fun `history source loads current play history from player controller`() = runTest {
        val historyItems = listOf(track(id = "history-song"))
        whenever(playerController.playHistory).thenReturn(MutableStateFlow(historyItems))

        val loader = SearchMusicListSourceLoader(playlistRepository, playerController)

        val actual = loader.observe(SearchMusicListSource.History).first()

        assertEquals(historyItems, actual)
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
