package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.MusicDetailDefaultPage
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackSpeeds
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.lyric.LyricTiming
import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.LocalLyricKind
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEvent
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricLoadState
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricSearchGroup
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricLoader
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.MfLogger
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    private val downloader: Downloader = mock()
    private val lyricShowTranslationFlow = MutableStateFlow(false)
    private val lyricDetailFontSizeFlow = MutableStateFlow(1)
    private val playQualityFlow = MutableStateFlow(PlayQuality.STANDARD)
    private val playRateFlow = MutableStateFlow(PlaybackSpeeds.DEFAULT)
    private val musicDetailDefaultPageFlow = MutableStateFlow(MusicDetailDefaultPage.Album)
    private val musicDetailAwakeFlow = MutableStateFlow(false)
    private val downloaderTasksFlow = MutableStateFlow<List<DownloadTaskUi>>(emptyList())
    private val downloaderDownloadedKeysFlow = MutableStateFlow<Set<MediaKey>>(emptySet())
    private val downloaderEventsFlow = MutableSharedFlow<DownloadEvent>()
    private val controllerErrorFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val playerStateFlow = MutableStateFlow(PlayerState.EMPTY)
    private val queueStateFlow = MutableStateFlow(PlayQueueSnapshot.EMPTY)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerState).thenReturn(playerStateFlow)
        whenever(playerController.queueState).thenReturn(queueStateFlow)
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
        whenever(playerLyricLoader.observeLyrics(anyOrNull())).thenReturn(flowOf(LyricLoadState.NoTrack))
        whenever(appPreferences.lyricShowTranslation).thenReturn(lyricShowTranslationFlow)
        whenever(appPreferences.lyricDetailFontSize).thenReturn(lyricDetailFontSizeFlow)
        whenever(appPreferences.playQuality).thenReturn(playQualityFlow)
        whenever(appPreferences.playRate).thenReturn(playRateFlow)
        whenever(appPreferences.musicDetailDefaultPage).thenReturn(musicDetailDefaultPageFlow)
        whenever(appPreferences.musicDetailAwake).thenReturn(musicDetailAwakeFlow)
        whenever(downloader.tasks).thenReturn(downloaderTasksFlow)
        whenever(downloader.downloadedKeys).thenReturn(downloaderDownloadedKeysFlow)
        whenever(downloader.events).thenReturn(downloaderEventsFlow)
        whenever(playerController.errorEvents).thenReturn(controllerErrorFlow)
    }

    @After
    fun tearDown() {
        MfLog.resetForTest()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PlayerViewModel(
        playerController,
        playlistRepository,
        playerLyricLoader,
        appPreferences,
        downloader,
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
    fun `togglePlayPause logs play action with current item fields`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val item = MusicItem(id = "play-log", platform = "demo", title = "Song", artist = "A", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, isPlaying = false, position = 1_234L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.togglePlayPause()

        val event = logger.events.single { it.event == "player_play" }
        assertEquals(LogCategory.PLAYER, event.category)
        assertEquals("player", event.fields["screen"])
        assertEquals("play", event.fields["operation"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
        assertEquals("play-log", event.fields["itemId"])
        assertEquals("Song", event.fields["itemName"])
        assertEquals("demo", event.fields["platform"])
        assertEquals(1_234L, event.fields["positionMs"])
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
    fun `lyrics loader uses music identity key instead of full item equality`() = runTest {
        val item = MusicItem(
            id = "same",
            platform = "demo",
            title = "Song",
            artist = "A",
            album = null,
            duration = 10_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
        val rebuiltItem = item.copy(raw = mapOf("source" to "updated"), addedAt = 99L)
        whenever(playerLyricLoader.observeLyrics(item)).thenReturn(
            flowOf(readyLyricState(item, listOf(ParsedLyricLine(0, 1_000L, "A")))),
        )
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()

        playerStateFlow.value = playerStateFlow.value.copy(currentItem = rebuiltItem)
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
    fun `lyrics ui state keeps ready during same track loading refresh`() = runTest {
        val item = MusicItem(id = "stable-ready", platform = "demo", title = "Song", artist = "A", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
        val lyricFlow = MutableStateFlow<LyricLoadState>(
            readyLyricState(item, listOf(ParsedLyricLine(0, 1_000L, "A"))),
        )
        whenever(playerLyricLoader.observeLyrics(item)).thenReturn(lyricFlow)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, position = 1_500L)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.lyricsUiState.value.loadState is LyricLoadState.Ready)

        lyricFlow.value = LyricLoadState.Loading(item)
        advanceUntilIdle()

        assertTrue(viewModel.lyricsUiState.value.loadState is LyricLoadState.Ready)
        assertEquals("A", viewModel.lyricsUiState.value.document?.lines?.first()?.text)
        job.cancel()
    }

    @Test
    fun `lyrics ui state allows final same track no lyric after loading refresh`() = runTest {
        val item = MusicItem(id = "final-no-lyric", platform = "demo", title = "Song", artist = "A", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
        val lyricFlow = MutableStateFlow<LyricLoadState>(
            readyLyricState(item, listOf(ParsedLyricLine(0, 1_000L, "A"))),
        )
        whenever(playerLyricLoader.observeLyrics(item)).thenReturn(lyricFlow)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, position = 1_500L)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()

        lyricFlow.value = LyricLoadState.Loading(item)
        advanceUntilIdle()
        assertTrue(viewModel.lyricsUiState.value.loadState is LyricLoadState.Ready)

        lyricFlow.value = LyricLoadState.NoLyric(item)
        advanceUntilIdle()

        assertEquals(LyricLoadState.NoLyric(item), viewModel.lyricsUiState.value.loadState)
        assertEquals(null, viewModel.lyricsUiState.value.document)
        assertEquals(null, viewModel.lyricsUiState.value.currentLineIndex)
        job.cancel()
    }

    @Test
    fun `lyrics ui state switches to loading for a different track`() = runTest {
        val first = MusicItem(id = "first", platform = "demo", title = "First", artist = "A", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
        val second = MusicItem(id = "second", platform = "demo", title = "Second", artist = "B", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
        whenever(playerLyricLoader.observeLyrics(first)).thenReturn(
            flowOf(readyLyricState(first, listOf(ParsedLyricLine(0, 1_000L, "First line")))),
        )
        whenever(playerLyricLoader.observeLyrics(second)).thenReturn(
            flowOf(LyricLoadState.Loading(second)),
        )
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = first, position = 1_500L)

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.lyricsUiState.value.loadState is LyricLoadState.Ready)

        playerStateFlow.value = playerStateFlow.value.copy(currentItem = second, position = 0L)
        advanceUntilIdle()

        assertEquals(LyricLoadState.Loading(second), viewModel.lyricsUiState.value.loadState)
        assertEquals(null, viewModel.lyricsUiState.value.document)
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
    fun `searchLyrics stores lyric candidates`() = runTest {
        val item = MusicItem(id = "lyric-search", platform = "demo", title = "Song", artist = "A", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        val candidate = item.copy(id = "candidate", platform = "lyric")
        val groups = listOf(LyricSearchGroup(pluginInfo("lyric"), listOf(candidate)))
        whenever(playerLyricLoader.searchCandidates(item)).thenReturn(groups)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        viewModel.searchLyrics()
        advanceUntilIdle()

        assertEquals(groups, viewModel.lyricSearchResults.value)
        assertFalse(viewModel.lyricSearchLoading.value)
    }

    @Test
    fun `searchLyrics logs failure with lyrics category`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val item = MusicItem(id = "lyric-failure", platform = "demo", title = "Song", artist = "A", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        whenever(playerLyricLoader.searchCandidates(item)).thenThrow(RuntimeException("boom"))
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        viewModel.searchLyrics()
        advanceUntilIdle()

        val event = logger.events.single { it.event == "lyric_search_failed" }
        assertEquals(LogCategory.LYRICS, event.category)
        assertEquals("player", event.fields["screen"])
        assertEquals("search", event.fields["operation"])
        assertEquals(LogFields.Result.FAILURE, event.fields["result"])
        assertEquals(LogFields.Reason.UNKNOWN, event.fields["reason"])
        assertEquals("lyric-failure", event.fields["itemId"])
        assertEquals("Song", event.fields["itemName"])
        assertEquals("demo", event.fields["platform"])
    }

    @Test
    fun `associateLyric delegates current item and target`() = runTest {
        val item = MusicItem(id = "current", platform = "demo", title = "Song", artist = "A", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        val target = item.copy(id = "target", platform = "lyric")
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        viewModel.associateLyric(target)
        advanceUntilIdle()

        verify(playerLyricLoader).associateLyric(item, target)
    }

    @Test
    fun `clearAssociatedLyric delegates current item`() = runTest {
        val item = MusicItem(id = "clear-associated", platform = "demo", title = "Song", artist = "A", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        viewModel.clearAssociatedLyric()
        advanceUntilIdle()

        verify(playerLyricLoader).clearAssociatedLyric(item)
    }

    @Test
    fun `setLyricOffset delegates current item and offset`() = runTest {
        val item = MusicItem(id = "offset", platform = "demo", title = "Song", artist = "A", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        viewModel.setLyricOffset(1_500L)
        advanceUntilIdle()

        verify(playerLyricLoader).setLyricOffset(item, 1_500L)
    }

    @Test
    fun `importLocalLyric delegates current item and kind`() = runTest {
        val item = MusicItem(id = "import", platform = "demo", title = "Song", artist = "A", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        viewModel.importLocalLyric("[00:01.00]歌词", LocalLyricKind.Raw)
        advanceUntilIdle()

        verify(playerLyricLoader).importLocalLyric(item, "[00:01.00]歌词", LocalLyricKind.Raw)
    }

    @Test
    fun `deleteLocalLyric delegates current item`() = runTest {
        val item = MusicItem(id = "delete", platform = "demo", title = "Song", artist = "A", album = null, duration = 0L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)

        val viewModel = createViewModel()
        viewModel.deleteLocalLyric()
        advanceUntilIdle()

        verify(playerLyricLoader).deleteLocalLyric(item)
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
    fun `cyclePlaybackMode calls controller`() {
        val viewModel = createViewModel()
        viewModel.cyclePlaybackMode()
        verify(playerController).cyclePlaybackMode()
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
        assertEquals(listOf(item), s.pendingItems)
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

    @Test
    fun `currentQuality reflects appPreferences playQuality`() = runTest {
        playQualityFlow.value = PlayQuality.HIGH
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.currentQuality.collect {} }
        advanceUntilIdle()

        assertEquals(PlayQuality.HIGH, viewModel.currentQuality.value)
        job.cancel()
    }

    @Test
    fun `setCurrentQuality writes prefs and calls controller changeQuality`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setCurrentQuality(PlayQuality.SUPER)
        advanceUntilIdle()

        verify(appPreferences).setPlayQuality(PlayQuality.SUPER)
        verify(playerController).changeQuality(PlayQuality.SUPER)
    }

    @Test
    fun `currentSpeed reflects appPreferences playRate`() = runTest {
        playRateFlow.value = 1.5f
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.currentSpeed.collect {} }
        advanceUntilIdle()

        assertEquals(1.5f, viewModel.currentSpeed.value)
        job.cancel()
    }

    @Test
    fun `setPlaybackSpeed writes prefs and calls controller setPlaybackSpeed`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPlaybackSpeed(1.25f)
        advanceUntilIdle()

        verify(appPreferences).setPlayRate(1.25f)
        verify(playerController).setPlaybackSpeed(1.25f)
    }

    @Test
    fun `downloadCurrent enqueues current item with quality`() = runTest {
        val item = MusicItem(id = "11", platform = "demo", title = "T", artist = "A", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.downloadCurrent(PlayQuality.HIGH)

        verify(downloader).enqueue(eq(listOf(item)), eq(PlayQuality.HIGH))
    }

    @Test
    fun `downloadCurrent is no-op when no current item`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.downloadCurrent(PlayQuality.HIGH)

        verify(downloader, never()).enqueue(any(), any())
    }

    @Test
    fun `isCurrentDownloaded true when downloadedKeys contains current item key`() = runTest {
        val item = MusicItem(id = "33", platform = "p", title = "X", artist = "A", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        downloaderDownloadedKeysFlow.value = setOf(MediaKey.of(item))
        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.isCurrentDownloaded.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.isCurrentDownloaded.value)
        job.cancel()
    }

    @Test
    fun `isCurrentDownloaded reacts to downloadedKeys changes after VM construction`() = runTest {
        val item = MusicItem(id = "44", platform = "p", title = "Y", artist = "B", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        val viewModel = createViewModel()
        val collectJob = backgroundScope.launch { viewModel.isCurrentDownloaded.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.isCurrentDownloaded.value)

        downloaderDownloadedKeysFlow.value = setOf(MediaKey.of(item))
        advanceUntilIdle()

        assertTrue(viewModel.isCurrentDownloaded.value)
        collectJob.cancel()
    }

    @Test
    fun `toggleCurrentFavorite emits error event on repository failure`() = runTest {
        val item = MusicItem(id = "fav", platform = "p", title = "X", artist = "A", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        whenever(playlistRepository.toggleFavorite(item)).thenThrow(RuntimeException("boom"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        val errors = mutableListOf<String>()
        val collectJob = launch { viewModel.errorEvents.collect { errors.add(it) } }
        advanceUntilIdle()

        viewModel.toggleCurrentFavorite()
        advanceUntilIdle()

        assertTrue(errors.any { it.contains("收藏") })
        collectJob.cancel()
    }
}

private fun pluginInfo(platform: String) = PluginInfo(
    platform = platform,
    version = null,
    author = null,
    description = null,
    srcUrl = null,
    supportedSearchType = listOf("lyric"),
)

private data class RecordedLogEvent(
    val level: String,
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
    val throwable: Throwable? = null,
)

private class RecordingLogger : MfLogger {
    val events = mutableListOf<RecordedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += RecordedLogEvent("trace", category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += RecordedLogEvent("detail", category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += RecordedLogEvent("error", category, event, fields, throwable)
    }

    override fun flush() = Unit
}
