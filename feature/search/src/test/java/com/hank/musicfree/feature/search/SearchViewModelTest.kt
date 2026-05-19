package com.hank.musicfree.feature.search

import com.hank.musicfree.core.media.EmptyMediaSourceResolver
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.search.runtime.PluginManagerSearchSessionGateway
import com.hank.musicfree.feature.search.runtime.SearchSessionClock
import com.hank.musicfree.feature.search.runtime.SearchSessionStore
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.PluginSearchItem
import com.hank.musicfree.plugin.api.SearchResult
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pluginManager: PluginManager = mock()
    private val playerController: PlayerController = mock()
    private val appPreferences: AppPreferences = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val downloader: Downloader = mock()
    private val pluginFlow = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    private val searchablePluginFlow = MutableStateFlow<List<LoadedPlugin>>(emptyList())

    init {
        whenever(pluginManager.plugins).thenReturn(pluginFlow)
        whenever(pluginManager.getSearchablePlugins(any())).thenReturn(searchablePluginFlow)
        whenever(pluginManager.getPlugin(any())).thenAnswer { invocation ->
            val platform = invocation.getArgument<String>(0)
            pluginFlow.value.find { it.info.platform == platform }
        }
        whenever(appPreferences.searchHistory).thenReturn(flowOf(emptyList()))
        whenever(appPreferences.defaultDownloadQuality).thenReturn(flowOf(PlayQuality.STANDARD))
        whenever(appPreferences.clickMusicInSearch).thenReturn(flowOf(SearchResultClickAction.PlayMusic))
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
    }

    private fun createViewModel(
        mediaSourceResolver: MediaSourceResolver = EmptyMediaSourceResolver,
        searchSessionStore: SearchSessionStore = createSearchSessionStore(),
    ) = SearchViewModel(
        pluginManager = pluginManager,
        playerController = playerController,
        appPreferences = appPreferences,
        playlistRepository = playlistRepository,
        downloader = downloader,
        mediaSourceResolver = mediaSourceResolver,
        searchSessionStore = searchSessionStore,
    )

    private fun createSearchSessionStore(): SearchSessionStore {
        val gateway = PluginManagerSearchSessionGateway(pluginManager)
        return SearchSessionStore(
            snapshotStore = InMemorySnapshotStore(),
            gateway = gateway,
            signatureProvider = gateway,
            json = Json { ignoreUnknownKeys = true },
            clock = SearchSessionClock { 1_000L },
        )
    }

    @Test
    fun `initial autofocus request is consumed once per view model instance`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        val viewModel = createViewModel()

        assertTrue(viewModel.consumeInitialAutofocusRequest())
        assertFalse(viewModel.consumeInitialAutofocusRequest())
    }

    @Test
    fun `filters searchable plugins and auto-selects first`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val searchable = plugin(platform = "searchable", supportedSearchType = listOf("music"))
        val nonSearchable = plugin(platform = "comment", supportedSearchType = listOf("comment"))
        pluginFlow.value = listOf(searchable, nonSearchable)
        searchablePluginFlow.value = listOf(searchable)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(searchable.info), viewModel.searchablePlugins.value)
        assertEquals(searchable.info.platform, viewModel.selectedPlatform.value)
        assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
    }

    @Test
    fun `uses plugin manager searchable plugin flow for available plugins`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val rawFirst = plugin(platform = "raw-first", supportedSearchType = listOf("music"))
        val searchableSecond = plugin(platform = "searchable-second", supportedSearchType = listOf("music"))
        pluginFlow.value = listOf(rawFirst, searchableSecond)
        searchablePluginFlow.value = listOf(searchableSecond)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(searchableSecond.info), viewModel.searchablePlugins.value)
        assertEquals("searchable-second", viewModel.selectedPlatform.value)
        assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
    }

    @Test
    fun `initial empty searchable plugin flow stays editing while plugins are loading`() = runTest {
        val loadGate = CompletableDeferred<Unit>()
        whenever(pluginManager.ensurePluginsLoaded()).doSuspendableAnswer {
            loadGate.await()
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
        assertTrue(viewModel.searchablePlugins.value.isEmpty())
        assertNull(viewModel.selectedPlatform.value)

        loadGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `empty searchable plugin flow transitions to NO_PLUGIN after plugin load completes`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(SearchPageStatus.NO_PLUGIN, viewModel.pageStatus.value)
        assertTrue(viewModel.searchablePlugins.value.isEmpty())
        assertNull(viewModel.selectedPlatform.value)
    }

    @Test
    fun `searchable plugin arrival restores editing and selects first plugin`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(SearchPageStatus.NO_PLUGIN, viewModel.pageStatus.value)

        val plugin = plugin(platform = "searchable", supportedSearchType = listOf("music"))
        setLoadedPlugins(plugin)
        advanceUntilIdle()

        assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
        assertEquals(listOf(plugin.info), viewModel.searchablePlugins.value)
        assertEquals("searchable", viewModel.selectedPlatform.value)
    }

    @Test
    fun `searchAll triggers per-plugin search and transitions to RESULT`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val plugin = plugin(
            platform = "searchable",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(
                data = listOf(musicItem("1", "Song 1")),
                isEnd = true,
            ),
        )
        setLoadedPlugins(plugin)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        assertEquals(SearchPageStatus.RESULT, viewModel.pageStatus.value)

        // Check via searchResults directly (currentPluginState needs a subscriber to activate stateIn)
        val platform = viewModel.selectedPlatform.value
        val state = viewModel.searchResults.value[SearchMediaType.MUSIC]?.get(platform)
        assertTrue("Expected Success but was $state", state is PluginSearchState.Success)
        assertEquals(1, (state as PluginSearchState.Success).items.size)
    }

    @Test
    fun `selectMediaType reloads searchable plugins for requested type and searches album`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val musicPlugin = plugin(
            platform = "music-only",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(data = listOf(musicItem("1", "Song 1")), isEnd = true),
        )
        val albumPlugin = plugin(
            platform = "album-only",
            supportedSearchType = listOf("album"),
            firstPage = albumSearchResult(),
            firstPageType = "album",
        )
        pluginFlow.value = listOf(musicPlugin, albumPlugin)
        searchablePluginFlow.value = listOf(musicPlugin)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        searchablePluginFlow.value = listOf(albumPlugin)
        viewModel.selectMediaType(SearchMediaType.ALBUM)
        advanceUntilIdle()

        assertEquals("album-only", viewModel.selectedPlatform.value)
        val state = viewModel.searchResults.value[SearchMediaType.ALBUM]?.get("album-only")
        assertTrue("Expected Success but was $state", state is PluginSearchState.Success)
        state as PluginSearchState.Success
        assertTrue(state.items.single() is PluginSearchItem.Album)
        verify(albumPlugin).search("hello", 1, "album")
    }

    @Test
    fun `search waits for plugins matching selected media type`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val musicPlugin = plugin(
            platform = "music-only",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(data = listOf(musicItem("1", "Song 1")), isEnd = true),
        )
        val albumPlugin = plugin(
            platform = "album-only",
            supportedSearchType = listOf("album"),
            firstPage = albumSearchResult(),
            firstPageType = "album",
        )
        val musicSearchablePlugins = MutableStateFlow(listOf(musicPlugin))
        val albumSearchablePlugins = MutableSharedFlow<List<LoadedPlugin>>(replay = 0)
        whenever(pluginManager.getSearchablePlugins("music")).thenReturn(musicSearchablePlugins)
        whenever(pluginManager.getSearchablePlugins("album")).thenReturn(albumSearchablePlugins)
        pluginFlow.value = listOf(musicPlugin, albumPlugin)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        viewModel.selectMediaType(SearchMediaType.ALBUM)
        viewModel.searchAll("hello")
        advanceUntilIdle()

        assertNull(viewModel.searchResults.value[SearchMediaType.ALBUM])
        assertEquals(SearchPageStatus.SEARCHING, viewModel.pageStatus.value)

        albumSearchablePlugins.emit(listOf(albumPlugin))
        advanceUntilIdle()

        val state = viewModel.searchResults.value[SearchMediaType.ALBUM]?.get("album-only")
        assertTrue("Expected Success but was $state", state is PluginSearchState.Success)
        verify(musicPlugin, never()).search("hello", 1, "album")
        verify(albumPlugin).search("hello", 1, "album")
    }

    @Test
    fun `pending search runs when searchable plugins arrive after submit`() = runTest {
        val loadGate = CompletableDeferred<Unit>()
        whenever(pluginManager.ensurePluginsLoaded()).doSuspendableAnswer {
            loadGate.await()
        }
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        assertEquals(SearchPageStatus.SEARCHING, viewModel.pageStatus.value)

        val plugin = plugin(
            platform = "searchable",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(
                data = listOf(musicItem("1", "Song 1")),
                isEnd = true,
            ),
        )
        setLoadedPlugins(plugin)
        advanceUntilIdle()

        assertEquals(SearchPageStatus.RESULT, viewModel.pageStatus.value)
        val state = viewModel.searchResults.value[SearchMediaType.MUSIC]?.get("searchable")
        assertTrue("Expected Success but was $state", state is PluginSearchState.Success)
        assertEquals(1, (state as PluginSearchState.Success).items.size)

        loadGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `load more failure keeps existing search results`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val plugin = plugin(
            platform = "searchable",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(
                data = listOf(musicItem("1", "Song 1")),
                isEnd = false,
            ),
            secondPageThrows = true,
        )
        setLoadedPlugins(plugin)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        val platform = viewModel.selectedPlatform.value
        val initialState = viewModel.searchResults.value[SearchMediaType.MUSIC]?.get(platform)
        assertTrue("Expected Success but was $initialState", initialState is PluginSearchState.Success)
        assertEquals(1, (initialState as PluginSearchState.Success).items.size)

        viewModel.loadMore()
        advanceUntilIdle()

        val afterLoadMore = viewModel.searchResults.value[SearchMediaType.MUSIC]?.get(platform)
        assertTrue("Expected Success but was $afterLoadMore", afterLoadMore is PluginSearchState.Success)
        afterLoadMore as PluginSearchState.Success
        assertEquals(1, afterLoadMore.items.size)
        assertEquals(1, afterLoadMore.page)
    }

    @Test
    fun `load more ignores duplicate request while same page is in flight`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val secondPageGate = CompletableDeferred<SearchResult>()
        val plugin = plugin(
            platform = "searchable",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(
                data = listOf(musicItem("1", "Song 1")),
                isEnd = false,
            ),
        )
        whenever(plugin.search("hello", 2, "music")).doSuspendableAnswer {
            secondPageGate.await()
        }
        setLoadedPlugins(plugin)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        verify(plugin, times(1)).search("hello", 2, "music")

        secondPageGate.complete(
            searchResult(
                data = listOf(musicItem("2", "Song 2")),
                isEnd = true,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.searchResults.value[SearchMediaType.MUSIC]?.get("searchable")
        assertTrue("Expected Success but was $state", state is PluginSearchState.Success)
        state as PluginSearchState.Success
        assertEquals(listOf("1", "2"), state.items.map { (it as PluginSearchItem.Music).item.id })
        assertEquals(2, state.page)
    }

    @Test
    fun `old query load more does not block or overwrite new query load more`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val oldSecondPageGate = CompletableDeferred<SearchResult>()
        val plugin = plugin(
            platform = "searchable",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(
                data = listOf(musicItem("hello-1", "Hello 1")),
                isEnd = false,
            ),
        )
        whenever(plugin.search("hello", 2, "music")).doSuspendableAnswer {
            oldSecondPageGate.await()
        }
        whenever(plugin.search("world", 1, "music")).thenReturn(
            searchResult(
                data = listOf(musicItem("world-1", "World 1")),
                isEnd = false,
            ),
        )
        whenever(plugin.search("world", 2, "music")).thenReturn(
            searchResult(
                data = listOf(musicItem("world-2", "World 2")),
                isEnd = true,
            ),
        )
        setLoadedPlugins(plugin)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.searchAll("world")
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        verify(plugin, times(1)).search("hello", 2, "music")
        verify(plugin, times(1)).search("world", 2, "music")

        oldSecondPageGate.complete(
            searchResult(
                data = listOf(musicItem("hello-2", "Hello 2")),
                isEnd = true,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.searchResults.value[SearchMediaType.MUSIC]?.get("searchable")
        assertTrue("Expected Success but was $state", state is PluginSearchState.Success)
        state as PluginSearchState.Success
        assertEquals(
            listOf("world-1", "world-2"),
            state.items.map { (it as PluginSearchItem.Music).item.id },
        )
        assertEquals(2, state.page)
    }

    @Test
    fun `selectPlatform switches current platform`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val p1 = plugin(platform = "plugin1", supportedSearchType = listOf("music"))
        val p2 = plugin(platform = "plugin2", supportedSearchType = listOf("music"))
        setLoadedPlugins(p1, p2)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("plugin1", viewModel.selectedPlatform.value)

        viewModel.selectPlatform("plugin2")
        assertEquals("plugin2", viewModel.selectedPlatform.value)
    }

    @Test
    fun `backToEditing resets pageStatus to EDITING`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

        val plugin = plugin(
            platform = "searchable",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(data = listOf(musicItem("1", "Song 1")), isEnd = true),
        )
        setLoadedPlugins(plugin)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchAll("hello")
        advanceUntilIdle()

        assertEquals(SearchPageStatus.RESULT, viewModel.pageStatus.value)

        viewModel.backToEditing()
        assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
    }

    @Test
    fun `download enqueues selected item with requested quality`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        val item = musicItem("1", "Song 1")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.download(item, PlayQuality.HIGH)

        verify(downloader).enqueue(listOf(item), PlayQuality.HIGH)
    }

    @Test
    fun `resolveAndPlay defaults to playing only clicked music and preserves original platform`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val item = musicItem(id = "1", title = "Song 1").copy(platform = "source", url = null)
        val resolver = object : MediaSourceResolver {
            override suspend fun resolve(item: MusicItem, quality: String?, sid: String?): MediaSourceResolution {
                val source = MediaSourceResult(
                    url = "https://resolver.example/1.mp3",
                    headers = null,
                    userAgent = null,
                    quality = null,
                )
                return MediaSourceResolution(
                    item = item.copy(url = source.url),
                    source = source,
                    requestedPlatform = item.platform,
                    resolverPlatform = "resolver",
                    redirected = true,
                )
            }
        }

        val viewModel = createViewModel(mediaSourceResolver = resolver)
        advanceUntilIdle()

        viewModel.resolveAndPlay(item, listOf(item))
        advanceUntilIdle()

        argumentCaptor<MusicItem>().apply {
            verify(playerController).playItem(capture())
            assertEquals("source", firstValue.platform)
            assertEquals("https://resolver.example/1.mp3", firstValue.url)
        }
        verify(playerController, never()).playQueue(any(), any())
    }

    @Test
    fun `resolveAndPlay success does not emit navigation event`() = runTest(mainDispatcherRule.dispatcher) {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val item = musicItem(id = "1", title = "Song 1").copy(
            platform = "source",
            url = "https://resolver.example/1.mp3",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<SearchViewModel.PlayEvent>()
        val collectJob = launch {
            viewModel.playEvent.collect { event -> events += event }
        }

        viewModel.resolveAndPlay(item, listOf(item))
        advanceUntilIdle()
        collectJob.cancelAndJoin()

        verify(playerController).playItem(item)
        verify(playerController, never()).playQueue(any(), any())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `resolveAndPlay replaces queue when search click setting requests replacement`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        whenever(appPreferences.clickMusicInSearch).thenReturn(flowOf(SearchResultClickAction.PlayMusicAndReplace))

        val item = musicItem(id = "1", title = "Song 1").copy(platform = "source", url = null)
        val second = musicItem(id = "2", title = "Song 2").copy(platform = "source", url = null)
        val resolver = object : MediaSourceResolver {
            override suspend fun resolve(item: MusicItem, quality: String?, sid: String?): MediaSourceResolution {
                val source = MediaSourceResult(
                    url = "https://resolver.example/1.mp3",
                    headers = null,
                    userAgent = null,
                    quality = null,
                )
                return MediaSourceResolution(
                    item = item.copy(url = source.url),
                    source = source,
                    requestedPlatform = item.platform,
                    resolverPlatform = "resolver",
                    redirected = true,
                )
            }
        }

        val viewModel = createViewModel(mediaSourceResolver = resolver)
        advanceUntilIdle()

        viewModel.resolveAndPlay(item, listOf(item, second))
        advanceUntilIdle()

        argumentCaptor<List<MusicItem>>().apply {
            verify(playerController).playQueue(capture(), eq(0))
            assertEquals("https://resolver.example/1.mp3", firstValue.first().url)
            assertEquals("2", firstValue[1].id)
        }
        verify(playerController, never()).playItem(any())
    }

    private fun setLoadedPlugins(vararg plugins: LoadedPlugin) {
        val list = plugins.toList()
        pluginFlow.value = list
        searchablePluginFlow.value = list
    }

    private suspend fun plugin(
        platform: String,
        supportedSearchType: List<String>,
        firstPage: SearchResult? = null,
        firstPageType: String = "music",
        secondPageThrows: Boolean = false,
    ): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        val info = PluginInfo(
            platform = platform,
            version = "1.0.0",
            author = null,
            description = null,
            srcUrl = "https://example.com/$platform.js",
            supportedSearchType = supportedSearchType,
        )
        whenever(plugin.info).thenReturn(info)

        if (firstPage != null) {
            whenever(plugin.search("hello", 1, firstPageType)).thenReturn(firstPage)
            if (secondPageThrows) {
                whenever(plugin.search("hello", 2, firstPageType)).thenThrow(RuntimeException("page failure"))
            } else {
                whenever(plugin.search("hello", 2, firstPageType)).thenReturn(firstPage.copy(isEnd = true))
            }
        }
        return plugin
    }

    private fun searchResult(data: List<MusicItem>, isEnd: Boolean): SearchResult {
        return SearchResult(
            isEnd = isEnd,
            data = data.map { PluginSearchItem.Music(it) },
        )
    }

    private fun albumSearchResult(): SearchResult {
        return SearchResult(
            isEnd = true,
            data = listOf(
                PluginSearchItem.Album(
                    AlbumItemBase(
                        id = "album-1",
                        platform = "album-only",
                        title = "Album 1",
                        date = "2026-05-10",
                        artist = "Artist",
                        description = null,
                        artwork = null,
                        worksNum = null,
                        raw = emptyMap(),
                    ),
                ),
            ),
        )
    }

    private fun musicItem(id: String, title: String): MusicItem {
        return MusicItem(
            id = id,
            platform = "searchable",
            title = title,
            artist = "Artist $id",
            album = null,
            duration = 180_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
    }

    private class InMemorySnapshotStore : SnapshotStore {
        override suspend fun read(namespace: String, key: String): RuntimeSnapshot? = null
        override suspend fun write(snapshot: RuntimeSnapshot) = Unit
        override suspend fun delete(namespace: String, key: String) = Unit
        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int = 0
        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int = 0
        override suspend fun keys(namespace: String, limit: Int): List<String> = emptyList()
    }
}
