package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.lyric.LyricTiming
import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricLoadState
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricLoader
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playerController: PlayerController = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val playerLyricLoader: PlayerLyricLoader = mock()
    private val appPreferences: AppPreferences = mock()
    private val lyricShowTranslationFlow = MutableStateFlow(false)
    private val lyricDetailFontSizeFlow = MutableStateFlow(1)
    private val playerStateFlow = MutableStateFlow(PlayerState.EMPTY)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerState).thenReturn(playerStateFlow)
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
        whenever(playerLyricLoader.observeLyrics(anyOrNull())).thenReturn(flowOf(LyricLoadState.NoTrack))
        whenever(appPreferences.lyricShowTranslation).thenReturn(lyricShowTranslationFlow)
        whenever(appPreferences.lyricDetailFontSize).thenReturn(lyricDetailFontSizeFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PlayerViewModel(
        playerController,
        playlistRepository,
        playerLyricLoader,
        appPreferences,
    )

    private fun readyLyricState(item: MusicItem, lines: List<ParsedLyricLine>): LyricLoadState.Ready {
        val document = LyricDocument(
            musicId = item.id,
            musicPlatform = item.platform,
            lines = lines,
            source = LyricSourceInfo.Plugin(item.platform),
            metaOffsetMs = 500L,
            rawLrc = null,
            rawLrcTxt = null,
            translationRaw = null,
        )
        return LyricLoadState.Ready(item, document, userOffsetMs = 700L)
    }

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
    fun `lyrics ui state updates current line as playback position changes`() = runTest {
        val item = MusicItem(id = "1", platform = "demo", title = "Song", artist = "A", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
        val lines = listOf(
            ParsedLyricLine(0, 1_000L, "A"),
            ParsedLyricLine(1, 3_000L, "B"),
        )
        whenever(playerLyricLoader.observeLyrics(item)).thenReturn(
            flowOf(readyLyricState(item, lines)),
        )
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, position = 3_500L)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, viewModel.lyricsUiState.value.currentLineIndex)

        playerStateFlow.value = viewModel.playerState.value.copy(position = 1_500L)
        advanceUntilIdle()
        assertEquals(0, viewModel.lyricsUiState.value.currentLineIndex)
        job.cancel()
    }

    @Test
    fun `lyrics loader is not restarted when only playback position changes`() = runTest {
        val item = MusicItem(id = "stable", platform = "demo", title = "Song", artist = "A", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
        whenever(playerLyricLoader.observeLyrics(item)).thenReturn(
            flowOf(readyLyricState(item, listOf(ParsedLyricLine(0, 1_000L, "A")))),
        )
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, position = 1_000L)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()

        playerStateFlow.value = playerStateFlow.value.copy(position = 1_500L)
        playerStateFlow.value = playerStateFlow.value.copy(position = 2_000L)
        playerStateFlow.value = playerStateFlow.value.copy(position = 2_500L)
        advanceUntilIdle()

        verify(playerLyricLoader, times(1)).observeLyrics(item)
        job.cancel()
    }

    @Test
    fun `lyrics ui state reflects translation and font size preferences`() = runTest {
        val item = MusicItem(id = "2", platform = "demo", title = "Track", artist = "B", album = null, duration = 12_000L, url = null, artwork = null, qualities = null)
        whenever(playerLyricLoader.observeLyrics(item)).thenReturn(
            flowOf(readyLyricState(item, listOf(ParsedLyricLine(0, 500L, "Line")))),
        )
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()

        assertEquals(false, viewModel.lyricsUiState.value.showTranslation)
        assertEquals(1, viewModel.lyricsUiState.value.fontSizeLevel)

        lyricShowTranslationFlow.value = true
        lyricDetailFontSizeFlow.value = 3
        advanceUntilIdle()

        assertEquals(true, viewModel.lyricsUiState.value.showTranslation)
        assertEquals(3, viewModel.lyricsUiState.value.fontSizeLevel)
        job.cancel()
    }

    @Test
    fun `seekTo calls controller`() {
        val viewModel = createViewModel()
        viewModel.seekTo(5000L)
        verify(playerController).seekTo(5000L)
    }

    @Test
    fun `setLyricShowTranslation writes preference`() = runTest {
        val viewModel = createViewModel()
        viewModel.setLyricShowTranslation(true)
        advanceUntilIdle()

        verify(appPreferences).setLyricShowTranslation(true)
    }

    @Test
    fun `setLyricDetailFontSize writes preference`() = runTest {
        val viewModel = createViewModel()
        viewModel.setLyricDetailFontSize(3)
        advanceUntilIdle()

        verify(appPreferences).setLyricDetailFontSize(3)
    }

    @Test
    fun `seekToLyricLine applies lyric and meta offset`() = runTest {
        val item = MusicItem(id = "3", platform = "demo", title = "Line Seek", artist = "C", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
        val document = LyricDocument(
            musicId = item.id,
            musicPlatform = item.platform,
            lines = listOf(ParsedLyricLine(0, 1_000L, "A")),
            source = LyricSourceInfo.Plugin(item.platform),
            metaOffsetMs = 400L,
            rawLrc = null,
            rawLrcTxt = null,
            translationRaw = null,
        )
        whenever(playerLyricLoader.observeLyrics(item)).thenReturn(
            flowOf(LyricLoadState.Ready(item, document, userOffsetMs = 800L)),
        )
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, duration = 12_000L)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()

        viewModel.seekToLyricLine(2_500L)
        advanceUntilIdle()

        val expected = LyricTiming.seekPositionForLine(
            lineTimeMs = 2_500L,
            userOffsetMs = 800L,
            metaOffsetMs = 400L,
            durationMs = 12_000L,
        )
        verify(playerController).seekTo(expected)
        verify(playerController).play()
        job.cancel()
    }

    @Test
    fun `lyrics ui state stays default when no current track`() = runTest {
        playerStateFlow.value = PlayerState.EMPTY
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()

        assertEquals(LyricLoadState.NoTrack, viewModel.lyricsUiState.value.loadState)
        assertEquals(null, viewModel.lyricsUiState.value.document)
        assertEquals(null, viewModel.lyricsUiState.value.currentLineIndex)
        assertEquals(0L, viewModel.lyricsUiState.value.userOffsetMs)
        job.cancel()
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
