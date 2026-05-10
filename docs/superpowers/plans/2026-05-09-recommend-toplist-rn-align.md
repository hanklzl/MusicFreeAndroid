# Recommend And Toplist RN Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the recommended playlist and chart entry pages with the main RN behavior, and fix detail navigation by carrying complete `MusicSheetItemBase` seed objects.

**Architecture:** Keep changes scoped to `:feature:home`, route data classes, and app navigation. Use a short-lived in-memory seed store for complete plugin sheet/top-list objects, while keeping routes serializable with lightweight fallback fields. Add small pure helpers for filtering, fallback seed construction, and display text so the risky behavior can be unit-tested without large Compose or Hilt harnesses.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Navigation Compose serializable routes, Kotlin Coroutines Flow, Mockito-Kotlin, JUnit4, Gradle.

---

## Scope Check

The approved spec is a single coherent feature: recommended playlist and chart entry pages share the same plugin capability filtering and sheet seed navigation problem. It does not need to be split into separate implementation plans.

## File Structure

- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
  - Add lightweight fallback fields and `seedToken` to `TopListDetailRoute` and `PluginSheetDetailRoute`.
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
  - Store clicked `MusicSheetItemBase` seeds before navigating.
  - Pass route fallback fields for process recreation.
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStore.kt`
  - One-time in-memory storage for clicked sheet/top-list seed objects.
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeed.kt`
  - Fallback seed constructors for `PluginSheetDetailRoute` and `TopListDetailRoute`.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
  - Use seed store or route fallback before calling `getMusicSheetInfo`.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailViewModel.kt`
  - Use seed store or route fallback before calling `getTopListDetail`; remove the normal-path dependency on `findTopListById`.
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUi.kt`
  - Small UI model plus filtering helpers for enabled plugins with a required method.
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityTabs.kt`
  - Shared `ScrollableTabRow` UI used by recommended playlists and charts.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsUiState.kt`
  - Add empty-state metadata needed to distinguish no plugins from unsupported plugins.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModel.kt`
  - Source plugins from sorted enabled plugins filtered by `getRecommendSheetsByTag`.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt`
  - Replace segmented buttons and single-column rows with shared tabs and a three-column card grid.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModel.kt`
  - Source plugins from sorted enabled plugins filtered by `getTopLists`.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt`
  - Replace segmented buttons with shared tabs, pass complete top-list item on click, and use `description` for subtitle.
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListNavigation.kt`
  - Update callback signature to accept complete `MusicSheetItemBase`.
- Modify: `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
  - Assert new route fields round-trip through serialization.
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStoreTest.kt`
  - Test seed store one-time consumption.
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeedTest.kt`
  - Test route fallback seed construction.
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUiTest.kt`
  - Test method filtering and ordering.
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDisplayTextTest.kt`
  - Test chart subtitle selection.
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModelTest.kt`
  - Test recommended playlist capability filtering and tag payload calls.
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModelTest.kt`
  - Test chart capability filtering and load calls.

## Task 1: Route Fields, Seed Store, And Fallback Seeds

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStore.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeed.kt`
- Test: `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStoreTest.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeedTest.kt`

- [ ] **Step 1: Update route serialization tests first**

Replace the existing `TopListDetailRoute is serializable` and `PluginSheetDetailRoute is serializable` tests in `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt` with:

```kotlin
    @Test
    fun `TopListDetailRoute is serializable`() {
        val route = TopListDetailRoute(
            pluginPlatform = "demo",
            topListId = "sheet-1",
            title = "飙升榜",
            artist = "官方",
            description = "每日更新",
            coverImg = "https://example.com/top.jpg",
            artwork = "https://example.com/top-art.jpg",
            worksNum = 100,
            seedToken = "seed-1",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<TopListDetailRoute>(json)
        assertEquals(route, decoded)
    }

    @Test
    fun `PluginSheetDetailRoute is serializable`() {
        val route = PluginSheetDetailRoute(
            pluginPlatform = "demo",
            sheetId = "sheet-9",
            title = "热门推荐",
            artist = "编辑精选",
            description = "适合通勤",
            coverImg = "https://example.com/sheet.jpg",
            artwork = "https://example.com/sheet-art.jpg",
            worksNum = 42,
            seedToken = "seed-9",
        )
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)
        val decoded = Json.decodeFromString<PluginSheetDetailRoute>(json)
        assertEquals(route, decoded)
    }
```

- [ ] **Step 2: Add seed store tests**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStoreTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginSheetSeedStoreTest {

    @After
    fun tearDown() {
        PluginSheetSeedStore.clear()
    }

    @Test
    fun `take returns stored seed once`() {
        val seed = musicSheet("sheet-1")

        val token = PluginSheetSeedStore.put(seed)

        assertEquals(seed, PluginSheetSeedStore.take(token))
        assertNull(PluginSheetSeedStore.take(token))
    }

    @Test
    fun `take returns null for blank or unknown token`() {
        assertNull(PluginSheetSeedStore.take(null))
        assertNull(PluginSheetSeedStore.take(""))
        assertNull(PluginSheetSeedStore.take("missing"))
    }

    private fun musicSheet(id: String): MusicSheetItemBase = MusicSheetItemBase(
        id = id,
        platform = "demo",
        title = "Title $id",
        artist = "Artist",
        description = "Description",
        coverImg = "https://example.com/$id.jpg",
        artwork = "https://example.com/$id-art.jpg",
        worksNum = 12,
        raw = mapOf("id" to id, "custom" to "kept"),
    )
}
```

- [ ] **Step 3: Add fallback seed tests**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeedTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginSheetRouteSeedTest {

    @Test
    fun `plugin sheet route fallback keeps lightweight fields`() {
        val seed = PluginSheetDetailRoute(
            pluginPlatform = "demo",
            sheetId = "sheet-1",
            title = "推荐歌单",
            artist = "编辑",
            description = "描述",
            coverImg = "https://example.com/cover.jpg",
            artwork = "https://example.com/art.jpg",
            worksNum = 30,
            seedToken = null,
        ).fallbackSheetSeed()

        assertEquals("sheet-1", seed.id)
        assertEquals("demo", seed.platform)
        assertEquals("推荐歌单", seed.title)
        assertEquals("编辑", seed.artist)
        assertEquals("描述", seed.description)
        assertEquals("https://example.com/cover.jpg", seed.coverImg)
        assertEquals("https://example.com/art.jpg", seed.artwork)
        assertEquals(30, seed.worksNum)
        assertEquals("sheet-1", seed.raw["id"])
        assertEquals("demo", seed.raw["platform"])
        assertEquals("描述", seed.raw["description"])
    }

    @Test
    fun `top list route fallback keeps lightweight fields`() {
        val seed = TopListDetailRoute(
            pluginPlatform = "demo",
            topListId = "top-1",
            title = "飙升榜",
            artist = "官方",
            description = "每日更新",
            coverImg = "https://example.com/top.jpg",
            artwork = "https://example.com/top-art.jpg",
            worksNum = 100,
            seedToken = null,
        ).fallbackTopListSeed()

        assertEquals("top-1", seed.id)
        assertEquals("demo", seed.platform)
        assertEquals("飙升榜", seed.title)
        assertEquals("官方", seed.artist)
        assertEquals("每日更新", seed.description)
        assertEquals("https://example.com/top.jpg", seed.coverImg)
        assertEquals("https://example.com/top-art.jpg", seed.artwork)
        assertEquals(100, seed.worksNum)
        assertEquals("top-1", seed.raw["id"])
        assertEquals("demo", seed.raw["platform"])
        assertEquals("每日更新", seed.raw["description"])
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.zili.android.musicfreeandroid.RoutesTest
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetSeedStoreTest --tests com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetRouteSeedTest
```

Expected:

- `RoutesTest` fails because route constructors do not have the new fields.
- `PluginSheetSeedStoreTest` fails because `PluginSheetSeedStore` does not exist.
- `PluginSheetRouteSeedTest` fails because fallback extension functions do not exist.

- [ ] **Step 5: Extend routes**

Modify `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`:

```kotlin
@Serializable
data class TopListDetailRoute(
    val pluginPlatform: String,
    val topListId: String,
    val title: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverImg: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val seedToken: String? = null,
)

@Serializable
data object RecommendSheetsRoute

@Serializable
data class PluginSheetDetailRoute(
    val pluginPlatform: String,
    val sheetId: String,
    val title: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverImg: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val seedToken: String? = null,
)
```

- [ ] **Step 6: Implement the seed store**

Create `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStore.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PluginSheetSeedStore {
    private val seeds = ConcurrentHashMap<String, MusicSheetItemBase>()

    fun put(item: MusicSheetItemBase): String {
        val token = UUID.randomUUID().toString()
        seeds[token] = item
        return token
    }

    fun take(token: String?): MusicSheetItemBase? {
        if (token.isNullOrBlank()) {
            return null
        }
        return seeds.remove(token)
    }

    internal fun clear() {
        seeds.clear()
    }
}
```

- [ ] **Step 7: Implement fallback seed helpers**

Create `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeed.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

fun PluginSheetDetailRoute.fallbackSheetSeed(): MusicSheetItemBase = MusicSheetItemBase(
    id = sheetId,
    platform = pluginPlatform,
    title = title,
    artist = artist,
    description = description,
    coverImg = coverImg,
    artwork = artwork,
    worksNum = worksNum,
    raw = routeRaw(
        id = sheetId,
        platform = pluginPlatform,
        title = title,
        artist = artist,
        description = description,
        coverImg = coverImg,
        artwork = artwork,
        worksNum = worksNum,
    ),
)

fun TopListDetailRoute.fallbackTopListSeed(): MusicSheetItemBase = MusicSheetItemBase(
    id = topListId,
    platform = pluginPlatform,
    title = title,
    artist = artist,
    description = description,
    coverImg = coverImg,
    artwork = artwork,
    worksNum = worksNum,
    raw = routeRaw(
        id = topListId,
        platform = pluginPlatform,
        title = title,
        artist = artist,
        description = description,
        coverImg = coverImg,
        artwork = artwork,
        worksNum = worksNum,
    ),
)

private fun routeRaw(
    id: String,
    platform: String,
    title: String?,
    artist: String?,
    description: String?,
    coverImg: String?,
    artwork: String?,
    worksNum: Int?,
): Map<String, Any?> = buildMap {
    put("id", id)
    put("platform", platform)
    title?.let { put("title", it) }
    artist?.let { put("artist", it) }
    description?.let { put("description", it) }
    coverImg?.let { put("coverImg", it) }
    artwork?.let { put("artwork", it) }
    worksNum?.let { put("worksNum", it) }
}
```

- [ ] **Step 8: Run tests to verify Task 1 passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.zili.android.musicfreeandroid.RoutesTest
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetSeedStoreTest --tests com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetRouteSeedTest
```

Expected: all selected tests pass.

- [ ] **Step 9: Commit Task 1**

Run:

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStore.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeed.kt feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetSeedStoreTest.kt feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetRouteSeedTest.kt
git commit -m "feat(home): carry plugin sheet route seeds"
```

## Task 2: Plugin Capability Filtering Helpers

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUi.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUiTest.kt`

- [ ] **Step 1: Add filtering helper tests**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUiTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.pluginfeature

import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluginCapabilityUiTest {

    @Test
    fun `pluginsSupporting keeps order and filters by method`() {
        val unsupported = loadedPlugin("unsupported", setOf("search"))
        val first = loadedPlugin("first", setOf("getTopLists", "search"))
        val second = loadedPlugin("second", setOf("getTopLists"))

        val result = listOf(unsupported, first, second).pluginsSupporting("getTopLists")

        assertEquals(
            listOf(
                PluginCapabilityUiModel(platform = "first", label = "first"),
                PluginCapabilityUiModel(platform = "second", label = "second"),
            ),
            result,
        )
    }

    @Test
    fun `pluginsSupporting excludes blank platforms`() {
        val blank = loadedPlugin(" ", setOf("getRecommendSheetsByTag"))
        val valid = loadedPlugin("demo", setOf("getRecommendSheetsByTag"))

        val result = listOf(blank, valid).pluginsSupporting("getRecommendSheetsByTag")

        assertEquals(listOf(PluginCapabilityUiModel(platform = "demo", label = "demo")), result)
    }

    private fun loadedPlugin(platform: String, supportedMethods: Set<String>): LoadedPlugin {
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
            ),
        )
        return plugin
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.pluginfeature.PluginCapabilityUiTest
```

Expected: FAIL because `PluginCapabilityUiModel` and `pluginsSupporting` do not exist.

- [ ] **Step 3: Implement filtering helper**

Create `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUi.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.pluginfeature

import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin

data class PluginCapabilityUiModel(
    val platform: String,
    val label: String,
)

fun List<LoadedPlugin>.pluginsSupporting(method: String): List<PluginCapabilityUiModel> =
    mapNotNull { plugin ->
        val platform = plugin.info.platform.trim()
        if (platform.isBlank()) {
            null
        } else if (method in plugin.info.supportedMethods) {
            PluginCapabilityUiModel(platform = platform, label = platform)
        } else {
            null
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.pluginfeature.PluginCapabilityUiTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUi.kt feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityUiTest.kt
git commit -m "feat(home): filter plugins by sheet capabilities"
```

## Task 3: Recommended Playlist ViewModel Filtering

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsUiState.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModel.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModelTest.kt`

- [ ] **Step 1: Add ViewModel tests**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModelTest.kt`:

```kotlin
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
    fun `availablePlugins only includes recommend-capable plugins in order`() = runTest {
        val unsupported = plugin("unsupported", setOf("search"))
        val capableA = plugin("capable-a", setOf("getRecommendSheetsByTag"))
        val capableB = plugin("capable-b", setOf("getRecommendSheetsByTag", "getRecommendSheetTags"))
        enabledPlugins.value = listOf(unsupported, capableA, capableB)

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        assertEquals(listOf("capable-a", "capable-b"), viewModel.availablePlugins.value.map { it.platform })
        assertEquals("capable-a", viewModel.selectedPlugin.value)
        verify(capableA).getRecommendSheetsByTag(mapOf("id" to ""), 1)
    }

    @Test
    fun `no capable plugins shows unsupported empty state`() = runTest {
        enabledPlugins.value = listOf(plugin("search-only", setOf("search")))

        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        assertTrue(viewModel.availablePlugins.value.isEmpty())
        assertFalse(viewModel.uiState.value.loading)
        assertEquals("当前没有支持推荐歌单的插件", viewModel.uiState.value.emptyMessage)
    }

    @Test
    fun `selectTag passes tag payload to plugin`() = runTest {
        val tag = musicSheet(id = "rock", title = "摇滚")
        val capable = plugin(
            platform = "capable",
            methods = setOf("getRecommendSheetsByTag", "getRecommendSheetTags"),
            tags = RecommendSheetTagsResult(pinned = listOf(tag), data = emptyList()),
        )
        enabledPlugins.value = listOf(capable)
        val viewModel = RecommendSheetsViewModel(pluginManager)
        advanceUntilIdle()

        viewModel.selectTag("rock")
        advanceUntilIdle()

        verify(capable).getRecommendSheetsByTag(
            eq(mapOf("id" to "rock", "title" to "摇滚")),
            eq(1),
        )
    }

    private fun plugin(
        platform: String,
        methods: Set<String>,
        tags: RecommendSheetTagsResult? = null,
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
            whenever(plugin.getRecommendSheetsByTag(any(), any())).thenReturn(
                PaginationResult(isEnd = true, data = emptyList()),
            )
        }
        return plugin
    }

    private fun musicSheet(id: String, title: String): MusicSheetItemBase = MusicSheetItemBase(
        id = id,
        platform = "capable",
        title = title,
        artist = null,
        description = null,
        coverImg = null,
        artwork = null,
        worksNum = null,
        raw = emptyMap(),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.recommendsheets.RecommendSheetsViewModelTest
```

Expected: FAIL because the ViewModel still exposes all plugins and `RecommendSheetsUiState` has no `emptyMessage`.

- [ ] **Step 3: Add empty message to UI state**

Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsUiState.kt` so the data class is:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.recommendsheets

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

data class RecommendSheetsUiState(
    val tags: List<RecommendTag> = emptyList(),
    val selectedTagId: String? = null,
    val sheets: List<MusicSheetItemBase> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = true,
    val errorMessage: String? = null,
    val emptyMessage: String? = null,
)

data class RecommendTag(
    val id: String,
    val title: String,
    val payload: Map<String, Any?>,
)
```

- [ ] **Step 4: Update ViewModel plugin source and empty handling**

Modify the top of `RecommendSheetsViewModel.kt`:

```kotlin
import com.zili.android.musicfreeandroid.feature.home.pluginfeature.PluginCapabilityUiModel
import com.zili.android.musicfreeandroid.feature.home.pluginfeature.pluginsSupporting
```

Replace `availablePlugins` with:

```kotlin
    val availablePlugins: StateFlow<List<PluginCapabilityUiModel>> = pluginManager.getSortedEnabledPlugins()
        .map { plugins -> plugins.pluginsSupporting("getRecommendSheetsByTag") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Replace the `availablePlugins.collect` block in `init` with:

```kotlin
        viewModelScope.launch {
            availablePlugins.collect { plugins ->
                when {
                    plugins.isEmpty() -> {
                        _selectedPlugin.value = null
                        _uiState.value = RecommendSheetsUiState(
                            loading = false,
                            isEnd = true,
                            emptyMessage = "当前没有支持推荐歌单的插件",
                        )
                    }
                    _selectedPlugin.value == null ||
                        plugins.none { it.platform == _selectedPlugin.value } -> {
                        selectPlugin(plugins.first().platform)
                    }
                }
            }
        }
```

In `loadTagsAndFirstPage`, keep plugin lookup and set unsupported state when lookup fails:

```kotlin
        if (plugin == null) {
            _uiState.value = RecommendSheetsUiState(
                loading = false,
                isEnd = true,
                errorMessage = "插件不存在：$platform",
            )
            return
        }
```

In `loadSheets`, clear `emptyMessage` whenever loading starts:

```kotlin
        _uiState.value = _uiState.value.copy(
            loading = reset,
            loadingMore = !reset,
            errorMessage = null,
            emptyMessage = null,
        )
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.recommendsheets.RecommendSheetsViewModelTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsUiState.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModel.kt feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModelTest.kt
git commit -m "feat(home): filter recommend sheet plugins"
```

## Task 4: Chart ViewModel Filtering

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModel.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModelTest.kt`

- [ ] **Step 1: Add ViewModel tests**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModelTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.toplist

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetGroupItem
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TopListViewModelTest {

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
    fun `availablePlugins only includes chart-capable plugins in order`() = runTest {
        val unsupported = plugin("unsupported", setOf("search"))
        val capableA = plugin("capable-a", setOf("getTopLists"))
        val capableB = plugin("capable-b", setOf("getTopLists", "getTopListDetail"))
        enabledPlugins.value = listOf(unsupported, capableA, capableB)

        val viewModel = TopListViewModel(pluginManager)
        advanceUntilIdle()

        assertEquals(listOf("capable-a", "capable-b"), viewModel.availablePlugins.value.map { it.platform })
        assertEquals("capable-a", viewModel.selectedPlugin.value)
        verify(capableA).getTopLists()
    }

    @Test
    fun `no capable plugins shows unsupported error state`() = runTest {
        enabledPlugins.value = listOf(plugin("search-only", setOf("search")))

        val viewModel = TopListViewModel(pluginManager)
        advanceUntilIdle()

        assertTrue(viewModel.availablePlugins.value.isEmpty())
        assertEquals(TopListUiState.Error("当前没有支持榜单的插件"), viewModel.uiState.value)
    }

    private fun plugin(platform: String, methods: Set<String>): LoadedPlugin {
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
            whenever(plugin.getTopLists()).thenReturn(
                listOf(
                    MusicSheetGroupItem(
                        title = "官方榜",
                        data = listOf(
                            MusicSheetItemBase(
                                id = "top-1",
                                platform = platform,
                                title = "飙升榜",
                                artist = null,
                                description = "每日更新",
                                coverImg = null,
                                artwork = null,
                                worksNum = null,
                                raw = emptyMap(),
                            ),
                        ),
                    ),
                ),
            )
        }
        return plugin
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.toplist.TopListViewModelTest
```

Expected: FAIL because the ViewModel still exposes all plugins.

- [ ] **Step 3: Update TopListViewModel plugin source**

Modify imports in `TopListViewModel.kt`:

```kotlin
import com.zili.android.musicfreeandroid.feature.home.pluginfeature.PluginCapabilityUiModel
import com.zili.android.musicfreeandroid.feature.home.pluginfeature.pluginsSupporting
```

Replace `availablePlugins` with:

```kotlin
    val availablePlugins: StateFlow<List<PluginCapabilityUiModel>> = pluginManager.getSortedEnabledPlugins()
        .map { plugins -> plugins.pluginsSupporting("getTopLists") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Replace its collector in `init` with:

```kotlin
        viewModelScope.launch {
            availablePlugins.collect { plugins ->
                when {
                    plugins.isEmpty() -> {
                        _selectedPlugin.value = null
                        _uiState.value = TopListUiState.Error("当前没有支持榜单的插件")
                    }
                    _selectedPlugin.value == null ||
                        plugins.none { it.platform == _selectedPlugin.value } -> {
                        selectPlugin(plugins.first().platform)
                    }
                }
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.toplist.TopListViewModelTest
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

Run:

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModel.kt feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModelTest.kt
git commit -m "feat(home): filter chart plugins"
```

## Task 5: Shared Plugin Tabs And Entry Page UI

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityTabs.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDisplayTextTest.kt`

- [ ] **Step 1: Add display text test for chart rows**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDisplayTextTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.toplist

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopListDisplayTextTest {

    @Test
    fun `subtitle prefers description over artist`() {
        assertEquals(
            "每日更新",
            topListSubtitle(
                item(description = "每日更新", artist = "官方"),
            ),
        )
    }

    @Test
    fun `subtitle falls back to artist`() {
        assertEquals("官方", topListSubtitle(item(description = null, artist = "官方")))
    }

    @Test
    fun `subtitle returns null when both values are blank`() {
        assertNull(topListSubtitle(item(description = " ", artist = "")))
    }

    private fun item(description: String?, artist: String?): MusicSheetItemBase = MusicSheetItemBase(
        id = "top-1",
        platform = "demo",
        title = "飙升榜",
        artist = artist,
        description = description,
        coverImg = null,
        artwork = null,
        worksNum = null,
        raw = emptyMap(),
    )
}
```

- [ ] **Step 2: Run display test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.toplist.TopListDisplayTextTest
```

Expected: FAIL because `topListSubtitle` does not exist.

- [ ] **Step 3: Add shared tab composable**

Create `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityTabs.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.pluginfeature

import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme

@Composable
fun PluginCapabilityTabs(
    plugins: List<PluginCapabilityUiModel>,
    selectedPlatform: String?,
    onSelectPlugin: (String) -> Unit,
) {
    if (plugins.isEmpty()) {
        return
    }
    ScrollableTabRow(
        selectedTabIndex = plugins.indexOfFirst { it.platform == selectedPlatform }.coerceAtLeast(0),
        containerColor = MusicFreeTheme.colors.background,
        contentColor = MusicFreeTheme.colors.primary,
        edgePadding = androidx.compose.ui.unit.dp(12),
    ) {
        plugins.forEach { plugin ->
            val selected = plugin.platform == selectedPlatform
            Tab(
                selected = selected,
                onClick = { onSelectPlugin(plugin.platform) },
                selectedContentColor = MusicFreeTheme.colors.primary,
                unselectedContentColor = MusicFreeTheme.colors.text,
                text = {
                    Text(
                        text = plugin.label,
                        fontSize = FontSizes.subTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
```

- [ ] **Step 4: Update recommended playlists UI**

In `RecommendSheetsScreen.kt`:

- Remove imports for `SegmentedButton`, `SegmentedButtonDefaults`, and `SingleChoiceSegmentedButtonRow`.
- Add imports:

```kotlin
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.zili.android.musicfreeandroid.feature.home.pluginfeature.PluginCapabilityTabs
```

Replace the segmented button block with:

```kotlin
                PluginCapabilityTabs(
                    plugins = plugins,
                    selectedPlatform = selectedPlugin,
                    onSelectPlugin = viewModel::selectPlugin,
                )
```

Replace the `else -> LazyColumn` sheet list body with:

```kotlin
                    uiState.sheets.isEmpty() && !uiState.emptyMessage.isNullOrBlank() -> {
                        EmptyState(uiState.emptyMessage ?: "当前没有支持推荐歌单的插件")
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                            verticalArrangement = Arrangement.spacedBy(rpx(18)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = rpx(24),
                                vertical = rpx(18),
                            ),
                        ) {
                            items(
                                items = uiState.sheets,
                                key = { item -> "${item.platform}:${item.id}" },
                            ) { item ->
                                RecommendSheetGridItem(
                                    item = item,
                                    onClick = {
                                        val platform = selectedPlugin
                                        if (!platform.isNullOrBlank()) {
                                            onOpenSheetDetail(platform, item)
                                        }
                                    },
                                )
                            }

                            if (!uiState.isEnd) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = rpx(20)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (uiState.loadingMore) {
                                            CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                                        } else {
                                            TextButton(onClick = { viewModel.loadMore() }) {
                                                Text("加载更多", color = MusicFreeTheme.colors.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
```

Add this composable above `EmptyState`:

```kotlin
@Composable
private fun RecommendSheetGridItem(
    item: MusicSheetItemBase,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            CoverImage(
                uri = item.artwork ?: item.coverImg,
                size = maxWidth,
                cornerRadius = rpx(12),
            )
        }
        Text(
            text = item.title ?: "未命名歌单",
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.subTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = rpx(12)),
        )
    }
}
```

- [ ] **Step 5: Update chart screen UI and callback signature**

In `TopListScreen.kt`:

- Replace the screen callback type:

```kotlin
    onOpenTopListDetail: (pluginPlatform: String, topList: MusicSheetItemBase) -> Unit,
```

- Replace segmented button imports with:

```kotlin
import com.zili.android.musicfreeandroid.feature.home.pluginfeature.PluginCapabilityTabs
```

- Replace the segmented button block with:

```kotlin
                PluginCapabilityTabs(
                    plugins = plugins,
                    selectedPlatform = selectedPlugin,
                    onSelectPlugin = viewModel::selectPlugin,
                )
```

- Update `TopListGroups` callback type and click:

```kotlin
    onOpenTopListDetail: (pluginPlatform: String, topList: MusicSheetItemBase) -> Unit,
```

```kotlin
                            onOpenTopListDetail(pluginPlatform, item)
```

- Add this pure helper near the bottom of the file:

```kotlin
internal fun topListSubtitle(item: MusicSheetItemBase): String? =
    item.description?.trim()?.takeIf { it.isNotBlank() }
        ?: item.artist?.trim()?.takeIf { it.isNotBlank() }
```

- In `TopListItemRow`, replace the artist text block with:

```kotlin
            val subtitle = topListSubtitle(item)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
```

- [ ] **Step 6: Update TopListNavigation callback signature**

Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListNavigation.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.toplist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.TopListRoute
import com.zili.android.musicfreeandroid.feature.home.toplist.TopListScreen
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

fun NavGraphBuilder.topListScreen(
    onBack: () -> Unit,
    onOpenTopListDetail: (pluginPlatform: String, topList: MusicSheetItemBase) -> Unit,
) {
    composable<TopListRoute> {
        TopListScreen(
            onBack = onBack,
            onOpenTopListDetail = onOpenTopListDetail,
        )
    }
}
```

- [ ] **Step 7: Run tests and compile feature module**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.toplist.TopListDisplayTextTest
./gradlew :feature:home:compileDebugKotlin
```

Expected: selected display test passes and `:feature:home:compileDebugKotlin` succeeds.

- [ ] **Step 8: Commit Task 5**

Run:

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginfeature/PluginCapabilityTabs.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListNavigation.kt feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDisplayTextTest.kt
git commit -m "feat(home): align recommend and chart entry ui"
```

## Task 6: Navigation Seed Wiring And Detail ViewModels

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailViewModel.kt`

- [ ] **Step 1: Wire seed storage in app navigation**

Modify imports in `AppNavHost.kt`:

```kotlin
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetSeedStore
```

Replace the `topListScreen` navigation block with:

```kotlin
        topListScreen(
            onBack = { navController.popBackStack() },
            onOpenTopListDetail = { pluginPlatform, topList ->
                val seedToken = PluginSheetSeedStore.put(topList)
                navController.navigate(
                    TopListDetailRoute(
                        pluginPlatform = pluginPlatform,
                        topListId = topList.id,
                        title = topList.title,
                        artist = topList.artist,
                        description = topList.description,
                        coverImg = topList.coverImg,
                        artwork = topList.artwork,
                        worksNum = topList.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
        )
```

Replace the `recommendSheetsScreen` navigation block with:

```kotlin
        recommendSheetsScreen(
            onBack = { navController.popBackStack() },
            onOpenSheetDetail = { pluginPlatform, sheet ->
                val seedToken = PluginSheetSeedStore.put(sheet)
                navController.navigate(
                    PluginSheetDetailRoute(
                        pluginPlatform = pluginPlatform,
                        sheetId = sheet.id,
                        title = sheet.title,
                        artist = sheet.artist,
                        description = sheet.description,
                        coverImg = sheet.coverImg,
                        artwork = sheet.artwork,
                        worksNum = sheet.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
        )
```

- [ ] **Step 2: Update plugin sheet detail ViewModel seed construction**

Add imports to `PluginSheetDetailViewModel.kt`:

```kotlin
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetSeedStore
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.fallbackSheetSeed
```

Replace `private fun seedSheet(): MusicSheetItemBase` with:

```kotlin
    private fun seedSheet(): MusicSheetItemBase =
        PluginSheetSeedStore.take(route.seedToken) ?: route.fallbackSheetSeed()
```

- [ ] **Step 3: Update top list detail ViewModel seed construction**

Add imports to `TopListDetailViewModel.kt`:

```kotlin
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetSeedStore
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.fallbackTopListSeed
```

In `loadInitial()`, replace:

```kotlin
        val seedTopList = findTopListById(route.topListId)
        if (seedTopList == null) {
            _uiState.value = TopListDetailUiState(
                loading = false,
                errorMessage = "未找到榜单：${route.topListId}",
            )
            return
        }
```

with:

```kotlin
        val seedTopList = PluginSheetSeedStore.take(route.seedToken)
            ?: route.fallbackTopListSeed()
```

Delete the `findTopListById` function if no code uses it after this change.

- [ ] **Step 4: Compile app to catch wiring errors**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

Run:

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailViewModel.kt
git commit -m "feat(home): use sheet seeds for plugin details"
```

## Task 7: Focused Verification And Final Build

**Files:**
- No production files expected.

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.pluginfeature.PluginCapabilityUiTest --tests com.zili.android.musicfreeandroid.feature.home.recommendsheets.RecommendSheetsViewModelTest --tests com.zili.android.musicfreeandroid.feature.home.toplist.TopListViewModelTest --tests com.zili.android.musicfreeandroid.feature.home.toplist.TopListDisplayTextTest --tests com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetSeedStoreTest --tests com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetRouteSeedTest
```

Expected: PASS.

- [ ] **Step 2: Run app route tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.zili.android.musicfreeandroid.RoutesTest
```

Expected: PASS.

- [ ] **Step 3: Run Debug build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: PASS and a debug APK is produced under `app/build/outputs/apk/debug/`.

- [ ] **Step 4: Check for devices**

Run:

```bash
adb devices
```

Expected if a device is available: output includes one `device` row after the header.

Expected if no device is available: output has only the header or offline devices; record that runtime validation is blocked by device availability.

- [ ] **Step 5: Runtime smoke test when a device is available**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.zili.android.musicfreeandroid.debug 1
```

Manual validation:

- Open `首页 -> 推荐歌单`.
- Confirm only recommend-capable plugins appear in the tab row.
- Tap a visible recommended sheet.
- Confirm the plugin sheet detail screen opens and does not show `加载歌单失败` on first load.
- Go back to home and open `首页 -> 榜单`.
- Confirm only chart-capable plugins appear in the tab row.
- Tap a visible chart.
- Confirm the chart detail screen opens and does not show `未找到榜单` on first load.

- [ ] **Step 6: Commit final verification note if runtime validation required documentation**

If runtime validation is blocked by no device or no plugin data, create `docs/home-fidelity/homepage/recommend-toplist-runtime-note.md`:

```markdown
# 推荐歌单与榜单运行态验收记录

> 文档状态：当前参考
> 适用范围：推荐歌单与榜单 RN 对齐实现后的本地运行态验收记录。
> 最后校验：2026-05-09

## 结果

运行态验收未完成。

## 阻塞原因

本地未检测到可用 Android 设备或模拟器，或当前环境没有可用于推荐歌单/榜单的插件数据。

## 已完成静态闸门

- `./gradlew :feature:home:testDebugUnitTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
```

Then commit it:

```bash
git add docs/home-fidelity/homepage/recommend-toplist-runtime-note.md
git commit -m "docs: record recommend toplist runtime validation"
```

If runtime validation succeeds, do not create this note.

- [ ] **Step 7: Show final diff summary**

Run:

```bash
git status --short
git log --oneline --max-count=8
```

Expected:

- `git status --short` is empty.
- The latest commits include the spec commit and implementation task commits.
