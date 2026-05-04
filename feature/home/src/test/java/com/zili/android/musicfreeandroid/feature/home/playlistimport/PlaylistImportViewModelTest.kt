package com.zili.android.musicfreeandroid.feature.home.playlistimport

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistImportViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val pluginManager: PluginManager = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val enabledPluginFlow = MutableStateFlow<List<LoadedPlugin>>(emptyList())

    init {
        whenever(pluginManager.getSortedEnabledPlugins()).thenReturn(enabledPluginFlow)
        whenever(pluginManager.getPlugin(any())).thenAnswer { invocation ->
            val platform = invocation.getArgument<String>(0)
            enabledPluginFlow.value.find { it.info.platform == platform }
        }
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        runBlocking {
            whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PlaylistImportViewModel(
        pluginManager = pluginManager,
        playlistRepository = playlistRepository,
    )


    private fun musicItem(id: String, title: String): MusicItem {
        return MusicItem(
            id = id,
            platform = "source",
            title = title,
            artist = "Artist",
            album = null,
            duration = 180_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
    }

    private fun loadedPlugin(
        platform: String,
        supportedMethods: Set<String>,
        importResult: List<MusicItem>? = null,
        hints: List<String>? = null,
        importError: Exception? = null,
    ): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = "1.0.0",
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = listOf("music"),
                supportedMethods = supportedMethods,
                hints = hints?.let { mapOf("importMusicSheet" to it) },
            ),
        )

        if (importError != null) {
            runBlocking {
                whenever(plugin.importMusicSheet(any())).thenThrow(importError)
            }
        } else {
            runBlocking {
                whenever(plugin.importMusicSheet(any())).thenReturn(importResult)
            }
        }
        return plugin
    }

    @Test
    fun `openImportSheet only exposes import-capable plugins and keeps order`() = runTest {
        val pluginA = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("search", "importMusicSheet"),
            hints = listOf("url hint a"),
        )
        val pluginB = loadedPlugin(
            platform = "music-b",
            supportedMethods = setOf("search"),
        )
        val pluginC = loadedPlugin(
            platform = "music-c",
            supportedMethods = setOf("importMusicSheet"),
            hints = listOf("url hint c"),
        )
        enabledPluginFlow.value = listOf(pluginB, pluginA, pluginC)

        val viewModel = createViewModel()
        viewModel.openImportSheet()
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertTrue(state is PlaylistImportState.ChoosePlugin)
        state as PlaylistImportState.ChoosePlugin
        assertEquals(listOf("music-a", "music-c"), state.plugins.map { it.platform })
        assertEquals(listOf("url hint a"), state.plugins[0].hints)
        assertEquals(listOf("url hint c"), state.plugins[1].hints)
    }

    @Test
    fun `openImportSheet emits import failed message when plugin loading throws`() = runTest {
        runBlocking {
            whenever(pluginManager.ensurePluginsLoaded()).thenThrow(RuntimeException("load failed"))
        }
        val viewModel = createViewModel()
        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }

        viewModel.openImportSheet()
        advanceUntilIdle()

        assertEquals("歌单导入失败", toastEvents.singleOrNull())
        assertTrue(viewModel.importState.value is PlaylistImportState.Idle)
        toastCollector.cancel()
    }

    @Test
    fun `submitUrl keeps InputUrl when blank and does not call plugin`() = runTest {
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = listOf(musicItem("1", "Track")),
        )
        enabledPluginFlow.value = listOf(plugin)

        val viewModel = createViewModel()
        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")

        viewModel.submitUrl("   ")
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertTrue(state is PlaylistImportState.InputUrl)
        state as PlaylistImportState.InputUrl
        assertEquals("链接有误或目标歌单为空", state.errorMessage)
        assertEquals("music-a", state.plugin.platform)
        verify(plugin, never()).importMusicSheet(any())
    }

    @Test
    fun `submitUrl with valid url enters ConfirmFound`() = runTest {
        val songs = listOf(musicItem("1", "Song A"), musicItem("2", "Song B"))
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = songs,
        )
        enabledPluginFlow.value = listOf(plugin)

        val viewModel = createViewModel()
        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")

        viewModel.submitUrl("  https://music.example.com/playlist  ")
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertTrue(state is PlaylistImportState.ConfirmFound)
        state as PlaylistImportState.ConfirmFound
        assertEquals(songs, state.items)
        verify(plugin).importMusicSheet("https://music.example.com/playlist")
    }

    @Test
    fun `submitUrl emits invalid url message when plugin returns empty list`() = runTest {
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = emptyList(),
        )
        enabledPluginFlow.value = listOf(plugin)

        val viewModel = createViewModel()
        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }

        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/empty")
        advanceUntilIdle()

        verify(plugin).importMusicSheet("https://music.example.com/empty")
        assertEquals(1, toastEvents.size)
        assertEquals("链接有误或目标歌单为空", toastEvents.singleOrNull())
        toastCollector.cancel()
        assertTrue(viewModel.importState.value is PlaylistImportState.Idle)
    }

    @Test
    fun `submitUrl emits invalid url message when plugin returns null`() = runTest {
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = null,
        )
        enabledPluginFlow.value = listOf(plugin)

        val viewModel = createViewModel()
        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }

        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/null")
        advanceUntilIdle()

        verify(plugin).importMusicSheet("https://music.example.com/null")
        assertEquals("链接有误或目标歌单为空", toastEvents.singleOrNull())
        toastCollector.cancel()
        assertTrue(viewModel.importState.value is PlaylistImportState.Idle)
    }

    @Test
    fun `submitUrl emits import failed message when plugin throws`() = runTest {
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importError = RuntimeException("plugin error"),
        )
        enabledPluginFlow.value = listOf(plugin)

        val viewModel = createViewModel()
        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }

        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/error")
        advanceUntilIdle()

        verify(plugin).importMusicSheet("https://music.example.com/error")
        assertEquals("歌单导入失败", toastEvents.singleOrNull())
        toastCollector.cancel()
        assertTrue(viewModel.importState.value is PlaylistImportState.Idle)
    }

    @Test
    fun `dismissImportFlow cancels pending parser result`() = runTest {
        val songs = listOf(musicItem("1", "Song A"))
        val parseGate = CompletableDeferred<List<MusicItem>>()
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = songs,
        )
        runBlocking {
            whenever(plugin.importMusicSheet(any())).doSuspendableAnswer {
                parseGate.await()
            }
        }
        enabledPluginFlow.value = listOf(plugin)

        val viewModel = createViewModel()
        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/list")
        advanceUntilIdle()

        assertTrue(viewModel.importState.value is PlaylistImportState.Parsing)

        viewModel.dismissImportFlow()
        parseGate.complete(songs)
        advanceUntilIdle()

        assertEquals(PlaylistImportState.Idle, viewModel.importState.value)
        assertFalse(viewModel.sheetState.value.visible)
    }

    @Test
    fun `addImportedItemsToPlaylist computes added skipped and closes sheet`() = runTest {
        val songs = listOf(
            musicItem("1", "Song A"),
            musicItem("2", "Song B"),
            musicItem("3", "Song C"),
            musicItem("4", "Song D"),
            musicItem("5", "Song E"),
        )
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = songs,
        )
        enabledPluginFlow.value = listOf(plugin)
        runBlocking {
            whenever(playlistRepository.addMusicsToPlaylist("playlist-1", songs)).thenReturn(3)
        }

        val viewModel = createViewModel()
        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }

        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/list")
        advanceUntilIdle()

        viewModel.confirmFoundItems()
        assertTrue(viewModel.importState.value is PlaylistImportState.ChooseTarget)
        assertTrue(viewModel.sheetState.value.visible)

        viewModel.addImportedItemsToPlaylist("playlist-1")
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertTrue(state is PlaylistImportState.Completed)
        state as PlaylistImportState.Completed
        assertEquals(3, state.added)
        assertEquals(2, state.skipped)
        assertEquals("已导入 3 首，跳过 2 首重复歌曲", toastEvents.singleOrNull())
        toastCollector.cancel()
        assertFalse(viewModel.sheetState.value.visible)
    }

    @Test
    fun `addImportedItemsToPlaylist keeps target state when repository throws`() = runTest {
        val songs = listOf(
            musicItem("1", "Song A"),
            musicItem("2", "Song B"),
        )
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = songs,
        )
        enabledPluginFlow.value = listOf(plugin)
        runBlocking {
            whenever(playlistRepository.addMusicsToPlaylist("playlist-1", songs))
                .thenThrow(RuntimeException("db error"))
        }

        val viewModel = createViewModel()
        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }

        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/list")
        advanceUntilIdle()

        viewModel.confirmFoundItems()
        viewModel.addImportedItemsToPlaylist("playlist-1")
        advanceUntilIdle()

        assertEquals("导入失败，请重试", toastEvents.singleOrNull())
        toastCollector.cancel()
        assertTrue(viewModel.importState.value is PlaylistImportState.ChooseTarget)
        assertTrue(viewModel.sheetState.value.visible)
    }

    @Test
    fun `dismissImportFlow cancels pending add to existing playlist`() = runTest {
        val songs = listOf(musicItem("1", "Song A"), musicItem("2", "Song B"))
        val addGate = CompletableDeferred<Int>()
        runBlocking {
            whenever(playlistRepository.addMusicsToPlaylist("playlist-1", songs)).doSuspendableAnswer {
                addGate.await()
            }
        }

        val viewModel = createViewModel()
        viewModel.confirmImportTarget(songs)
        viewModel.addImportedItemsToPlaylist("playlist-1")
        advanceUntilIdle()

        assertTrue(viewModel.importState.value is PlaylistImportState.Parsing)
        assertFalse(viewModel.sheetState.value.visible)

        viewModel.dismissImportFlow()
        addGate.complete(2)
        advanceUntilIdle()

        assertEquals(PlaylistImportState.Idle, viewModel.importState.value)
        assertFalse(viewModel.sheetState.value.visible)
    }

    @Test
    fun `addImportedItemsToPlaylist ignores duplicate target clicks while import is pending`() = runTest {
        val songs = listOf(musicItem("1", "Song A"), musicItem("2", "Song B"))
        val addGate = CompletableDeferred<Int>()
        runBlocking {
            whenever(playlistRepository.addMusicsToPlaylist("playlist-1", songs)).doSuspendableAnswer {
                addGate.await()
            }
        }

        val viewModel = createViewModel()
        viewModel.confirmImportTarget(songs)
        viewModel.addImportedItemsToPlaylist("playlist-1")
        advanceUntilIdle()

        assertTrue(viewModel.importState.value is PlaylistImportState.Parsing)
        assertFalse(viewModel.sheetState.value.visible)

        viewModel.addImportedItemsToPlaylist("playlist-1")
        advanceUntilIdle()

        verify(playlistRepository, times(1)).addMusicsToPlaylist("playlist-1", songs)

        addGate.complete(2)
        advanceUntilIdle()

        val state = viewModel.importState.value
        assertTrue(state is PlaylistImportState.Completed)
        state as PlaylistImportState.Completed
        assertEquals(2, state.added)
        assertEquals(0, state.skipped)
    }

    @Test
    fun `createPlaylistAndImport trims name creates playlist and imports items`() = runTest {
        val songs = listOf(musicItem("1", "Song A"), musicItem("2", "Song B"))
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = songs,
        )
        enabledPluginFlow.value = listOf(plugin)
        var createdPlaylist: Playlist? = null
        runBlocking {
            whenever(playlistRepository.createPlaylist(any())).thenAnswer { invocation ->
                createdPlaylist = invocation.getArgument<Playlist>(0)
                Unit
            }
            whenever(playlistRepository.addMusicsToPlaylist(any(), eq(songs))).thenReturn(2)
        }

        val viewModel = createViewModel()
        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/list")
        advanceUntilIdle()
        viewModel.confirmFoundItems()

        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }
        advanceUntilIdle()
        viewModel.createPlaylistAndImport("  我的歌单  ")
        advanceUntilIdle()

        assertEquals("我的歌单", createdPlaylist?.name)
        assertEquals("已导入 2 首", toastEvents.singleOrNull())
        assertTrue(viewModel.importState.value is PlaylistImportState.Completed)
        assertTrue(!createdPlaylist?.id.isNullOrBlank())
        assertEquals(0, viewModel.sheetState.value.pendingItems.size)
        toastCollector.cancel()
    }

    @Test
    fun `createPlaylistAndImport cleans up created playlist when import fails`() = runTest {
        val songs = listOf(musicItem("1", "Song A"), musicItem("2", "Song B"))
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = songs,
        )
        enabledPluginFlow.value = listOf(plugin)
        var createdPlaylist: Playlist? = null
        var deletedPlaylist: Playlist? = null
        runBlocking {
            whenever(playlistRepository.createPlaylist(any())).thenAnswer { invocation ->
                createdPlaylist = invocation.getArgument<Playlist>(0)
                Unit
            }
            whenever(playlistRepository.addMusicsToPlaylist(any(), eq(songs)))
                .thenThrow(RuntimeException("db error"))
            whenever(playlistRepository.deletePlaylist(any())).thenAnswer { invocation ->
                deletedPlaylist = invocation.getArgument<Playlist>(0)
                Unit
            }
        }

        val viewModel = createViewModel()
        viewModel.openImportSheet()
        advanceUntilIdle()
        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/list")
        advanceUntilIdle()
        viewModel.confirmFoundItems()

        val toastEvents = mutableListOf<String>()
        val toastCollector = launch {
            viewModel.events.filterIsInstance<PlaylistImportEvent.Toast>()
                .collect { toastEvents.add(it.message) }
        }
        advanceUntilIdle()
        viewModel.createPlaylistAndImport("新歌单")
        advanceUntilIdle()

        assertEquals("导入失败，请重试", toastEvents.singleOrNull())
        assertEquals(createdPlaylist, deletedPlaylist)
        assertTrue(viewModel.importState.value is PlaylistImportState.ChooseTarget)
        assertTrue(viewModel.sheetState.value.visible)
        toastCollector.cancel()
    }

    @Test
    fun `dismissImportFlow cleans up created playlist while new playlist import is pending`() = runTest {
        val songs = listOf(musicItem("1", "Song A"), musicItem("2", "Song B"))
        val addGate = CompletableDeferred<Int>()
        var createdPlaylist: Playlist? = null
        var deletedPlaylist: Playlist? = null
        runBlocking {
            whenever(playlistRepository.createPlaylist(any())).thenAnswer { invocation ->
                createdPlaylist = invocation.getArgument<Playlist>(0)
                Unit
            }
            whenever(playlistRepository.addMusicsToPlaylist(any(), eq(songs))).doSuspendableAnswer {
                addGate.await()
            }
            whenever(playlistRepository.deletePlaylist(any())).thenAnswer { invocation ->
                deletedPlaylist = invocation.getArgument<Playlist>(0)
                Unit
            }
        }

        val viewModel = createViewModel()
        viewModel.confirmImportTarget(songs)
        viewModel.createPlaylistAndImport("新歌单")
        advanceUntilIdle()

        assertTrue(createdPlaylist != null)

        viewModel.dismissImportFlow()
        addGate.complete(2)
        advanceUntilIdle()

        assertEquals(createdPlaylist, deletedPlaylist)
        assertEquals(PlaylistImportState.Idle, viewModel.importState.value)
        assertFalse(viewModel.sheetState.value.visible)
    }

    @Test
    fun `dismissImportFlow resets state and clears sheet`() = runTest {
        val plugin = loadedPlugin(
            platform = "music-a",
            supportedMethods = setOf("importMusicSheet"),
            importResult = listOf(musicItem("1", "Song")),
        )
        enabledPluginFlow.value = listOf(plugin)

        val viewModel = createViewModel()
        viewModel.openImportSheet()
        advanceUntilIdle()

        assertTrue(viewModel.importState.value is PlaylistImportState.ChoosePlugin)

        viewModel.selectPlugin("music-a")
        viewModel.submitUrl("https://music.example.com/list")
        advanceUntilIdle()

        viewModel.confirmFoundItems()
        assertTrue(viewModel.sheetState.value.visible)

        viewModel.dismissImportFlow()
        assertEquals(PlaylistImportState.Idle, viewModel.importState.value)
        assertFalse(viewModel.sheetState.value.visible)
    }
}
