# Scene Pager Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将搜索结果、推荐歌单和榜单的 Tab 从松手后硬切的轻量 swipe 升级为完整 `HorizontalPager` scene，并让每个 Tab 独立保存加载、分页、错误和选中子状态。

**Architecture:** 先在 `:core` 提供一个无业务状态的 `MusicFreeScenePagerTabs` 组件，负责 `ScrollableTabRow` 与 `HorizontalPager` 双向同步。随后把推荐歌单、榜单和搜索运行态从单一当前状态改为 `selectedKey + scenes map`，UI 按 scene 渲染并把加载入口改为按 scene key 幂等。搜索继续通过 `SearchSessionStore` 承载高价值运行态，但扩展为每个 media type 独立的 selected platform、plugin list 和结果 map。

**Tech Stack:** Kotlin, Jetpack Compose Foundation Pager, Material3, Hilt ViewModel, Kotlin Coroutines Flow, JUnit4, kotlinx-coroutines-test, Mockito Kotlin.

---

## Context And Guardrails

- Worktree: `.worktrees/scene-pager-tabs`
- Spec: `docs/superpowers/specs/2026-05-20-scene-pager-tabs-design.md`
- Current replaced spec: `docs/superpowers/specs/2026-05-15-enable-horizontal-swipe-design.md`
- Required docs already read for this work: `docs/dev-harness/ui/rules.md`, `docs/dev-harness/runtime/rules.md`, `docs/dev-harness/test/rules.md`
- Compose Pager reference: `HorizontalPager` lazily places equal-size pages, accepts stable page `key`, and `PagerState.settledPage` only updates after gesture or animation settles.
- Test rules that matter here: ViewModel unit tests use `runTest { advanceUntilIdle() }`, stale async responses must carry generation or request identity, and fixture constructor changes must be reflected in tests.

## File Map

### Core Pager UI

- Create: `core/src/main/java/com/hank/musicfree/core/ui/ScenePagerTabs.kt`
  - Owns reusable Pager + TabRow synchronization.
  - Contains `ScenePagerPage<K>`, `MusicFreeScenePagerTabs(...)`, and pure helper `resolveScenePagerSelectedIndex(...)`.
- Create: `core/src/test/java/com/hank/musicfree/core/ui/ScenePagerTabsTest.kt`
  - Covers selected-key-to-index fallback and duplicate-free page selection logic.
- Modify: `gradle/libs.versions.toml`
  - Add `androidx-compose-foundation` alias so `:core` directly declares the Pager dependency it imports.
- Modify: `core/build.gradle.kts`
  - Add `implementation(libs.androidx.compose.foundation)`.

### Home: 推荐歌单

- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsUiState.kt`
  - Add `RecommendSheetsPagerUiState`.
  - Rename the existing state role to `RecommendSheetsSceneState` while preserving field semantics.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsViewModel.kt`
  - Replace single `_uiState` with `_pagerUiState`.
  - Keep derived compatibility flows `selectedPlugin` and `uiState` for narrower call sites and tests.
  - Add `ensureSceneLoaded(platform)`, `selectPlugin(platform)`, `selectTag(platform, tagId)`, `refresh(platform)`, `loadMore(platform)`.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`
  - Render plugin pages through `MusicFreeScenePagerTabs`.
  - Move tags and grid/loading/error/empty rendering into per-platform scene content.
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsViewModelTest.kt`
  - Update existing expectations to read the selected scene.
  - Add scene independence and duplicate-load tests.

### Home: 榜单

- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListUiState.kt`
  - Add `TopListPagerUiState`.
  - Keep `TopListUiState` as per-scene state.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListViewModel.kt`
  - Replace single `_uiState` with `_pagerUiState`.
  - Keep derived compatibility flows `selectedPlugin` and `uiState`.
  - Add `ensureSceneLoaded(platform)`, `selectPlugin(platform)`, `refresh(platform)`.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`
  - Render plugin pages through `MusicFreeScenePagerTabs`.
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/toplist/TopListViewModelTest.kt`
  - Update selected-scene assertions.
  - Add success/error independence and no-repeat-load tests.

### Search Runtime And UI

- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/runtime/SearchSessionStore.kt`
  - Extend `SearchSessionState` with `selectedPlatforms` and `searchablePlugins`.
  - Add `pagerUiState`-ready state transitions for each media type.
  - Add `selectPlatform(mediaType, platform)`, `ensureMediaSearched(mediaType)`, `loadMore(mediaType, platform)`.
  - Keep existing no-arg `loadMore()` and `selectPlatform(platform)` wrappers for compatibility.
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchUiState.kt`
  - Add `SearchResultsPagerUiState`, `SearchMediaSceneState`, and `SearchPluginSceneState`.
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
  - Collect searchable plugin flows for every `SearchMediaType`.
  - Expose `resultsPagerUiState`.
  - Add media/platform-aware select and load-more wrappers for UI scenes.
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
  - Replace result-panel TabRows + `horizontalSwipeNavigation` with nested `MusicFreeScenePagerTabs`.
  - Keep existing result rows, options sheets, add-to-playlist behavior, playback click behavior, and search chrome.
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/runtime/SearchSessionStoreTest.kt`
  - Add per-media selected platform, scene retention, duplicate pagination, and stale query tests.
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`
  - Update expectations to `resultsPagerUiState`.
  - Add selected platform per media type and plugin scene retention tests.
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelRuntimeStoreTest.kt`
  - Update fixture assertions if `SearchSessionState` constructor defaults or restored state shape changes.

### Cleanup

- Delete: `core/src/main/java/com/hank/musicfree/core/ui/HorizontalTabSwipe.kt`
- Delete: `core/src/test/java/com/hank/musicfree/core/ui/HorizontalTabSwipeTest.kt`
- Verify no remaining imports of `horizontalTabSwipe` or `horizontalSwipeNavigation`.

---

### Task 1: Core Scene Pager Tabs Component

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/ui/ScenePagerTabs.kt`
- Create: `core/src/test/java/com/hank/musicfree/core/ui/ScenePagerTabsTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `core/build.gradle.kts`

- [ ] **Step 1: Add the explicit Compose Foundation dependency alias**

Modify `gradle/libs.versions.toml` under the Compose library aliases:

```toml
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
```

Modify `core/build.gradle.kts` in `dependencies` after `implementation(libs.androidx.compose.ui)`:

```kotlin
implementation(libs.androidx.compose.foundation)
```

- [ ] **Step 2: Write the pure selected-index tests**

Create `core/src/test/java/com/hank/musicfree/core/ui/ScenePagerTabsTest.kt`:

```kotlin
package com.hank.musicfree.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScenePagerTabsTest {
    @Test
    fun `selected key resolves to matching page index`() {
        val pages = listOf(
            ScenePagerPage(key = "a", label = "A"),
            ScenePagerPage(key = "b", label = "B"),
            ScenePagerPage(key = "c", label = "C"),
        )

        assertEquals(1, resolveScenePagerSelectedIndex(pages, "b"))
    }

    @Test
    fun `missing selected key falls back to first page`() {
        val pages = listOf(
            ScenePagerPage(key = "a", label = "A"),
            ScenePagerPage(key = "b", label = "B"),
        )

        assertEquals(0, resolveScenePagerSelectedIndex(pages, "missing"))
    }

    @Test
    fun `null selected key falls back to first page`() {
        val pages = listOf(
            ScenePagerPage(key = "a", label = "A"),
            ScenePagerPage(key = "b", label = "B"),
        )

        assertEquals(0, resolveScenePagerSelectedIndex(pages, null))
    }

    @Test
    fun `empty pages resolves to zero`() {
        assertEquals(0, resolveScenePagerSelectedIndex(emptyList<ScenePagerPage<String>>(), "a"))
    }
}
```

- [ ] **Step 3: Run the core test and confirm it fails before implementation**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests "com.hank.musicfree.core.ui.ScenePagerTabsTest" --no-daemon
```

Expected: FAIL with unresolved references `ScenePagerPage` and `resolveScenePagerSelectedIndex`.

- [ ] **Step 4: Add the shared Pager component**

Create `core/src/main/java/com/hank/musicfree/core/ui/ScenePagerTabs.kt`:

```kotlin
package com.hank.musicfree.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ScenePagerPage<K : Any>(
    val key: K,
    val label: String,
)

@Composable
fun <K : Any> MusicFreeScenePagerTabs(
    pages: List<ScenePagerPage<K>>,
    selectedKey: K?,
    onSelectedKeyChange: (K) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 0.dp,
    beyondViewportPageCount: Int = 0,
    tabLabel: @Composable (page: ScenePagerPage<K>, selected: Boolean) -> Unit = { page, _ ->
        Text(page.label)
    },
    pageContent: @Composable ColumnScope.(page: ScenePagerPage<K>) -> Unit,
) {
    if (pages.isEmpty()) return

    val selectedIndex = resolveScenePagerSelectedIndex(pages, selectedKey)
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { pages.size },
    )
    val currentOnSelectedKeyChange by rememberUpdatedState(onSelectedKeyChange)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, pages.size) {
        if (pagerState.currentPage != selectedIndex && selectedIndex in pages.indices) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@collect
                if (page.key != selectedKey) {
                    currentOnSelectedKeyChange(page.key)
                }
            }
    }

    Column(modifier = modifier) {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = edgePadding,
        ) {
            pages.forEachIndexed { index, page ->
                val selected = index == selectedIndex
                Tab(
                    selected = selected,
                    onClick = {
                        currentOnSelectedKeyChange(page.key)
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { tabLabel(page, selected) },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = beyondViewportPageCount,
            key = { index -> pages[index].key },
        ) { index ->
            Column(modifier = Modifier.fillMaxSize()) {
                pageContent(pages[index])
            }
        }
    }
}

internal fun <K : Any> resolveScenePagerSelectedIndex(
    pages: List<ScenePagerPage<K>>,
    selectedKey: K?,
): Int {
    if (pages.isEmpty()) return 0
    return pages.indexOfFirst { it.key == selectedKey }
        .takeIf { it >= 0 }
        ?: 0
}
```

- [ ] **Step 5: Run the core test and compile core**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests "com.hank.musicfree.core.ui.ScenePagerTabsTest" --no-daemon
./gradlew :core:compileDebugKotlin --no-daemon
```

Expected: both PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add gradle/libs.versions.toml core/build.gradle.kts core/src/main/java/com/hank/musicfree/core/ui/ScenePagerTabs.kt core/src/test/java/com/hank/musicfree/core/ui/ScenePagerTabsTest.kt
git commit -m "feat(core): add scene pager tabs"
```

---

### Task 2: 推荐歌单 Scene State And Pager UI

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsUiState.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsViewModelTest.kt`

- [ ] **Step 1: Write failing tests for recommendation scene independence**

First extend the existing `plugin(...)` helper in `RecommendSheetsViewModelTest.kt` with a deterministic `sheets` argument:

```kotlin
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
            PaginationResult(isEnd = true, data = sheets)
        }
    }
    return plugin
}
```

Then append these tests to `RecommendSheetsViewModelTest.kt`:

```kotlin
@Test
fun `switching plugin keeps each recommend scene state independent`() = runTest {
    val first = plugin(
        platform = "first",
        methods = setOf("getRecommendSheetsByTag"),
        sheets = listOf(musicSheet(id = "first-sheet", title = "First Sheet")),
    )
    val second = plugin(
        platform = "second",
        methods = setOf("getRecommendSheetsByTag"),
        sheets = listOf(musicSheet(id = "second-sheet", title = "Second Sheet")),
    )
    enabledPlugins.value = listOf(first, second)

    val viewModel = RecommendSheetsViewModel(pluginManager)
    advanceUntilIdle()

    viewModel.selectPlugin("second")
    advanceUntilIdle()

    val pager = viewModel.pagerUiState.value
    assertEquals("second", pager.selectedPlatform)
    assertEquals(listOf("first-sheet"), pager.scenes.getValue("first").sheets.map { it.id })
    assertEquals(listOf("second-sheet"), pager.scenes.getValue("second").sheets.map { it.id })
}

@Test
fun `ensureSceneLoaded does not duplicate first page request`() = runTest {
    val capable = plugin(
        platform = "capable",
        methods = setOf("getRecommendSheetsByTag"),
        sheets = listOf(musicSheet(id = "first", title = "First")),
    )
    enabledPlugins.value = listOf(capable)

    val viewModel = RecommendSheetsViewModel(pluginManager)
    advanceUntilIdle()

    viewModel.ensureSceneLoaded("capable")
    viewModel.ensureSceneLoaded("capable")
    advanceUntilIdle()

    verify(capable, times(1)).getRecommendSheetsByTag(eq(mapOf("id" to "")), eq(1))
}

@Test
fun `tag switch only resets selected recommend scene`() = runTest {
    val rock = musicSheet(id = "rock", title = "Rock")
    val first = plugin(
        platform = "first",
        methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
        tags = RecommendSheetTagsResult(pinned = listOf(rock), data = emptyList()),
        sheets = listOf(musicSheet(id = "first-default", title = "First Default")),
    )
    val second = plugin(
        platform = "second",
        methods = setOf("getRecommendSheetsByTag"),
        sheets = listOf(musicSheet(id = "second-default", title = "Second Default")),
    )
    enabledPlugins.value = listOf(first, second)

    val viewModel = RecommendSheetsViewModel(pluginManager)
    advanceUntilIdle()
    viewModel.selectPlugin("second")
    advanceUntilIdle()
    viewModel.selectPlugin("first")
    viewModel.selectTag("first", "rock")
    advanceUntilIdle()

    val pager = viewModel.pagerUiState.value
    assertEquals("rock", pager.scenes.getValue("first").selectedTagId)
    assertEquals(listOf("second-default"), pager.scenes.getValue("second").sheets.map { it.id })
}
```

- [ ] **Step 2: Run the recommendation tests and confirm they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.recommendsheets.RecommendSheetsViewModelTest" --no-daemon
```

Expected: FAIL with unresolved references `pagerUiState`, `ensureSceneLoaded`, or `selectTag(platform, tagId)`.

- [ ] **Step 3: Convert recommendation UI state to pager state**

Edit `RecommendSheetsUiState.kt` so it contains:

```kotlin
package com.hank.musicfree.feature.home.recommendsheets

import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiModel
import com.hank.musicfree.plugin.api.MusicSheetItemBase

data class RecommendTag(
    val id: String,
    val title: String,
    val payload: Map<String, Any?>,
)

data class RecommendSheetsPagerUiState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginCapabilityUiModel> = emptyList(),
    val scenes: Map<String, RecommendSheetsSceneState> = emptyMap(),
)

data class RecommendSheetsSceneState(
    val tags: List<RecommendTag> = emptyList(),
    val selectedTagId: String? = null,
    val sheets: List<MusicSheetItemBase> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = true,
    val errorMessage: String? = null,
    val emptyMessage: String? = null,
    val loaded: Boolean = false,
    val firstPageInFlight: Boolean = false,
    val loadingMorePage: Int? = null,
)

typealias RecommendSheetsUiState = RecommendSheetsSceneState
```

The `typealias` keeps old selected-scene call sites compiling while the screen migrates to `pagerUiState`.

- [ ] **Step 4: Refactor `RecommendSheetsViewModel` to scene map**

In `RecommendSheetsViewModel.kt`, replace `_selectedPlugin` and `_uiState` with:

```kotlin
private val _pagerUiState = MutableStateFlow(RecommendSheetsPagerUiState())
val pagerUiState: StateFlow<RecommendSheetsPagerUiState> = _pagerUiState.asStateFlow()

val selectedPlugin: StateFlow<String?> = pagerUiState
    .map { it.selectedPlatform }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

val uiState: StateFlow<RecommendSheetsUiState> = pagerUiState
    .map { pager -> pager.selectedScene() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecommendSheetsUiState(loading = true))
```

Add helpers in the same class:

```kotlin
private fun RecommendSheetsPagerUiState.selectedScene(): RecommendSheetsSceneState =
    selectedPlatform?.let { scenes[it] } ?: RecommendSheetsSceneState(
        loading = false,
        isEnd = true,
        emptyMessage = if (plugins.isEmpty()) "当前没有支持推荐歌单的插件" else null,
    )

private fun updateScene(
    platform: String,
    transform: (RecommendSheetsSceneState) -> RecommendSheetsSceneState,
) {
    _pagerUiState.value = _pagerUiState.value.let { pager ->
        pager.copy(
            scenes = pager.scenes.toMutableMap().apply {
                put(platform, transform(get(platform) ?: RecommendSheetsSceneState()))
            },
        )
    }
}
```

Rewrite plugin collection to preserve scenes:

```kotlin
availablePlugins.collect { plugins ->
    val platforms = plugins.map { it.platform }.toSet()
    _pagerUiState.value = _pagerUiState.value.let { current ->
        val retainedScenes = current.scenes.filterKeys { it in platforms }
        val selected = when {
            plugins.isEmpty() -> null
            current.selectedPlatform in platforms -> current.selectedPlatform
            else -> plugins.first().platform
        }
        current.copy(
            selectedPlatform = selected,
            plugins = plugins,
            scenes = retainedScenes,
        )
    }
    if (plugins.isEmpty()) {
        invalidateLoads()
        page = 0
        _pagerUiState.value = RecommendSheetsPagerUiState()
    } else {
        ensureSceneLoaded(_pagerUiState.value.selectedPlatform ?: plugins.first().platform)
    }
}
```

Expose scene-aware actions:

```kotlin
fun selectPlugin(platform: String) {
    _pagerUiState.value = _pagerUiState.value.copy(selectedPlatform = platform)
    ensureSceneLoaded(platform)
}

fun ensureSceneLoaded(platform: String) {
    val scene = _pagerUiState.value.scenes[platform]
    if (scene?.loaded == true || scene?.firstPageInFlight == true) return
    val generation = nextLoadGeneration()
    updateScene(platform) {
        it.copy(loading = true, firstPageInFlight = true, errorMessage = null, emptyMessage = null)
    }
    viewModelScope.launch {
        loadTagsAndFirstPage(platform, generation)
    }
}

fun selectTag(tagId: String) {
    val platform = _pagerUiState.value.selectedPlatform ?: return
    selectTag(platform, tagId)
}

fun selectTag(platform: String, tagId: String) {
    val scene = _pagerUiState.value.scenes[platform] ?: return
    val tag = scene.tags.firstOrNull { it.id == tagId } ?: return
    if (scene.selectedTagId == tag.id && scene.sheets.isNotEmpty()) return
    val generation = nextLoadGeneration()
    updateScene(platform) {
        it.copy(
            selectedTagId = tag.id,
            sheets = emptyList(),
            page = 0,
            loading = true,
            loadingMore = false,
            isEnd = false,
            errorMessage = null,
            emptyMessage = null,
            loaded = false,
            firstPageInFlight = true,
        )
    }
    viewModelScope.launch {
        loadSheets(platform = platform, tag = tag, reset = true, generation = generation)
    }
}
```

Then update `loadTagsAndFirstPage`, `refresh`, `loadMore`, and `loadSheets` with these concrete rules:

```kotlin
private fun hasPlatform(platform: String): Boolean =
    _pagerUiState.value.plugins.any { it.platform == platform }

private fun currentTag(platform: String): RecommendTag? {
    val scene = _pagerUiState.value.scenes[platform] ?: return null
    return scene.tags.firstOrNull { it.id == scene.selectedTagId }
}
```

- All success and failure writes use `updateScene(platform) { ... }`.
- Page number is read from and written to `RecommendSheetsSceneState.page`.
- First-page success sets `loaded = true`, `firstPageInFlight = false`, `loading = false`, `page = 1`.
- First-page failure sets `loaded = false`, `firstPageInFlight = false`, `loading = false`, `errorMessage = error.message ?: "加载推荐歌单失败"`.
- Load-more success appends to `scene.sheets`, sets `loadingMore = false`, `loadingMorePage = null`, and updates `page`.
- Load-more failure sets `loadingMore = false`, `loadingMorePage = null`, and preserves old `scene.sheets`.
- Before every write, return early unless `isCurrentLoad(platform, generation) && hasPlatform(platform)`.

- [ ] **Step 5: Render recommendation Pager scenes**

In `RecommendSheetsScreen.kt`, replace `PluginCapabilityTabs(...)` and selected-only content with:

```kotlin
val pagerUiState by viewModel.pagerUiState.collectAsStateWithLifecycle()
val pages = pagerUiState.plugins.map { plugin ->
    ScenePagerPage(key = plugin.platform, label = plugin.label)
}

MusicFreeScenePagerTabs(
    pages = pages,
    selectedKey = pagerUiState.selectedPlatform,
    onSelectedKeyChange = viewModel::selectPlugin,
    modifier = Modifier.fillMaxSize(),
    edgePadding = 12.dp,
    beyondViewportPageCount = 1,
    tabLabel = { page, _ ->
        Text(
            text = page.label,
            fontSize = FontSizes.subTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
) { page ->
    val scene = pagerUiState.scenes[page.key] ?: RecommendSheetsSceneState(loading = true)
    LaunchedEffect(page.key) {
        viewModel.ensureSceneLoaded(page.key)
    }
    RecommendSheetsScene(
        platform = page.key,
        scene = scene,
        onSelectTag = { tagId -> viewModel.selectTag(page.key, tagId) },
        onRefresh = { viewModel.refresh(page.key) },
        onLoadMore = { viewModel.loadMore(page.key) },
        onOpenSheetDetail = onOpenSheetDetail,
    )
}
```

Extract the existing tags/grid/loading/error/empty branches into:

```kotlin
@Composable
private fun RecommendSheetsScene(
    platform: String,
    scene: RecommendSheetsSceneState,
    onSelectTag: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenSheetDetail: (pluginPlatform: String, sheet: MusicSheetItemBase) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (scene.tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(scene.tags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = tag.id == scene.selectedTagId,
                        onClick = { onSelectTag(tag.id) },
                        label = {
                            Text(
                                text = tag.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                scene.loading && scene.sheets.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                    }
                }

                !scene.errorMessage.isNullOrBlank() && scene.sheets.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = scene.errorMessage ?: "加载推荐歌单失败",
                            color = MusicFreeTheme.colors.danger,
                            fontSize = FontSizes.content,
                        )
                        TextButton(onClick = onRefresh) {
                            Text("重试", color = MusicFreeTheme.colors.primary)
                        }
                    }
                }

                scene.sheets.isEmpty() && !scene.emptyMessage.isNullOrBlank() -> {
                    EmptyState(scene.emptyMessage ?: "当前没有支持推荐歌单的插件")
                }

                else -> {
                    RecommendSheetsGrid(
                        platform = platform,
                        scene = scene,
                        onLoadMore = onLoadMore,
                        onOpenSheetDetail = onOpenSheetDetail,
                    )
                }
            }
        }
    }
}
```

Add `RecommendSheetsGrid(...)` below `RecommendSheetsScene` by extracting the current `LazyVerticalGrid` body unchanged except for `uiState` renamed to `scene`, the footer button calling `onLoadMore`, and item clicks calling `onOpenSheetDetail(platform, item)`.

- [ ] **Step 6: Run recommendation tests and home compile**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.recommendsheets.RecommendSheetsViewModelTest" --no-daemon
./gradlew :feature:home:compileDebugKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit Task 2**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets feature/home/src/test/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsViewModelTest.kt
git commit -m "feat(home): isolate recommend sheet pager scenes"
```

---

### Task 3: 榜单 Scene State And Pager UI

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListUiState.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/toplist/TopListViewModelTest.kt`

- [ ] **Step 1: Write failing tests for top-list scene independence**

Append tests to `TopListViewModelTest.kt`:

```kotlin
@Test
fun `switching top list plugins keeps loaded scenes independent`() = runTest {
    val firstCapable = plugin("first", setOf("getTopLists"), topLists = listOf(musicGroup("first-group")))
    val secondCapable = plugin("second", setOf("getTopLists"), topLists = listOf(musicGroup("second-group")))
    enabledPlugins.value = listOf(firstCapable, secondCapable)

    val viewModel = TopListViewModel(pluginManager)
    advanceUntilIdle()

    viewModel.selectPlugin("second")
    advanceUntilIdle()

    val pager = viewModel.pagerUiState.value
    assertEquals("second", pager.selectedPlatform)
    assertEquals(
        listOf("first-group"),
        (pager.scenes.getValue("first") as TopListUiState.Success).groups.map { it.title },
    )
    assertEquals(
        listOf("second-group"),
        (pager.scenes.getValue("second") as TopListUiState.Success).groups.map { it.title },
    )
}

@Test
fun `selecting already loaded top list scene does not reload plugin`() = runTest {
    val capable = plugin("capable", setOf("getTopLists"), topLists = listOf(musicGroup("group")))
    enabledPlugins.value = listOf(capable)

    val viewModel = TopListViewModel(pluginManager)
    advanceUntilIdle()

    viewModel.selectPlugin("capable")
    viewModel.ensureSceneLoaded("capable")
    advanceUntilIdle()

    verify(capable, times(1)).getTopLists()
}
```

- [ ] **Step 2: Run top-list tests and confirm they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.toplist.TopListViewModelTest" --no-daemon
```

Expected: FAIL with unresolved references `pagerUiState` or `ensureSceneLoaded`.

- [ ] **Step 3: Add top-list pager UI state**

Edit `TopListUiState.kt`:

```kotlin
package com.hank.musicfree.feature.home.toplist

import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiModel
import com.hank.musicfree.plugin.api.MusicSheetGroupItem

data class TopListPagerUiState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginCapabilityUiModel> = emptyList(),
    val scenes: Map<String, TopListUiState> = emptyMap(),
)

sealed interface TopListUiState {
    data object Idle : TopListUiState
    data object Loading : TopListUiState
    data class Success(val groups: List<MusicSheetGroupItem>) : TopListUiState
    data class Error(val message: String) : TopListUiState
}
```

- [ ] **Step 4: Refactor `TopListViewModel` to scene map**

Replace `_selectedPlugin`, `_uiState`, and `selectedPluginInstance` with scene-aware state:

```kotlin
private val _pagerUiState = MutableStateFlow(TopListPagerUiState())
val pagerUiState: StateFlow<TopListPagerUiState> = _pagerUiState.asStateFlow()

val selectedPlugin: StateFlow<String?> = pagerUiState
    .map { it.selectedPlatform }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

val uiState: StateFlow<TopListUiState> = pagerUiState
    .map { pager -> pager.selectedPlatform?.let { pager.scenes[it] } ?: TopListUiState.Idle }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TopListUiState.Idle)

private val loadedPluginInstances = mutableMapOf<String, LoadedPlugin>()
private val inFlightPlatforms = mutableSetOf<String>()
```

Expose:

```kotlin
fun selectPlugin(platform: String) {
    _pagerUiState.value = _pagerUiState.value.copy(selectedPlatform = platform)
    ensureSceneLoaded(platform)
}

fun ensureSceneLoaded(platform: String) {
    val scene = _pagerUiState.value.scenes[platform]
    if (scene is TopListUiState.Success || platform in inFlightPlatforms) return
    val plugin = capablePlugins.value.firstOrNull { it.info.platform.trim() == platform }
        ?: pluginManager.getPlugin(platform)?.takeIf { it.supportsTopLists() }
        ?: run {
            updateScene(platform, TopListUiState.Error("插件不存在：$platform"))
            return
        }
    loadTopLists(platform, plugin, nextLoadGeneration())
}
```

Update plugin collection:

```kotlin
capablePlugins.collect { plugins ->
    val models = plugins.pluginsSupporting("getTopLists")
    val platforms = models.map { it.platform }.toSet()
    _pagerUiState.value = _pagerUiState.value.let { current ->
        val selected = when {
            models.isEmpty() -> null
            current.selectedPlatform in platforms -> current.selectedPlatform
            else -> models.first().platform
        }
        current.copy(
            selectedPlatform = selected,
            plugins = models,
            scenes = current.scenes.filterKeys { it in platforms },
        )
    }
    loadedPluginInstances.keys.retainAll(platforms)
    if (models.isEmpty()) {
        invalidateLoads()
        _pagerUiState.value = TopListPagerUiState(
            scenes = emptyMap(),
        )
    } else {
        ensureSceneLoaded(_pagerUiState.value.selectedPlatform ?: models.first().platform)
    }
}
```

Update `loadTopLists` so success and failure write only `updateScene(platform, ...)`. Add this helper and use it before applying any async result:

```kotlin
private fun isCurrentLoad(platform: String, plugin: LoadedPlugin, generation: Long): Boolean =
    loadGeneration == generation &&
        loadedPluginInstances[platform] === plugin &&
        _pagerUiState.value.plugins.any { it.platform == platform }
```

Success writes `TopListUiState.Success(groups)` and failure writes `TopListUiState.Error(e.message ?: "加载榜单失败")`; both paths remove `platform` from `inFlightPlatforms` in `finally`.

- [ ] **Step 5: Render top-list Pager scenes**

In `TopListScreen.kt`, replace `PluginCapabilityTabs(...)` and selected-only `Box(...)` with:

```kotlin
val pagerUiState by viewModel.pagerUiState.collectAsStateWithLifecycle()
val pages = pagerUiState.plugins.map { plugin ->
    ScenePagerPage(key = plugin.platform, label = plugin.label)
}

MusicFreeScenePagerTabs(
    pages = pages,
    selectedKey = pagerUiState.selectedPlatform,
    onSelectedKeyChange = viewModel::selectPlugin,
    modifier = Modifier.fillMaxSize(),
    edgePadding = 12.dp,
    beyondViewportPageCount = 1,
    tabLabel = { page, _ ->
        Text(
            text = page.label,
            fontSize = FontSizes.subTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
) { page ->
    val state = pagerUiState.scenes[page.key] ?: TopListUiState.Loading
    LaunchedEffect(page.key) {
        viewModel.ensureSceneLoaded(page.key)
    }
    TopListScene(
        pluginPlatform = page.key,
        state = state,
        onRefresh = { viewModel.refresh(page.key) },
        onOpenTopListDetail = onOpenTopListDetail,
    )
}
```

Extract the existing `when (val state = uiState)` body into:

```kotlin
@Composable
private fun TopListScene(
    pluginPlatform: String,
    state: TopListUiState,
    onRefresh: () -> Unit,
    onOpenTopListDetail: (pluginPlatform: String, topList: MusicSheetItemBase) -> Unit,
) {
    when (state) {
        is TopListUiState.Idle,
        is TopListUiState.Loading,
        -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
            }
        }

        is TopListUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = rpx(24)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.message,
                    color = MusicFreeTheme.colors.danger,
                    fontSize = FontSizes.content,
                )
                TextButton(onClick = onRefresh) {
                    Text("重试", color = MusicFreeTheme.colors.primary)
                }
            }
        }

        is TopListUiState.Success -> {
            if (state.groups.isEmpty()) {
                EmptyState("当前插件不支持榜单")
            } else {
                TopListGroups(
                    pluginPlatform = pluginPlatform,
                    groups = state.groups,
                    onOpenTopListDetail = onOpenTopListDetail,
                )
            }
        }
    }
}
```

- [ ] **Step 6: Run top-list tests and home compile**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.toplist.TopListViewModelTest" --no-daemon
./gradlew :feature:home:compileDebugKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit Task 3**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/toplist feature/home/src/test/java/com/hank/musicfree/feature/home/toplist/TopListViewModelTest.kt
git commit -m "feat(home): isolate top list pager scenes"
```

---

### Task 4: Search Session Scene State

**Files:**
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchUiState.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/runtime/SearchSessionStore.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/runtime/SearchSessionStoreTest.kt`
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelRuntimeStoreTest.kt`

- [ ] **Step 1: Write failing SearchSessionStore tests**

Append to `SearchSessionStoreTest.kt`:

```kotlin
@Test
fun selectedPlatformIsIndependentPerMediaType() = runTest {
    val store = searchSessionStore()
    store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("music-a"), plugin("music-b")))
    store.setSearchablePlugins(SearchMediaType.ALBUM, listOf(plugin("album-a"), plugin("album-b")))

    store.selectPlatform(SearchMediaType.MUSIC, "music-b")
    store.selectMediaType(SearchMediaType.ALBUM)
    store.selectPlatform(SearchMediaType.ALBUM, "album-b")
    store.selectMediaType(SearchMediaType.MUSIC)

    assertEquals("music-b", store.state.value.selectedPlatforms[SearchMediaType.MUSIC])
    assertEquals("album-b", store.state.value.selectedPlatforms[SearchMediaType.ALBUM])
    assertEquals("music-b", store.state.value.selectedPlatform)
}

@Test
fun ensureMediaSearchedKeepsMediaResultsIndependent() = runTest {
    val gateway = StaticPerTypeSearchGateway()
    val store = searchSessionStore(gateway = gateway)
    store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("demo")))
    store.setSearchablePlugins(SearchMediaType.ALBUM, listOf(plugin("demo")))

    store.search("hello")
    store.ensureMediaSearched(SearchMediaType.ALBUM)

    val music = store.state.value.results.getValue(SearchMediaType.MUSIC).getValue("demo")
        as PluginSearchState.Success
    val album = store.state.value.results.getValue(SearchMediaType.ALBUM).getValue("demo")
        as PluginSearchState.Success

    assertTrue(music.items.single() is PluginSearchItem.Music)
    assertTrue(album.items.single() is PluginSearchItem.Album)
}

@Test
fun loadMoreOnlyUpdatesRequestedMediaAndPlatformScene() = runTest {
    val gateway = PagingSearchGateway()
    val store = searchSessionStore(gateway = gateway)
    store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("first"), plugin("second")))

    store.search("hello")
    store.loadMore(SearchMediaType.MUSIC, "first")

    val first = store.state.value.results.getValue(SearchMediaType.MUSIC).getValue("first")
        as PluginSearchState.Success
    val second = store.state.value.results.getValue(SearchMediaType.MUSIC).getValue("second")
        as PluginSearchState.Success

    assertEquals(2, first.items.size)
    assertEquals(1, second.items.size)
}
```

Add local test gateways in the same test file:

```kotlin
private class StaticPerTypeSearchGateway : SearchSessionGateway {
    override suspend fun search(
        platform: String,
        query: String,
        page: Int,
        mediaType: SearchMediaType,
    ): SearchResult = when (mediaType) {
        SearchMediaType.MUSIC -> SearchResult(
            isEnd = true,
            data = listOf(PluginSearchItem.Music(music("music-$platform"))),
        )
        SearchMediaType.ALBUM -> SearchResult(
            isEnd = true,
            data = listOf(PluginSearchItem.Album(album("album-$platform"))),
        )
        else -> SearchResult(isEnd = true, data = emptyList())
    }
}

private class PagingSearchGateway : SearchSessionGateway {
    override suspend fun search(
        platform: String,
        query: String,
        page: Int,
        mediaType: SearchMediaType,
    ): SearchResult = SearchResult(
        isEnd = page >= 2,
        data = listOf(PluginSearchItem.Music(music("$platform-$page"))),
    )
}
```

Use the existing `music(...)` helper and add this `album(...)` helper:

```kotlin
private fun album(id: String) = com.hank.musicfree.plugin.api.AlbumItemBase(
    id = id,
    platform = "demo",
    title = id,
    artist = null,
    artwork = null,
    raw = emptyMap(),
)
```

- [ ] **Step 2: Run SearchSessionStore tests and confirm they fail**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.runtime.SearchSessionStoreTest" --no-daemon
```

Expected: FAIL with unresolved references `selectedPlatforms`, `ensureMediaSearched`, and `loadMore(mediaType, platform)`.

- [ ] **Step 3: Add search pager UI state types**

Edit `SearchUiState.kt`:

```kotlin
data class SearchResultsPagerUiState(
    val selectedMediaType: SearchMediaType = SearchMediaType.MUSIC,
    val mediaScenes: Map<SearchMediaType, SearchMediaSceneState> = emptyMap(),
)

data class SearchMediaSceneState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginInfo> = emptyList(),
    val pluginScenes: Map<String, PluginSearchState> = emptyMap(),
)
```

Add import:

```kotlin
import com.hank.musicfree.plugin.api.PluginInfo
```

- [ ] **Step 4: Extend `SearchSessionState`**

In `SearchSessionStore.kt`, change `SearchSessionState` to include plugin and selected-platform maps while preserving compatibility:

```kotlin
data class SearchSessionState(
    val query: String = "",
    val selectedMediaType: SearchMediaType = SearchMediaType.MUSIC,
    val selectedPlatforms: Map<SearchMediaType, String?> = emptyMap(),
    val selectedPlatform: String? = selectedPlatforms[selectedMediaType],
    val searchablePlugins: Map<SearchMediaType, List<PluginInfo>> = emptyMap(),
    val generation: Long = 0L,
    val results: Map<SearchMediaType, Map<String, PluginSearchState>> = emptyMap(),
    val pageStatus: SearchPageStatus = SearchPageStatus.EDITING,
    val restoreReason: String? = null,
)
```

Add:

```kotlin
val SearchSessionState.resultsPagerUiState: SearchResultsPagerUiState
    get() = SearchResultsPagerUiState(
        selectedMediaType = selectedMediaType,
        mediaScenes = SearchMediaType.entries.associateWith { mediaType ->
            SearchMediaSceneState(
                selectedPlatform = selectedPlatforms[mediaType],
                plugins = searchablePlugins[mediaType].orEmpty(),
                pluginScenes = results[mediaType].orEmpty(),
            )
        },
    )
```

- [ ] **Step 5: Update searchable plugin and selection actions**

In `setSearchablePlugins`, write plugins and selected platform per media type:

```kotlin
suspend fun setSearchablePlugins(mediaType: SearchMediaType, plugins: List<PluginInfo>) {
    searchablePlugins[mediaType] = plugins
    _state.update { current ->
        val currentSelected = current.selectedPlatforms[mediaType]
        val nextSelected = when {
            currentSelected == null && plugins.isNotEmpty() -> plugins.first().platform
            currentSelected != null && plugins.none { it.platform == currentSelected } -> plugins.firstOrNull()?.platform
            else -> currentSelected
        }
        val nextSelectedPlatforms = current.selectedPlatforms.toMutableMap().apply {
            put(mediaType, nextSelected)
        }
        val nextSearchablePlugins = current.searchablePlugins.toMutableMap().apply {
            put(mediaType, plugins)
        }
        current.copy(
            searchablePlugins = nextSearchablePlugins,
            selectedPlatforms = nextSelectedPlatforms,
            selectedPlatform = nextSelectedPlatforms[current.selectedMediaType],
        )
    }
    updatePageStatusForPluginAvailability()
    runPendingSearchIfPossible()
}
```

Update `selectMediaType`:

```kotlin
fun selectMediaType(type: SearchMediaType) {
    val current = _state.value
    if (current.selectedMediaType == type) return
    pendingSearch = if (current.query.isNotBlank() && current.results[type] == null) {
        PendingSearch(current.query, type)
    } else {
        null
    }
    _state.update {
        it.copy(
            selectedMediaType = type,
            selectedPlatform = it.selectedPlatforms[type],
            pageStatus = if (pendingSearch != null) SearchPageStatus.SEARCHING else it.pageStatus,
        )
    }
}
```

Add media-aware platform selection:

```kotlin
fun selectPlatform(mediaType: SearchMediaType, platform: String) {
    _state.update { current ->
        val nextSelectedPlatforms = current.selectedPlatforms.toMutableMap().apply {
            put(mediaType, platform)
        }
        current.copy(
            selectedPlatforms = nextSelectedPlatforms,
            selectedPlatform = nextSelectedPlatforms[current.selectedMediaType],
        )
    }
}

fun selectPlatform(platform: String) {
    selectPlatform(_state.value.selectedMediaType, platform)
}
```

- [ ] **Step 6: Add scene-aware search and pagination**

Change `search(query)` to clear old query results:

```kotlin
_state.update {
    it.copy(
        query = query,
        generation = generation,
        results = emptyMap(),
        pageStatus = SearchPageStatus.SEARCHING,
        restoreReason = null,
    )
}
ensureMediaSearched(mediaType)
```

Add:

```kotlin
suspend fun ensureMediaSearched(mediaType: SearchMediaType) {
    val current = _state.value
    if (current.query.isBlank()) return
    if (current.results[mediaType]?.isNotEmpty() == true) return
    searchForMediaType(current.query, mediaType, current.generation)
}

suspend fun loadMore(mediaType: SearchMediaType, platform: String) {
    val currentState = _state.value
    val current = currentState.results[mediaType]?.get(platform)
    if (current !is PluginSearchState.Success || current.isEnd) return
    val request = SearchRequest(
        generation = currentState.generation,
        query = currentState.query,
        mediaType = mediaType,
        platform = platform,
        page = current.page + 1,
    )
    if (!loadMoreInFlight.add(request)) return

    val startedAt = System.nanoTime()
    try {
        val (result, durationMs) = timedSuspend {
            gateway.search(platform, request.query, request.page, mediaType)
        }
        val latestState = _state.value
        val latest = latestState.results[mediaType]?.get(platform)
        if (
            latestState.generation != request.generation ||
            latestState.query != request.query ||
            latest !is PluginSearchState.Success ||
            latest.page != current.page
        ) {
            logStaleResult(request, elapsedMs(startedAt))
            return
        }
        updatePluginState(
            mediaType,
            platform,
            latest.copy(
                items = latest.items + result.data,
                isEnd = result.isEnd,
                page = request.page,
            ),
        )
        MfLog.detail(
            category = LogCategory.SEARCH,
            event = "search_session_page_success",
            fields = logFields(
                key = snapshotKey(_state.value),
                operation = "search_session_load_more",
                state = _state.value,
                platform = platform,
                page = request.page,
                count = result.data.size,
                result = LogFields.Result.SUCCESS,
                reason = null,
                durationMs = durationMs,
            ),
        )
        persist()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        MfLog.error(
            category = LogCategory.SEARCH,
            event = "search_session_page_failed",
            throwable = error,
            fields = logFields(
                key = snapshotKey(_state.value),
                operation = "search_session_load_more",
                state = _state.value,
                platform = platform,
                page = request.page,
                count = 0,
                result = LogFields.Result.FAILURE,
                reason = "search_failed",
                durationMs = elapsedMs(startedAt),
            ),
        )
    } finally {
        loadMoreInFlight.remove(request)
    }
}

suspend fun loadMore() {
    val current = _state.value
    val platform = current.selectedPlatform ?: return
    loadMore(current.selectedMediaType, platform)
}
```

When moving the existing `loadMore()` body, keep:

- `loadMoreInFlight.add(request)` guard.
- generation/query/latest state stale checks.
- `search_session_page_success` and `search_session_page_failed` logs.
- `persist()` after a successful append.

- [ ] **Step 7: Update `SearchViewModel` to expose all media scenes**

In `SearchViewModel.kt`, replace the selected-only `flatMapLatest` collection with one collector per media type:

```kotlin
SearchMediaType.entries.forEach { mediaType ->
    viewModelScope.launch {
        pluginManager.getSearchablePlugins(mediaType.key)
            .map { plugins -> mediaType to plugins.map { it.info } }
            .collect { (type, plugins) ->
                handleSearchablePluginsChanged(type, plugins)
            }
    }
}
```

Expose:

```kotlin
val resultsPagerUiState: StateFlow<SearchResultsPagerUiState> = searchSessionStore.state
    .map { it.resultsPagerUiState }
    .stateIn(viewModelScope, SharingStarted.Eagerly, searchSessionStore.state.value.resultsPagerUiState)
```

Update selected-only compatibility:

```kotlin
val searchablePlugins: StateFlow<List<PluginInfo>> = resultsPagerUiState
    .map { pager -> pager.mediaScenes[pager.selectedMediaType]?.plugins.orEmpty() }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

val selectedPlatform: StateFlow<String?> = resultsPagerUiState
    .map { pager -> pager.mediaScenes[pager.selectedMediaType]?.selectedPlatform }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)
```

Add:

```kotlin
fun selectMediaType(type: SearchMediaType) {
    searchSessionStore.selectMediaType(type)
    viewModelScope.launch {
        searchSessionStore.ensureMediaSearched(type)
    }
}

fun selectPlatform(mediaType: SearchMediaType, platform: String) {
    searchSessionStore.selectPlatform(mediaType, platform)
}

fun loadMore(mediaType: SearchMediaType, platform: String) {
    viewModelScope.launch {
        searchSessionStore.loadMore(mediaType, platform)
    }
}
```

Keep the existing `selectPlatform(platform)` and `loadMore()` wrappers for old call sites.

- [ ] **Step 8: Run search runtime tests**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.runtime.SearchSessionStoreTest" --no-daemon
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelTest" --no-daemon
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelRuntimeStoreTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 9: Commit Task 4**

```bash
git add feature/search/src/main/java/com/hank/musicfree/feature/search/SearchUiState.kt feature/search/src/main/java/com/hank/musicfree/feature/search/runtime/SearchSessionStore.kt feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt feature/search/src/test/java/com/hank/musicfree/feature/search
git commit -m "feat(search): isolate search pager scene state"
```

---

### Task 5: Search Result Nested Pager UI

**Files:**
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`

- [ ] **Step 1: Replace the media TabRow with `MusicFreeScenePagerTabs`**

In `SearchResultPanel`, collect `resultsPagerUiState` instead of separate selected media/platform state. Build media pages:

```kotlin
val mediaPages = SearchMediaType.entries.map { mediaType ->
    ScenePagerPage(key = mediaType, label = mediaType.label)
}
```

Render:

```kotlin
MusicFreeScenePagerTabs(
    pages = mediaPages,
    selectedKey = pagerUiState.selectedMediaType,
    onSelectedKeyChange = onSelectMediaType,
    modifier = Modifier.fillMaxSize(),
    edgePadding = 0.dp,
    beyondViewportPageCount = 1,
    tabLabel = { page, _ ->
        Text(page.label, fontSize = FontSizes.subTitle)
    },
) { mediaPage ->
    val mediaScene = pagerUiState.mediaScenes[mediaPage.key]
        ?: SearchMediaSceneState()
    SearchPluginPagerScene(
        mediaType = mediaPage.key,
        scene = mediaScene,
        onSelectPlatform = onSelectPlatform,
        onLoadMore = onLoadMore,
        // pass through existing row click, more-menu, favorite, add-to-playlist, download callbacks
    )
}
```

- [ ] **Step 2: Add a plugin Pager scene composable**

In the same file, extract plugin tab and result content into:

```kotlin
@Composable
private fun SearchPluginPagerScene(
    mediaType: SearchMediaType,
    scene: SearchMediaSceneState,
    onSelectPlatform: (SearchMediaType, String) -> Unit,
    onLoadMore: (SearchMediaType, String) -> Unit,
    // keep the existing callbacks currently used by result rows
) {
    if (scene.plugins.isEmpty()) {
        EmptySearchResult()
        return
    }

    val pluginPages = scene.plugins.map { plugin ->
        ScenePagerPage(key = plugin.platform, label = plugin.platform)
    }

    MusicFreeScenePagerTabs(
        pages = pluginPages,
        selectedKey = scene.selectedPlatform,
        onSelectedKeyChange = { platform -> onSelectPlatform(mediaType, platform) },
        modifier = Modifier.fillMaxSize(),
        edgePadding = 0.dp,
        beyondViewportPageCount = 1,
        tabLabel = { page, _ ->
            Text(
                text = page.label,
                fontSize = FontSizes.subTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) { pluginPage ->
        val pluginState = scene.pluginScenes[pluginPage.key] ?: PluginSearchState.Idle
        SearchPluginResultScene(
            mediaType = mediaType,
            platform = pluginPage.key,
            state = pluginState,
            onLoadMore = { onLoadMore(mediaType, pluginPage.key) },
            // pass through existing row callbacks
        )
    }
}
```

Use the existing empty-state composable already present in `SearchScreen.kt`; do not introduce a second visual empty state.

- [ ] **Step 3: Move existing result-state rendering into `SearchPluginResultScene`**

Create:

```kotlin
@Composable
private fun SearchPluginResultScene(
    mediaType: SearchMediaType,
    platform: String,
    state: PluginSearchState,
    onLoadMore: () -> Unit,
    // existing callbacks
) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            is PluginSearchState.Idle -> Unit
            is PluginSearchState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                }
            }
            is PluginSearchState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.message,
                        color = MusicFreeTheme.colors.danger,
                        fontSize = FontSizes.content,
                    )
                }
            }
            is PluginSearchState.Success -> {
                SearchSuccessResultList(
                    mediaType = mediaType,
                    platform = platform,
                    state = state,
                    onLoadMore = onLoadMore,
                    onPlayItem = onPlayItem,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite,
                    isFavoriteFlow = isFavoriteFlow,
                    onDownload = onDownload,
                )
            }
        }
    }
}
```

Add `SearchSuccessResultList(...)` by extracting the current success branch list code from `SearchResultPanel`. Its footer calls `onLoadMore()`, and all row callbacks must be the same callbacks currently passed to the success branch.

- [ ] **Step 4: Remove `horizontalSwipeNavigation` imports and calls from search**

Delete imports:

```kotlin
import com.hank.musicfree.core.ui.HorizontalSwipeDirection
import com.hank.musicfree.core.ui.horizontalSwipeNavigation
```

The result panel should no longer contain:

```kotlin
.horizontalSwipeNavigation { ... }
```

- [ ] **Step 5: Compile search**

Run:

```bash
./gradlew :feature:search:compileDebugKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit Task 5**

```bash
git add feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt
git commit -m "feat(search): render results with nested scene pagers"
```

---

### Task 6: Cleanup, Verification, And Final Review

**Files:**
- Delete: `core/src/main/java/com/hank/musicfree/core/ui/HorizontalTabSwipe.kt`
- Delete: `core/src/test/java/com/hank/musicfree/core/ui/HorizontalTabSwipeTest.kt`
- Modify imports in `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`, `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`, and `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt` if old helper references remain.

- [ ] **Step 1: Remove old lightweight swipe helper**

Run:

```bash
rg -n "horizontalTabSwipe|horizontalSwipeNavigation|HorizontalSwipeDirection" core feature app -g "*.kt"
```

Expected before cleanup: only `HorizontalTabSwipe.kt`, `HorizontalTabSwipeTest.kt`, or stale imports appear.

Delete:

```bash
git rm core/src/main/java/com/hank/musicfree/core/ui/HorizontalTabSwipe.kt core/src/test/java/com/hank/musicfree/core/ui/HorizontalTabSwipeTest.kt
```

Run the `rg` command again.

Expected after cleanup: no matches, except unrelated text in docs if the search includes docs.

- [ ] **Step 2: Run targeted unit tests**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests "com.hank.musicfree.core.ui.ScenePagerTabsTest" --no-daemon
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.recommendsheets.RecommendSheetsViewModelTest" --no-daemon
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.toplist.TopListViewModelTest" --no-daemon
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.runtime.SearchSessionStoreTest" --no-daemon
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelTest" --no-daemon
```

Expected: all PASS.

- [ ] **Step 3: Run module compile gates**

Run:

```bash
./gradlew :core:compileDebugKotlin --no-daemon
./gradlew :feature:home:compileDebugKotlin --no-daemon
./gradlew :feature:search:compileDebugKotlin --no-daemon
```

Expected: all PASS.

- [ ] **Step 4: Run harness and diff checks**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
bash scripts/dev-harness/check.sh
git diff --check
```

Expected: all PASS.

- [ ] **Step 5: Run Debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Optional emulator smoke if a device is available**

Check:

```bash
adb devices
```

If an emulator/device is listed, install the Debug APK and manually verify:

- 搜索结果一级媒体 Tab 横滑时内容跟手移动，settle 后选中态同步。
- 搜索结果二级插件 Tab 横滑时插件结果 scene 保留。
- 推荐歌单插件 Tab 横滑时每个插件 tags、列表、分页状态独立保留。
- 榜单插件 Tab 横滑时每个插件 groups/error/loading 独立保留。
- 竖向滚动、推荐歌单 tag 横向滚动、TabRow 横向滚动正常。

If no device is available, record this as a runtime validation gap in the final report.

- [ ] **Step 7: Final code review pass**

Review these risks before declaring done:

- Pager `LaunchedEffect` does not create an infinite select/animate loop.
- `HorizontalPager` nested in search does not prevent vertical result list scrolling.
- Recommendation and top-list old async responses cannot mutate the wrong scene.
- Search `selectedPlatforms` map preserves one selected plugin per media type.
- New query clears old scene results and stale old-query responses are dropped.
- No new direct `android.util.Log.*` calls.
- No raw `TopAppBarDefaults.topAppBarColors(...)` added outside the core screen chrome file.

- [ ] **Step 8: Commit cleanup and verification-ready state**

```bash
git add -A
git commit -m "refactor(pager): remove lightweight tab swipe helper"
```

If there are no cleanup changes because the helper is intentionally kept for another caller, do not create an empty commit. Instead, note the reason in the final implementation report.

---

## Completion Criteria

- `MusicFreeScenePagerTabs` exists in `:core` and is used by search, recommend sheets, and top list.
- `horizontalTabSwipe` / `horizontalSwipeNavigation` no longer drive the target pages.
- 推荐歌单每个插件 scene 独立保存 tags、selectedTag、sheets、page、loading、loadingMore、error、isEnd。
- 榜单每个插件 scene 独立保存 loading/error/success groups。
- 搜索每个 media type 独立保存 selected platform；每个 media + plugin 独立保存 result state and pagination.
- Pager preloading does not duplicate first-page or load-more requests.
- Targeted tests, module compile gates, dev harness, diff check, and `:app:assembleDebug` pass.
