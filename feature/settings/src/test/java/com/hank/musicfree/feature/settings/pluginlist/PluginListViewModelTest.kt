package com.hank.musicfree.feature.settings.pluginlist

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.plugin.local.LocalFilePluginConstants
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.PluginUserVariable
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.hank.musicfree.feature.settings.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PluginListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createFixture(
        plugins: MutableStateFlow<List<LoadedPlugin>> = MutableStateFlow(emptyList()),
        disabledPlugins: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
        pluginOrder: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
        alternativePlugins: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap()),
        userVariables: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap()),
        playlists: MutableStateFlow<List<Playlist>> = MutableStateFlow(emptyList()),
    ): Fixture {
        val metaStore = mock<PluginMetaStore> {
            on { this.disabledPlugins } doReturn disabledPlugins
            on { this.pluginOrder } doReturn pluginOrder
            on { this.alternativePlugins } doReturn alternativePlugins
            on { subscriptions } doReturn MutableStateFlow(emptyList())
            on { getUserVariables(any()) } doReturn userVariables
        }
        val pluginManager = mock<PluginManager> {
            on { this.plugins } doReturn plugins
            on { pluginMetaStore } doReturn metaStore
        }
        whenever(pluginManager.getPlugin(any())).thenAnswer { invocation ->
            val platform = invocation.getArgument<String>(0)
            plugins.value.find { it.info.platform == platform }
        }
        val playlistRepository = mock<PlaylistRepository> {
            on { observeAllPlaylists() } doReturn playlists
        }
        runBlocking {
            whenever(playlistRepository.addMusicsToPlaylist(any(), any())).thenAnswer { invocation ->
                invocation.getArgument<List<MusicItem>>(1).size
            }
        }
        val appPreferences = mock<AppPreferences> {
            on { lazyLoadPlugins } doReturn MutableStateFlow(true)
        }
        return Fixture(
            viewModel = PluginListViewModel(pluginManager, playlistRepository, appPreferences),
            pluginManager = pluginManager,
            metaStore = metaStore,
            playlistRepository = playlistRepository,
            plugins = plugins,
            disabledPlugins = disabledPlugins,
            pluginOrder = pluginOrder,
            alternativePlugins = alternativePlugins,
            userVariables = userVariables,
            playlists = playlists,
        )
    }

    private fun createViewModel(): PluginListViewModel {
        return createFixture().viewModel
    }

    @Test
    fun `install state starts as Idle`() {
        val viewModel = createViewModel()
        assertEquals(PluginOperationUiState.Idle, viewModel.operationState.value)
    }

    @Test
    fun `plugin items starts empty`() = runTest {
        val viewModel = createViewModel()
        assertEquals(emptyList<PluginUiItem>(), viewModel.pluginItems.value)
    }

    @Test
    fun pluginItems_excludesLocalBuiltInPlugin() = runTest {
        val fixture = createFixture(
            plugins = MutableStateFlow(
                listOf(
                    loadedPlugin(
                        platform = LocalFilePluginConstants.PLATFORM,
                        methods = LocalFilePluginConstants.SUPPORTED_METHODS,
                    ),
                    loadedPlugin(platform = "source"),
                ),
            ),
        )
        val itemsJob = launch { fixture.viewModel.pluginItems.collect() }

        advanceUntilIdle()

        val items = fixture.viewModel.pluginItems.value
        assertEquals(1, items.size)
        assertEquals("source", items.single().info.platform)
        assertTrue(
            "pluginItems should not contain 本地",
            items.none { it.info.platform == LocalFilePluginConstants.PLATFORM },
        )

        itemsJob.cancel()
    }

    @Test
    fun `allPlaylists exposes repository playlists`() = runTest {
        val target = playlist("target")
        val fixture = createFixture(playlists = MutableStateFlow(listOf(target)))
        val collectJob = launch { fixture.viewModel.allPlaylists.collect() }

        advanceUntilIdle()

        assertEquals(listOf(target), fixture.viewModel.allPlaylists.value)
        collectJob.cancel()
    }

    @Test
    fun `reset install state sets to Idle`() = runTest {
        val viewModel = createViewModel()
        viewModel.resetInstallState()
        assertEquals(PluginOperationUiState.Idle, viewModel.operationState.value)
    }

    @Test
    fun `plugin item exposes parity action flags and alternative state`() = runTest {
        val plugins = MutableStateFlow(
            listOf(
                loadedPlugin(
                    platform = "source",
                    methods = setOf("getMediaSource", "importMusicItem", "importMusicSheet"),
                    userVariableCount = 1,
                    srcUrl = "https://example.com/source.js",
                ),
                loadedPlugin(
                    platform = "target",
                    methods = setOf("getMediaSource"),
                ),
            ),
        )
        val fixture = createFixture(
            plugins = plugins,
            alternativePlugins = MutableStateFlow(mapOf("source" to "target")),
        )
        val collectJob = launch { fixture.viewModel.pluginItems.collect() }

        advanceUntilIdle()

        val item = fixture.viewModel.pluginItems.value.first { it.info.platform == "source" }
        assertEquals("target", item.alternativePlatform)
        assertFalse(item.alternativeInvalid)
        assertTrue(item.canUpdate)
        assertTrue(item.canImportMusicItem)
        assertTrue(item.canImportMusicSheet)
        assertTrue(item.canEditUserVariables)
        collectJob.cancel()
    }

    @Test
    fun `plugin item marks missing alternative target invalid`() = runTest {
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(loadedPlugin(platform = "source"))),
            alternativePlugins = MutableStateFlow(mapOf("source" to "missing")),
        )
        val collectJob = launch { fixture.viewModel.pluginItems.collect() }

        advanceUntilIdle()

        val item = fixture.viewModel.pluginItems.value.single()
        assertEquals("missing", item.alternativePlatform)
        assertTrue(item.alternativeInvalid)
        collectJob.cancel()
    }

    @Test
    fun `plugin item marks disabled alternative target invalid`() = runTest {
        val fixture = createFixture(
            plugins = MutableStateFlow(
                listOf(
                    loadedPlugin(platform = "source"),
                    loadedPlugin(platform = "target", methods = setOf("getMediaSource")),
                ),
            ),
            disabledPlugins = MutableStateFlow(setOf("target")),
            alternativePlugins = MutableStateFlow(mapOf("source" to "target")),
        )
        val collectJob = launch { fixture.viewModel.pluginItems.collect() }

        advanceUntilIdle()

        val item = fixture.viewModel.pluginItems.value.first { it.info.platform == "source" }
        assertEquals("target", item.alternativePlatform)
        assertTrue(item.alternativeInvalid)
        collectJob.cancel()
    }

    @Test
    fun `plugin item marks non media source alternative target invalid`() = runTest {
        val fixture = createFixture(
            plugins = MutableStateFlow(
                listOf(
                    loadedPlugin(platform = "source"),
                    loadedPlugin(platform = "target", methods = setOf("search")),
                ),
            ),
            alternativePlugins = MutableStateFlow(mapOf("source" to "target")),
        )
        val collectJob = launch { fixture.viewModel.pluginItems.collect() }

        advanceUntilIdle()

        val item = fixture.viewModel.pluginItems.value.first { it.info.platform == "source" }
        assertEquals("target", item.alternativePlatform)
        assertTrue(item.alternativeInvalid)
        collectJob.cancel()
    }

    @Test
    fun `setAlternativePlugin delegates to meta store`() = runTest {
        val fixture = createFixture()

        fixture.viewModel.setAlternativePlugin("source", "target")
        advanceUntilIdle()

        verify(fixture.metaStore).setAlternativePlugin("source", "target")
    }

    @Test
    fun `saveUserVariables delegates to manager and reports success`() = runTest {
        val fixture = createFixture()
        val values = mapOf("cookie" to "abc")

        val requestId = fixture.viewModel.saveUserVariables("source", values)
        advanceUntilIdle()

        verify(fixture.pluginManager).setUserVariables("source", values)
        assertEquals(PluginOperationUiState.Success("设置成功"), fixture.viewModel.operationState.value)
        assertEquals(
            UserVariableSaveUiState.Success(requestId, "设置成功"),
            fixture.viewModel.userVariableSaveState.value,
        )
    }

    @Test
    fun `saveUserVariables reports failure for matching request`() = runTest {
        val fixture = createFixture()
        val values = mapOf("cookie" to "abc")
        runBlocking {
            whenever(fixture.pluginManager.setUserVariables("source", values))
                .thenThrow(IllegalStateException("变量刷新失败"))
        }

        val requestId = fixture.viewModel.saveUserVariables("source", values)
        advanceUntilIdle()

        assertEquals(
            PluginOperationUiState.Failure("变量刷新失败"),
            fixture.viewModel.operationState.value,
        )
        assertEquals(
            UserVariableSaveUiState.Failure(requestId, "变量刷新失败"),
            fixture.viewModel.userVariableSaveState.value,
        )
    }

    @Test
    fun `userVariables delegates to meta store`() = runTest {
        val values = MutableStateFlow(mapOf("cookie" to "abc"))
        val fixture = createFixture(userVariables = values)

        assertEquals(mapOf("cookie" to "abc"), fixture.viewModel.userVariables("source").first())
        verify(fixture.metaStore).getUserVariables("source")
    }

    @Test
    fun `importMusicItem parses item into add to playlist state`() = runTest {
        val item = musicItem("song-1")
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicItem"),
            importMusicItemResult = item,
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )

        fixture.viewModel.importMusicItem("source", "  https://example.com/song  ")
        advanceUntilIdle()

        verify(plugin).importMusicItem("https://example.com/song")
        assertEquals(PluginOperationUiState.Success("解析成功"), fixture.viewModel.operationState.value)
        assertTrue(fixture.viewModel.sheetState.value.visible)
        assertEquals(listOf(item), fixture.viewModel.sheetState.value.pendingItems)
    }

    @Test
    fun `hideAddToPlaylistSheet clears pending import items`() = runTest {
        val item = musicItem("song-1")
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicItem"),
            importMusicItemResult = item,
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )
        fixture.viewModel.importMusicItem("source", "https://example.com/song")
        advanceUntilIdle()

        fixture.viewModel.hideAddToPlaylistSheet()

        assertFalse(fixture.viewModel.sheetState.value.visible)
        assertTrue(fixture.viewModel.sheetState.value.pendingItems.isEmpty())
    }

    @Test
    fun `importMusicItem rejects blank url without calling plugin`() = runTest {
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicItem"),
            importMusicItemResult = musicItem("song-1"),
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )

        fixture.viewModel.importMusicItem("source", "   ")
        advanceUntilIdle()

        verify(plugin, never()).importMusicItem(any())
        assertEquals(
            PluginOperationUiState.Failure("链接有误或目标为空"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
    }

    @Test
    fun `importMusicItem failure clears previous pending import items`() = runTest {
        val item = musicItem("song-1")
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicItem"),
            importMusicItemResult = item,
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )
        fixture.viewModel.importMusicItem("source", "https://example.com/song")
        advanceUntilIdle()

        fixture.viewModel.importMusicItem("source", "   ")
        advanceUntilIdle()

        assertEquals(
            PluginOperationUiState.Failure("链接有误或目标为空"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
        assertTrue(fixture.viewModel.sheetState.value.pendingItems.isEmpty())
    }

    @Test
    fun `importMusicItem reports failure when plugin is missing`() = runTest {
        val fixture = createFixture()

        fixture.viewModel.importMusicItem("missing", "https://example.com/song")
        advanceUntilIdle()

        assertEquals(
            PluginOperationUiState.Failure("导入单曲失败"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
    }

    @Test
    fun `importMusicSheet failure clears previous pending import items`() = runTest {
        val items = listOf(musicItem("song-1"), musicItem("song-2"))
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = items,
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )
        fixture.viewModel.importMusicSheet("source", "https://example.com/sheet")
        advanceUntilIdle()

        fixture.viewModel.importMusicSheet("source", "   ")
        advanceUntilIdle()

        assertEquals(
            PluginOperationUiState.Failure("链接有误或目标为空"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
        assertTrue(fixture.viewModel.sheetState.value.pendingItems.isEmpty())
    }

    @Test
    fun `importMusicItem reports failure when plugin returns null`() = runTest {
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicItem"),
            importMusicItemResult = null,
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )

        fixture.viewModel.importMusicItem("source", "https://example.com/song")
        advanceUntilIdle()

        verify(plugin).importMusicItem("https://example.com/song")
        assertEquals(
            PluginOperationUiState.Failure("导入单曲失败"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
    }

    @Test
    fun `importMusicSheet parses items into add to playlist state`() = runTest {
        val items = listOf(musicItem("song-1"), musicItem("song-2"))
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = items,
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )

        fixture.viewModel.importMusicSheet("source", "  https://example.com/sheet  ")
        advanceUntilIdle()

        verify(plugin).importMusicSheet("https://example.com/sheet")
        assertEquals(PluginOperationUiState.Success("发现 2 首歌曲"), fixture.viewModel.operationState.value)
        assertTrue(fixture.viewModel.sheetState.value.visible)
        assertEquals(items, fixture.viewModel.sheetState.value.pendingItems)
    }

    @Test
    fun `importMusicSheet rejects blank url without calling plugin`() = runTest {
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = listOf(musicItem("song-1")),
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )

        fixture.viewModel.importMusicSheet("source", "   ")
        advanceUntilIdle()

        verify(plugin, never()).importMusicSheet(any())
        assertEquals(
            PluginOperationUiState.Failure("链接有误或目标为空"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
    }

    @Test
    fun `importMusicSheet reports failure when plugin is missing`() = runTest {
        val fixture = createFixture()

        fixture.viewModel.importMusicSheet("missing", "https://example.com/sheet")
        advanceUntilIdle()

        assertEquals(
            PluginOperationUiState.Failure("链接有误或目标歌单为空"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
    }

    @Test
    fun `importMusicSheet reports failure when parsed list is empty`() = runTest {
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = emptyList(),
        )
        val fixture = createFixture(
            plugins = MutableStateFlow(listOf(plugin)),
        )

        fixture.viewModel.importMusicSheet("source", "https://example.com/sheet")
        advanceUntilIdle()

        verify(plugin).importMusicSheet("https://example.com/sheet")
        assertEquals(
            PluginOperationUiState.Failure("链接有误或目标歌单为空"),
            fixture.viewModel.operationState.value,
        )
        assertFalse(fixture.viewModel.sheetState.value.visible)
    }

    @Test
    fun `addImportedItemsToPlaylist writes pending items to existing playlist`() = runTest {
        val items = listOf(musicItem("song-1"), musicItem("song-2"))
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = items,
        )
        val fixture = createFixture(plugins = MutableStateFlow(listOf(plugin)))
        runBlocking {
            whenever(fixture.playlistRepository.addMusicsToPlaylist("target", items)).thenReturn(1)
        }
        fixture.viewModel.importMusicSheet("source", "https://example.com/sheet")
        advanceUntilIdle()

        fixture.viewModel.addImportedItemsToPlaylist("target")
        advanceUntilIdle()

        verify(fixture.playlistRepository).addMusicsToPlaylist("target", items)
        assertFalse(fixture.viewModel.sheetState.value.visible)
        assertTrue(fixture.viewModel.sheetState.value.pendingItems.isEmpty())
        assertEquals(
            PluginOperationUiState.Success("已导入 1 首，跳过 1 首重复歌曲"),
            fixture.viewModel.operationState.value,
        )
    }

    @Test
    fun `addImportedItemsToPlaylist preserves pending items when dismissed before failure returns`() = runTest {
        val items = listOf(musicItem("song-1"), musicItem("song-2"))
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = items,
        )
        val fixture = createFixture(plugins = MutableStateFlow(listOf(plugin)))
        val addGate = CompletableDeferred<Int>()
        runBlocking {
            whenever(fixture.playlistRepository.addMusicsToPlaylist("target", items))
                .doSuspendableAnswer { addGate.await() }
        }
        fixture.viewModel.importMusicSheet("source", "https://example.com/sheet")
        advanceUntilIdle()

        fixture.viewModel.addImportedItemsToPlaylist("target")
        advanceUntilIdle()
        fixture.viewModel.hideAddToPlaylistSheet()
        addGate.completeExceptionally(IllegalStateException("写入失败"))
        advanceUntilIdle()

        assertTrue(fixture.viewModel.sheetState.value.visible)
        assertEquals(items, fixture.viewModel.sheetState.value.pendingItems)
        assertEquals(
            PluginOperationUiState.Failure("写入失败"),
            fixture.viewModel.operationState.value,
        )
    }

    @Test
    fun `createPlaylistAndImport creates playlist and imports pending items`() = runTest {
        val items = listOf(musicItem("song-1"), musicItem("song-2"))
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = items,
        )
        val fixture = createFixture(plugins = MutableStateFlow(listOf(plugin)))
        fixture.viewModel.importMusicSheet("source", "https://example.com/sheet")
        advanceUntilIdle()

        fixture.viewModel.createPlaylistAndImport("  新歌单  ")
        advanceUntilIdle()

        val playlistCaptor = argumentCaptor<Playlist>()
        verify(fixture.playlistRepository).createPlaylist(playlistCaptor.capture())
        val created = playlistCaptor.firstValue
        assertEquals("新歌单", created.name)
        assertNull(created.coverUri)
        verify(fixture.playlistRepository).addMusicsToPlaylist(created.id, items)
        assertFalse(fixture.viewModel.sheetState.value.visible)
        assertTrue(fixture.viewModel.sheetState.value.pendingItems.isEmpty())
        assertEquals(
            PluginOperationUiState.Success("已导入 2 首"),
            fixture.viewModel.operationState.value,
        )
    }

    @Test
    fun `createPlaylistAndImport deletes created playlist when import fails`() = runTest {
        val items = listOf(musicItem("song-1"), musicItem("song-2"))
        val plugin = loadedPlugin(
            platform = "source",
            methods = setOf("importMusicSheet"),
            importMusicSheetResult = items,
        )
        val fixture = createFixture(plugins = MutableStateFlow(listOf(plugin)))
        runBlocking {
            whenever(fixture.playlistRepository.addMusicsToPlaylist(any(), any()))
                .thenThrow(IllegalStateException("写入失败"))
        }
        fixture.viewModel.importMusicSheet("source", "https://example.com/sheet")
        advanceUntilIdle()

        fixture.viewModel.createPlaylistAndImport("新歌单")
        advanceUntilIdle()

        val playlistCaptor = argumentCaptor<Playlist>()
        verify(fixture.playlistRepository).createPlaylist(playlistCaptor.capture())
        verify(fixture.playlistRepository).deletePlaylist(playlistCaptor.firstValue)
        assertTrue(fixture.viewModel.sheetState.value.visible)
        assertEquals(items, fixture.viewModel.sheetState.value.pendingItems)
        assertEquals(
            PluginOperationUiState.Failure("写入失败"),
            fixture.viewModel.operationState.value,
        )
    }

    private data class Fixture(
        val viewModel: PluginListViewModel,
        val pluginManager: PluginManager,
        val metaStore: PluginMetaStore,
        val playlistRepository: PlaylistRepository,
        val plugins: MutableStateFlow<List<LoadedPlugin>>,
        val disabledPlugins: MutableStateFlow<Set<String>>,
        val pluginOrder: MutableStateFlow<List<String>>,
        val alternativePlugins: MutableStateFlow<Map<String, String>>,
        val userVariables: MutableStateFlow<Map<String, String>>,
        val playlists: MutableStateFlow<List<Playlist>>,
    )

    private fun loadedPlugin(
        platform: String,
        methods: Set<String> = emptySet(),
        userVariableCount: Int = 0,
        srcUrl: String? = null,
        importMusicItemResult: MusicItem? = null,
        importMusicSheetResult: List<MusicItem>? = null,
    ): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = "1.0.0",
                author = null,
                description = null,
                srcUrl = srcUrl,
                supportedSearchType = listOf("music"),
                supportedMethods = methods,
                userVariables = List(userVariableCount) { index ->
                    PluginUserVariable(
                        key = "var$index",
                        name = "Variable $index",
                        hint = null,
                    )
                },
            ),
        )
        runBlocking {
            whenever(plugin.importMusicItem(any())).thenReturn(importMusicItemResult)
            whenever(plugin.importMusicSheet(any())).thenReturn(importMusicSheetResult)
        }
        return plugin
    }

    private fun musicItem(id: String) = MusicItem(
        id = id,
        platform = "source",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )

    private fun playlist(id: String) = Playlist(
        id = id,
        name = "Playlist $id",
    )
}
