package com.hank.musicfree.feature.search

import com.hank.musicfree.core.media.EmptyMediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.search.runtime.SearchPluginSignatureProvider
import com.hank.musicfree.feature.search.runtime.SearchSessionClock
import com.hank.musicfree.feature.search.runtime.SearchSessionGateway
import com.hank.musicfree.feature.search.runtime.SearchSessionStore
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.PluginSearchItem
import com.hank.musicfree.plugin.api.SearchResult
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelRuntimeStoreTest {

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
        whenever(appPreferences.searchHistory).thenReturn(flowOf(emptyList()))
        whenever(appPreferences.defaultDownloadQuality).thenReturn(flowOf(PlayQuality.STANDARD))
        whenever(appPreferences.clickMusicInSearch).thenReturn(flowOf(SearchResultClickAction.PlayMusic))
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
    }

    @Test
    fun viewModelUsesStoreStateAfterRecreation() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        val loaded = loadedPlugin("demo")
        pluginFlow.value = listOf(loaded)
        searchablePluginFlow.value = listOf(loaded)
        val store = SearchSessionStore(
            snapshotStore = InMemorySnapshotStore(),
            gateway = StaticSearchGateway(
                SearchResult(
                    isEnd = true,
                    data = listOf(PluginSearchItem.Music(music("song-1"))),
                ),
            ),
            signatureProvider = SearchPluginSignatureProvider { "sig" },
            json = Json { ignoreUnknownKeys = true },
            clock = SearchSessionClock { 1_000L },
        )
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(loaded.info))
        store.search("hello")

        val first = createViewModel(store)
        advanceUntilIdle()
        assertEquals("hello", first.currentQuery.value)
        assertEquals("demo", first.selectedPlatform.value)

        val recreated = createViewModel(store)
        advanceUntilIdle()

        assertEquals("hello", recreated.currentQuery.value)
        assertEquals("demo", recreated.selectedPlatform.value)
        val success = recreated.searchResults.value.getValue(SearchMediaType.MUSIC).getValue("demo")
            as PluginSearchState.Success
        assertEquals("song-1", (success.items.single() as PluginSearchItem.Music).item.id)
    }

    private fun createViewModel(store: SearchSessionStore): SearchViewModel = SearchViewModel(
        pluginManager = pluginManager,
        playerController = playerController,
        appPreferences = appPreferences,
        playlistRepository = playlistRepository,
        downloader = downloader,
        mediaSourceResolver = EmptyMediaSourceResolver,
        searchSessionStore = store,
    )

    private class StaticSearchGateway(
        private val result: SearchResult,
    ) : SearchSessionGateway {
        override suspend fun search(
            platform: String,
            query: String,
            page: Int,
            mediaType: SearchMediaType,
        ): SearchResult = result
    }

    private class InMemorySnapshotStore : SnapshotStore {
        private val snapshots = mutableMapOf<Pair<String, String>, RuntimeSnapshot>()

        override suspend fun read(namespace: String, key: String): RuntimeSnapshot? =
            snapshots[namespace to key]

        override suspend fun write(snapshot: RuntimeSnapshot) {
            snapshots[snapshot.namespace to snapshot.key] = snapshot
        }

        override suspend fun delete(namespace: String, key: String) {
            snapshots.remove(namespace to key)
        }

        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int {
            val expired = snapshots.filter { (identity, snapshot) ->
                identity.first == namespace && snapshot.isExpired(nowEpochMs)
            }.keys
            expired.forEach { snapshots.remove(it) }
            return expired.size
        }

        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int = 0

        override suspend fun keys(namespace: String, limit: Int): List<String> =
            snapshots.values
                .filter { it.namespace == namespace }
                .sortedByDescending { it.updatedAtEpochMs }
                .take(limit)
                .map { it.key }
    }
}

private fun loadedPlugin(platform: String): LoadedPlugin {
    val plugin = mock<LoadedPlugin>()
    val info = PluginInfo(
        platform = platform,
        version = "1.0.0",
        author = null,
        description = null,
        srcUrl = "https://example.com/$platform.js",
        supportedSearchType = listOf(SearchMediaType.MUSIC.key),
        supportedMethods = setOf("search"),
        hash = "hash-$platform",
    )
    whenever(plugin.info).thenReturn(info)
    return plugin
}

private fun music(id: String): MusicItem = MusicItem(
    id = id,
    platform = "demo",
    title = id,
    artist = "Artist $id",
    album = null,
    duration = 180_000L,
    url = null,
    artwork = null,
    qualities = null,
)
