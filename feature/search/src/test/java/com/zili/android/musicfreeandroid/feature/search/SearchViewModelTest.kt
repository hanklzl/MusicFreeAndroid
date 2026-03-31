package com.zili.android.musicfreeandroid.feature.search

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pluginManager: PluginManager = mock()
    private val playerController: PlayerController = mock()
    private val pluginFlow = MutableStateFlow<List<LoadedPlugin>>(emptyList())

    init {
        whenever(pluginManager.plugins).thenReturn(pluginFlow)
        whenever(pluginManager.getPlugin(any())).thenAnswer { invocation ->
            val platform = invocation.getArgument<String>(0)
            pluginFlow.value.find { it.info.platform == platform }
        }
    }

    @Test
    fun `filters searchable plugins and preserves selected plugin while still available`() = runTest {
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)

        val searchable = plugin(platform = "searchable", supportedSearchType = listOf("music"))
        val nonSearchable = plugin(platform = "comment", supportedSearchType = listOf("comment"))
        pluginFlow.value = listOf(searchable, nonSearchable)

        val viewModel = SearchViewModel(pluginManager, playerController)
        advanceUntilIdle()

        assertEquals(listOf(searchable.info), viewModel.availablePlugins.value)
        assertEquals(listOf(searchable.info, nonSearchable.info), viewModel.installedPlugins.value)
        assertEquals(searchable.info.platform, viewModel.selectedPlugin.value)
        assertTrue(viewModel.uiState.value is SearchUiState.Idle)

        pluginFlow.value = listOf(searchable, plugin("extra", supportedSearchType = listOf("music")))
        advanceUntilIdle()

        assertEquals(searchable.info.platform, viewModel.selectedPlugin.value)
        assertEquals(2, viewModel.availablePlugins.value.size)
    }

    @Test
    fun `plugin update keeps selected searchable plugin stable`() = runTest {
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)

        val searchable = plugin(platform = "searchable", supportedSearchType = listOf("music"))
        val upgraded = plugin(platform = "searchable", supportedSearchType = listOf("music"))
        pluginFlow.value = listOf(searchable)

        val viewModel = SearchViewModel(pluginManager, playerController)
        advanceUntilIdle()

        assertEquals(searchable.info.platform, viewModel.selectedPlugin.value)

        pluginFlow.value = listOf(upgraded)
        advanceUntilIdle()

        assertEquals(searchable.info.platform, viewModel.selectedPlugin.value)
        assertEquals(listOf(upgraded.info), viewModel.availablePlugins.value)
        assertTrue(viewModel.uiState.value is SearchUiState.Idle)
    }

    @Test
    fun `no installed plugins enters no plugins state`() = runTest {
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)

        val viewModel = SearchViewModel(pluginManager, playerController)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SearchUiState.NoPlugins)
        assertTrue(viewModel.availablePlugins.value.isEmpty())
        assertTrue(viewModel.installedPlugins.value.isEmpty())
        assertNull(viewModel.selectedPlugin.value)
    }

    @Test
    fun `installed but non searchable plugins enters no searchable plugins state`() = runTest {
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)
        pluginFlow.value = listOf(plugin(platform = "comment", supportedSearchType = listOf("comment")))

        val viewModel = SearchViewModel(pluginManager, playerController)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SearchUiState.NoSearchablePlugins)
        assertTrue(viewModel.availablePlugins.value.isEmpty())
        assertEquals(listOf("comment"), viewModel.installedPlugins.value.map { it.platform })
        assertNull(viewModel.selectedPlugin.value)
    }

    @Test
    fun `load more failure keeps existing search results`() = runTest {
        whenever(pluginManager.loadAllPlugins()).thenReturn(Unit)

        val plugin = plugin(
            platform = "searchable",
            supportedSearchType = listOf("music"),
            firstPage = searchResult(
                data = listOf(
                    musicItem("1", "Song 1"),
                ),
                isEnd = false,
            ),
            secondPageThrows = true,
        )
        pluginFlow.value = listOf(plugin)

        val viewModel = SearchViewModel(pluginManager, playerController)
        advanceUntilIdle()

        viewModel.search("hello")
        advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertTrue(initialState is SearchUiState.Success)
        assertEquals(1, (initialState as SearchUiState.Success).items.size)

        viewModel.loadMore()
        advanceUntilIdle()

        val afterLoadMore = viewModel.uiState.value
        assertTrue(afterLoadMore is SearchUiState.Success)
        afterLoadMore as SearchUiState.Success
        assertEquals(1, afterLoadMore.items.size)
        assertEquals(1, afterLoadMore.page)
    }

    private suspend fun plugin(
        platform: String,
        supportedSearchType: List<String>,
        firstPage: SearchResult? = null,
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
            whenever(plugin.search("hello", 1, "music")).thenReturn(firstPage)
            if (secondPageThrows) {
                whenever(plugin.search("hello", 2, "music")).thenThrow(RuntimeException("page failure"))
            } else {
                whenever(plugin.search("hello", 2, "music")).thenReturn(firstPage.copy(isEnd = true))
            }
        }
        return plugin
    }

    private fun searchResult(data: List<MusicItem>, isEnd: Boolean): SearchResult {
        return SearchResult(
            isEnd = isEnd,
            data = data,
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
}
