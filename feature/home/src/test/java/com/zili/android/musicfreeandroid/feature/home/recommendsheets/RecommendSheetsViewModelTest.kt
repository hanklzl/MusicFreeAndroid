package com.zili.android.musicfreeandroid.feature.home.recommendsheets

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PaginationResult
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.RecommendSheetTagsResult
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendSheetsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val pluginManager: PluginManager = mock()
    private val enabledPlugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())

    @Before
    fun setup() {
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
    fun `availablePlugins only includes plugins supporting getRecommendSheetsByTag and preserves sorted enabled order`() = runTest {
        val unsupported = plugin("unsupported", setOf("search"))
        val firstCapable = plugin(
            platform = "capable-a",
            methods = setOf("getRecommendSheetsByTag"),
        )
        val secondCapable = plugin(
            platform = "capable-b",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
        )
        enabledPlugins.value = listOf(unsupported, secondCapable, firstCapable)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        assertEquals(
            listOf("capable-b", "capable-a"),
            viewModel.availablePlugins.value.map { it.platform },
        )
        assertEquals("capable-b", viewModel.selectedPlugin.value)
        verify(secondCapable).getRecommendSheetsByTag(eq(mapOf("id" to "")), eq(1))
    }

    @Test
    fun `no capable plugins shows unsupported empty state and stops loading`() = runTest {
        enabledPlugins.value = listOf(plugin("search-only", setOf("search")))

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        assertTrue(viewModel.availablePlugins.value.isEmpty())
        assertEquals(null, viewModel.selectedPlugin.value)
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(true, viewModel.uiState.value.isEnd)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)
    }

    @Test
    fun `selectTag passes merged tag payload including id title and raw`() = runTest {
        val requestedTagPayloads = mutableListOf<Map<String, Any?>>()
        val tag = musicSheet(
            id = "rock",
            title = "摇滚",
            raw = mapOf(
                "id" to "raw-id",
                "title" to "raw-title",
                "category" to "genre-rock",
            ),
        )
        val capable = plugin(
            platform = "capable",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
            tags = RecommendSheetTagsResult(pinned = listOf(tag), data = emptyList()),
            getRecommendSheetsByTag = requestedTagPayloads::add,
        )
        enabledPlugins.value = listOf(capable)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        viewModel.selectTag("rock")
        advanceUntilIdle()

        val payload = requestedTagPayloads.lastOrNull { it["id"] == "rock" }
        assertEquals(
            mapOf(
                "id" to "rock",
                "title" to "摇滚",
                "category" to "genre-rock",
            ),
            payload,
        )
    }

    private fun plugin(
        platform: String,
        methods: Set<String>,
        tags: RecommendSheetTagsResult? = null,
        getRecommendSheetsByTag: ((Map<String, Any?>) -> Unit)? = null,
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
            whenever(plugin.getRecommendSheetTags()).thenReturn(tags)
            whenever(plugin.getRecommendSheetsByTag(any(), any())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val payload = invocation.getArgument<Map<String, Any?>>(0)
                getRecommendSheetsByTag?.invoke(payload)
                PaginationResult<MusicSheetItemBase>(isEnd = true, data = emptyList())
            }
        }
        return plugin
    }

    private fun musicSheet(
        id: String,
        title: String,
        raw: Map<String, Any?> = emptyMap(),
    ): MusicSheetItemBase = MusicSheetItemBase(
        id = id,
        platform = "capable",
        title = title,
        artist = null,
        description = null,
        coverImg = null,
        artwork = null,
        worksNum = null,
        raw = raw,
    )
}
