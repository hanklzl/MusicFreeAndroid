package com.zili.android.musicfreeandroid.feature.home.toplist

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetGroupItem
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TopListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val pluginManager: PluginManager = mock()
    private val enabledPlugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        whenever(pluginManager.getSortedEnabledPlugins()).thenReturn(enabledPlugins)
        whenever(pluginManager.getPlugin(any())).thenAnswer { invocation ->
            val platform = invocation.getArgument<String>(0)
            enabledPlugins.value.firstOrNull { it.info.platform == platform }
        }
        runBlocking {
            whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `availablePlugins only includes plugins supporting getTopLists and preserves sorted enabled order`() = runTest {
        val unsupported = plugin("unsupported", setOf("search"))
        val capableFirst = plugin("capable-first", setOf("getTopLists"), topLists = listOf(musicGroup("first")))
        val capableSecond = plugin("capable-second", setOf("getTopLists"), topLists = listOf(musicGroup("second")))

        enabledPlugins.value = listOf(unsupported, capableSecond, capableFirst)

        val viewModel = TopListViewModel(pluginManager)
        advanceUntilIdle()

        assertEquals(
            listOf("capable-second", "capable-first"),
            viewModel.availablePlugins.value.map { it.platform },
        )
    }

    @Test
    fun `no capable plugins sets error state with no plugin message`() = runTest {
        val searchOnly = plugin("search-only", setOf("search"))
        enabledPlugins.value = listOf(searchOnly)

        val viewModel = TopListViewModel(pluginManager)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.availablePlugins.value.map { it.platform })
        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals(
            "当前没有支持榜单的插件",
            (viewModel.uiState.value as TopListUiState.Error).message,
        )
    }

    @Test
    fun `first capable plugin is auto-selected and getTopLists is loaded`() = runTest {
        val firstCapable = plugin("capable-first", setOf("getTopLists"), topLists = listOf(musicGroup("first")))
        val secondCapable = plugin("capable-second", setOf("getTopLists"), topLists = listOf(musicGroup("second")))
        enabledPlugins.value = listOf(firstCapable, secondCapable)

        val viewModel = TopListViewModel(pluginManager)
        advanceUntilIdle()

        assertEquals("capable-first", viewModel.selectedPlugin.value)
        verify(firstCapable).getTopLists()
        verify(secondCapable, never()).getTopLists()
    }

    @Test
    fun `pending top list load does not overwrite unsupported error state`() = runTest {
        val topListsGate = CompletableDeferred<List<MusicSheetGroupItem>>()
        val capable = plugin("capable", setOf("getTopLists"))
        runBlocking {
            whenever(capable.getTopLists()).doSuspendableAnswer {
                topListsGate.await()
            }
        }
        enabledPlugins.value = listOf(capable)

        val viewModel = TopListViewModel(pluginManager)
        advanceUntilIdle()
        assertEquals(TopListUiState.Loading, viewModel.uiState.value)

        enabledPlugins.value = emptyList()
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals(
            "当前没有支持榜单的插件",
            (viewModel.uiState.value as TopListUiState.Error).message,
        )

        topListsGate.complete(listOf(musicGroup("stale")))
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals(
            "当前没有支持榜单的插件",
            (viewModel.uiState.value as TopListUiState.Error).message,
        )
    }

    @Test
    fun `pending old plugin load does not overwrite newly selected plugin result`() = runTest {
        val firstGate = CompletableDeferred<List<MusicSheetGroupItem>>()
        val firstCapable = plugin("capable-first", setOf("getTopLists"))
        runBlocking {
            whenever(firstCapable.getTopLists()).doSuspendableAnswer {
                firstGate.await()
            }
        }
        val secondCapable = plugin("capable-second", setOf("getTopLists"), topLists = listOf(musicGroup("second")))
        enabledPlugins.value = listOf(firstCapable, secondCapable)

        val viewModel = TopListViewModel(pluginManager)
        advanceUntilIdle()
        assertEquals("capable-first", viewModel.selectedPlugin.value)
        assertEquals(TopListUiState.Loading, viewModel.uiState.value)

        viewModel.selectPlugin("capable-second")
        advanceUntilIdle()

        assertEquals("capable-second", viewModel.selectedPlugin.value)
        assertEquals(
            listOf("second"),
            (viewModel.uiState.value as TopListUiState.Success).groups.map { it.title },
        )

        firstGate.complete(listOf(musicGroup("stale-first")))
        advanceUntilIdle()

        assertEquals("capable-second", viewModel.selectedPlugin.value)
        assertEquals(
            listOf("second"),
            (viewModel.uiState.value as TopListUiState.Success).groups.map { it.title },
        )
    }

    private fun plugin(
        platform: String,
        methods: Set<String>,
        topLists: List<MusicSheetGroupItem> = emptyList(),
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
                supportedMethods = methods,
            ),
        )
        runBlocking {
            whenever(plugin.getTopLists()).thenReturn(topLists)
        }
        return plugin
    }

    private fun musicGroup(title: String): MusicSheetGroupItem = MusicSheetGroupItem(
        title = title,
        data = listOf(
            MusicSheetItemBase(
                id = "list-$title",
                platform = "test",
                title = "榜单$title",
                artist = null,
                description = null,
                coverImg = null,
                artwork = null,
                worksNum = null,
                raw = emptyMap(),
            ),
        ),
    )
}
