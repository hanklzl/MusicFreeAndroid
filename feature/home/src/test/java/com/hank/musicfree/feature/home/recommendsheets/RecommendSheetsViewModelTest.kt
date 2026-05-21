package com.hank.musicfree.feature.home.recommendsheets

import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.api.PaginationResult
import com.hank.musicfree.plugin.api.MusicSheetGroupItem
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.RecommendSheetTagsResult
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.CompletableDeferred
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `pending sheet load does not overwrite unsupported empty state`() = runTest {
        val sheetGate = CompletableDeferred<PaginationResult<MusicSheetItemBase>>()
        val capable = plugin(
            platform = "capable",
            methods = setOf("getRecommendSheetsByTag"),
        )
        runBlocking {
            whenever(capable.getRecommendSheetsByTag(any(), any())).doSuspendableAnswer {
                sheetGate.await()
            }
        }
        enabledPlugins.value = listOf(capable)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loading)

        enabledPlugins.value = emptyList()
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)

        sheetGate.complete(
            PaginationResult(
                isEnd = true,
                data = listOf(musicSheet(id = "stale", title = "旧结果")),
            ),
        )
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)
        assertTrue(viewModel.uiState.value.sheets.isEmpty())
    }

    @Test
    fun `pending tag load does not overwrite unsupported empty state`() = runTest {
        val tagsGate = CompletableDeferred<RecommendSheetTagsResult?>()
        val capable = plugin(
            platform = "capable",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
        )
        runBlocking {
            whenever(capable.getRecommendSheetTags()).doSuspendableAnswer {
                tagsGate.await()
            }
        }
        enabledPlugins.value = listOf(capable)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loading)

        enabledPlugins.value = emptyList()
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)

        tagsGate.complete(
            RecommendSheetTagsResult(
                pinned = listOf(musicSheet(id = "stale-tag", title = "旧标签")),
                data = emptyList(),
            ),
        )
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)
        assertTrue(viewModel.uiState.value.tags.isEmpty())
        assertTrue(viewModel.uiState.value.sheets.isEmpty())
    }

    @Test
    fun `pending loadMore does not overwrite unsupported empty state`() = runTest {
        val nextPageGate = CompletableDeferred<PaginationResult<MusicSheetItemBase>>()
        val capable = plugin(
            platform = "capable",
            methods = setOf("getRecommendSheetsByTag"),
        )
        runBlocking {
            whenever(capable.getRecommendSheetsByTag(any(), any())).doSuspendableAnswer { invocation ->
                val page = invocation.getArgument<Int>(1)
                if (page == 1) {
                    PaginationResult(
                        isEnd = false,
                        data = listOf(musicSheet(id = "first", title = "第一页")),
                    )
                } else {
                    nextPageGate.await()
                }
            }
        }
        enabledPlugins.value = listOf(capable)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()
        assertEquals(listOf("first"), viewModel.uiState.value.sheets.map { it.id })
        assertFalse(viewModel.uiState.value.isEnd)

        viewModel.loadMore()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loadingMore)

        enabledPlugins.value = emptyList()
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)

        nextPageGate.complete(
            PaginationResult(
                isEnd = true,
                data = listOf(musicSheet(id = "stale-next", title = "旧下一页")),
            ),
        )
        advanceUntilIdle()

        assertEquals(null, viewModel.selectedPlugin.value)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)
        assertTrue(viewModel.uiState.value.sheets.isEmpty())
        assertFalse(viewModel.uiState.value.loadingMore)
    }

    @Test
    fun `switching plugin keeps each recommend scene independent`() = runTest {
        val pluginA = plugin(
            platform = "plugin-a",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
            tags = RecommendSheetTagsResult(
                pinned = listOf(musicSheet(id = "a-default", title = "A 默认")),
                data = emptyList(),
            ),
            sheets = listOf(musicSheet(id = "a-1", title = "A-1")),
        )
        val pluginB = plugin(
            platform = "plugin-b",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
            tags = RecommendSheetTagsResult(
                pinned = listOf(
                    musicSheet(id = "b-default", title = "B 默认"),
                ),
                data = listOf(
                    MusicSheetGroupItem(
                        title = "B 分组",
                        data = listOf(
                            musicSheet(id = "b-other", title = "B 其他"),
                        ),
                    ),
                ),
            ),
            sheets = listOf(musicSheet(id = "b-1", title = "B-1")),
        )
        enabledPlugins.value = listOf(pluginA, pluginB)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        viewModel.ensureSceneLoaded("plugin-a")
        viewModel.ensureSceneLoaded("plugin-b")
        advanceUntilIdle()

        val sceneBefore = viewModel.pagerUiState.value.scenes["plugin-a"] ?: RecommendSheetsSceneState()
        viewModel.selectPlugin("plugin-b")
        advanceUntilIdle()
        viewModel.selectTag("plugin-b", "b-other")
        advanceUntilIdle()

        val sceneAfter = viewModel.pagerUiState.value.scenes["plugin-a"] ?: RecommendSheetsSceneState()
        assertEquals(sceneBefore.sheets, sceneAfter.sheets)
        assertEquals(sceneBefore.selectedTagId, sceneAfter.selectedTagId)
        assertEquals("b-other", viewModel.pagerUiState.value.scenes["plugin-b"]?.selectedTagId)
    }

    @Test
    fun `ensureSceneLoaded does not duplicate first page request`() = runTest {
        val tagsGate = CompletableDeferred<RecommendSheetTagsResult?>()
        val sheetsGate = CompletableDeferred<PaginationResult<MusicSheetItemBase>>()
        val sheetRequestCount = AtomicInteger(0)
        val capable = mock<LoadedPlugin>()
        runBlocking {
            whenever(capable.info).thenReturn(
                PluginInfo(
                    platform = "capable",
                    version = "1.0.0",
                    author = null,
                    description = null,
                    srcUrl = null,
                    supportedSearchType = listOf("music"),
                    supportedMethods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
                ),
            )
            whenever(capable.getRecommendSheetTags()).doSuspendableAnswer { tagsGate.await() }
            whenever(capable.getRecommendSheetsByTag(any(), any())).doSuspendableAnswer {
                sheetRequestCount.incrementAndGet()
                sheetsGate.await()
            }
        }
        enabledPlugins.value = listOf(capable)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        viewModel.ensureSceneLoaded("capable")
        viewModel.ensureSceneLoaded("capable")
        viewModel.ensureSceneLoaded("capable")
        advanceUntilIdle()
        assertEquals(0, sheetRequestCount.get())

        tagsGate.complete(
            RecommendSheetTagsResult(
                pinned = listOf(musicSheet(id = "tag", title = "tag")),
                data = emptyList(),
            ),
        )
        sheetsGate.complete(
            PaginationResult(
                isEnd = true,
                data = listOf(musicSheet(id = "s", title = "S")),
            ),
        )
        advanceUntilIdle()
        assertEquals(1, sheetRequestCount.get())
    }

    @Test
    fun `same platform plugin instance replacement with old request in flight does not overwrite replacement result`() = runTest {
        val oldTagGate = CompletableDeferred<RecommendSheetTagsResult?>()
        val oldSheetGate = CompletableDeferred<PaginationResult<MusicSheetItemBase>>()
        val oldTagRequestCount = AtomicInteger(0)
        val oldSheetRequestCount = AtomicInteger(0)
        val replacementSheetRequestCount = AtomicInteger(0)
        val oldPlugin = mock<LoadedPlugin>()
        val replacementPlugin = mock<LoadedPlugin>()

        runBlocking {
            whenever(oldPlugin.info).thenReturn(
                PluginInfo(
                    platform = "same-platform",
                    version = "1.0.0",
                    author = null,
                    description = null,
                    srcUrl = null,
                    supportedSearchType = listOf("music"),
                    supportedMethods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
                ),
            )
            whenever(replacementPlugin.info).thenReturn(
                PluginInfo(
                    platform = "same-platform",
                    version = "1.0.1",
                    author = null,
                    description = null,
                    srcUrl = null,
                    supportedSearchType = listOf("music"),
                    supportedMethods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
                ),
            )
            whenever(oldPlugin.getRecommendSheetTags()).doSuspendableAnswer {
                oldTagRequestCount.incrementAndGet()
                oldTagGate.await()
            }
            whenever(oldPlugin.getRecommendSheetsByTag(any(), any())).doSuspendableAnswer {
                oldSheetRequestCount.incrementAndGet()
                oldSheetGate.await()
            }
            whenever(replacementPlugin.getRecommendSheetTags()).thenReturn(
                RecommendSheetTagsResult(
                    pinned = listOf(musicSheet(id = "new-tag", title = "新标签")),
                    data = emptyList(),
                ),
            )
            whenever(replacementPlugin.getRecommendSheetsByTag(any(), any())).doSuspendableAnswer {
                replacementSheetRequestCount.incrementAndGet()
                PaginationResult(
                    isEnd = true,
                    data = listOf(musicSheet(id = "new-sheet", title = "新歌单")),
                )
            }
        }

        enabledPlugins.value = listOf(oldPlugin)
        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()
        assertEquals(1, oldTagRequestCount.get())
        assertTrue(viewModel.uiState.value.loading)

        enabledPlugins.value = listOf(replacementPlugin)
        advanceUntilIdle()

        oldTagGate.complete(
            RecommendSheetTagsResult(
                pinned = listOf(musicSheet(id = "old-tag", title = "旧标签")),
                data = emptyList(),
            ),
        )
            oldSheetGate.complete(
                PaginationResult(
                    isEnd = true,
                    data = listOf(musicSheet(id = "old-sheet", title = "旧歌单")),
            ),
        )
        advanceUntilIdle()

        val scene = viewModel.pagerUiState.value.scenes["same-platform"] ?: RecommendSheetsSceneState()
        assertEquals(listOf("new-sheet"), scene.sheets.map { it.id })
        assertEquals("__default__", scene.selectedTagId)
        assertTrue(
            "new tag should be present in refreshed tag list",
            scene.tags.any { it.id == "new-tag" },
        )
        assertFalse(
            "old tag should not overwrite current scene",
            scene.tags.any { it.id == "old-tag" },
        )
        assertEquals(0, oldSheetRequestCount.get())
        assertEquals(1, replacementSheetRequestCount.get())
    }

    @Test
    fun `already-loaded scene reloads when same platform plugin instance changes`() = runTest {
        val oldPlugin = plugin(
            platform = "same-platform",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
            tags = RecommendSheetTagsResult(
                pinned = listOf(musicSheet(id = "old-tag", title = "旧标签")),
                data = emptyList(),
            ),
            sheets = listOf(musicSheet(id = "old-sheet", title = "旧歌单")),
        )
        val newTagGate = CompletableDeferred<RecommendSheetTagsResult?>()
        val replacementPlugin = mock<LoadedPlugin>()
        runBlocking {
            whenever(replacementPlugin.info).thenReturn(
                PluginInfo(
                    platform = "same-platform",
                    version = "1.0.1",
                    author = null,
                    description = null,
                    srcUrl = null,
                    supportedSearchType = listOf("music"),
                    supportedMethods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
                ),
            )
            whenever(replacementPlugin.getRecommendSheetTags()).doSuspendableAnswer { newTagGate.await() }
            whenever(replacementPlugin.getRecommendSheetsByTag(any(), any())).thenReturn(
                PaginationResult(
                    isEnd = true,
                    data = listOf(musicSheet(id = "new-sheet", title = "新歌单")),
                ),
            )
        }

        enabledPlugins.value = listOf(oldPlugin)
        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        val before = viewModel.pagerUiState.value.scenes["same-platform"] ?: RecommendSheetsSceneState()
        assertEquals(listOf("old-sheet"), before.sheets.map { it.id })

        enabledPlugins.value = listOf(replacementPlugin)
        advanceUntilIdle()

        val resetScene = viewModel.pagerUiState.value.scenes["same-platform"] ?: RecommendSheetsSceneState()
        assertTrue(resetScene.sheets.isEmpty())
        assertEquals(false, resetScene.loaded)
        assertEquals(null, resetScene.selectedTagId)

        newTagGate.complete(
            RecommendSheetTagsResult(
                pinned = listOf(musicSheet(id = "new-tag", title = "新标签")),
                data = emptyList(),
            ),
        )
        advanceUntilIdle()

        val afterReload = viewModel.pagerUiState.value.scenes["same-platform"] ?: RecommendSheetsSceneState()
        assertEquals(listOf("new-sheet"), afterReload.sheets.map { it.id })
        assertEquals("__default__", afterReload.selectedTagId)
        assertTrue(afterReload.tags.any { it.id == "new-tag" })
    }

    @Test
    fun `tag switch only resets selected recommend scene`() = runTest {
        val pluginA = plugin(
            platform = "plugin-a",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
            tags = RecommendSheetTagsResult(
                pinned = listOf(musicSheet(id = "a-default", title = "A 默认")),
                data = emptyList(),
            ),
            sheets = listOf(musicSheet(id = "a-1", title = "A-1")),
        )
        val pluginB = plugin(
            platform = "plugin-b",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
            tags = RecommendSheetTagsResult(
                pinned = listOf(
                    musicSheet(id = "b-default", title = "B 默认"),
                    musicSheet(id = "b-other", title = "B 其他"),
                ),
                data = listOf(
                    MusicSheetGroupItem(
                        title = "B 分组",
                        data = listOf(
                            musicSheet(id = "b-other-group", title = "B 其他（分组）"),
                        ),
                    ),
                ),
            ),
            sheets = listOf(musicSheet(id = "b-1", title = "B-1")),
        )
        enabledPlugins.value = listOf(pluginA, pluginB)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        viewModel.ensureSceneLoaded("plugin-a")
        viewModel.ensureSceneLoaded("plugin-b")
        advanceUntilIdle()

        val beforeA = viewModel.pagerUiState.value.scenes["plugin-a"] ?: RecommendSheetsSceneState()
        viewModel.selectTag("plugin-b", "b-other")
        advanceUntilIdle()

        val afterA = viewModel.pagerUiState.value.scenes["plugin-a"] ?: RecommendSheetsSceneState()
        assertEquals(beforeA.sheets, afterA.sheets)
        assertEquals(beforeA.selectedTagId, afterA.selectedTagId)
        assertEquals("b-other", viewModel.pagerUiState.value.scenes["plugin-b"]?.selectedTagId)
    }

    private fun plugin(
        platform: String,
        methods: Set<String>,
        tags: RecommendSheetTagsResult? = null,
        sheets: List<MusicSheetItemBase> = emptyList(),
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
                PaginationResult<MusicSheetItemBase>(isEnd = true, data = sheets)
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
