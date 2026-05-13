# RN Play Click Stay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对齐 RN 原版，让歌曲列表点击只切换播放或替换队列，不主动进入播放器页。

**Architecture:** 保留现有 ViewModel 播放语义和 DataStore 设置，只删除 UI 层与搜索播放事件中的自动导航副作用。MiniPlayer 继续作为进入 `PlayerRoute` 的入口，歌曲详情页 route 保留给独立详情入口使用。

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose typed routes, Coroutines Flow, JUnit4, Mockito Kotlin, Gradle Debug unit tests.

---

## Scope Check

该 spec 只覆盖一个行为面：歌曲列表播放动作不触发自动导航。它不拆分为多个子项目；搜索、首页各列表、导航 wrapper 和 contract test 必须在同一计划中收口，否则容易留下某个入口仍跳播放器页。

## File Structure

- Create: `app/src/test/java/com/zili/android/musicfreeandroid/navigation/PlaybackNavigationContractTest.kt`
  - 负责跨模块源码级守门，防止歌曲列表播放回调再次调用 `onNavigateToPlayer()` 或搜索成功事件再次发出导航事件。
- Modify: `feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModelTest.kt`
  - 增加运行态回归测试，验证搜索播放成功只调用播放器控制器，不发 UI 导航事件。
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
  - 删除成功播放后的 `NavigateToPlayer` 事件，只保留失败事件。
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt`
  - 删除 `onNavigateToPlayer` 参数和成功事件导航响应。
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/navigation/SearchNavigation.kt`
  - 删除搜索页 navigation wrapper 的 `onNavigateToPlayer` 参数。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreen.kt`
  - 点击过滤结果只调用 `viewModel.playFilteredItem(index)`。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/navigation/SearchMusicListNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数。
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt`
  - 更新 `SearchMusicListScreen` 调用签名。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
  - 本地音乐行点击只调用 `viewModel.playItem(item, items)`。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/navigation/LocalNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt`
  - 历史行点击只调用 `viewModel.playAt(index)`。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/navigation/HistoryNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
  - 用户歌单“播放全部”和歌曲行点击只播放，不导航。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
  - 插件歌单歌曲行点击只播放，不导航。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetDetailNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt`
  - 榜单歌曲行点击只播放，不导航；独立“详情”按钮保留。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListDetailNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数，保留 `onOpenMusicDetail`。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
  - 专辑歌曲行点击只播放，不导航。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/navigation/AlbumDetailNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt`
  - 歌手作品点击只播放，不导航。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/navigation/ArtistDetailNavigation.kt`
  - 删除 `onNavigateToPlayer` 参数。
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
  - 删除以上 destination 的 `onNavigateToPlayer = { navController.navigate(PlayerRoute) }` 传参；`playerScreen` 注册和 `MusicDetailRoute` 详情入口保留。

### Task 1: Regression Tests For No Auto Navigation

**Files:**
- Create: `app/src/test/java/com/zili/android/musicfreeandroid/navigation/PlaybackNavigationContractTest.kt`
- Modify: `feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModelTest.kt`

- [ ] **Step 1: Add a source contract test that fails on current automatic navigation**

Create `app/src/test/java/com/zili/android/musicfreeandroid/navigation/PlaybackNavigationContractTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.navigation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PlaybackNavigationContractTest {

    private val projectRoot: Path = locateProjectRoot()

    @Test
    fun `song list playback callbacks do not navigate to player`() {
        val forbiddenSnippets = mapOf(
            "feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt" to
                listOf("NavigateToPlayer"),
            "feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt" to
                listOf("onNavigateToPlayer()"),
            "app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt" to
                listOf("onNavigateToPlayer = { navController.navigate(PlayerRoute) }"),
        )

        val violations = forbiddenSnippets.flatMap { (relativePath, snippets) ->
            val source = Files.readString(projectRoot.resolve(relativePath))
            snippets.filter { snippet -> source.contains(snippet) }
                .map { snippet -> "$relativePath contains `$snippet`" }
        }

        assertTrue(
            "Song list playback must stay on the current screen; violations: $violations",
            violations.isEmpty(),
        )
    }

    private fun locateProjectRoot(): Path {
        val userDir = Paths.get("").toAbsolutePath().normalize()
        val candidates = generateSequence(userDir) { it.parent }.take(6).toList()
        return candidates.firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate project root from $userDir")
    }
}
```

- [ ] **Step 2: Add a SearchViewModel runtime test that fails on current success navigation event**

In `feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModelTest.kt`, add imports:

```kotlin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
```

Add this test near the existing `resolveAndPlay` tests:

```kotlin
    @Test
    fun `resolveAndPlay success does not emit navigation event`() = runTest {
        whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

        val item = musicItem(id = "1", title = "Song 1").copy(
            platform = "source",
            url = "https://resolver.example/1.mp3",
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val events = mutableListOf<SearchViewModel.PlayEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.playEvent.collect { event -> events += event }
        }

        viewModel.resolveAndPlay(item, listOf(item))
        advanceUntilIdle()
        collectJob.cancel()

        verify(playerController).playItem(item)
        verify(playerController, never()).playQueue(any(), any())
        assertTrue(events.isEmpty())
    }
```

- [ ] **Step 3: Run the new failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.zili.android.musicfreeandroid.navigation.PlaybackNavigationContractTest --no-daemon
./gradlew :feature:search:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.search.SearchViewModelTest --no-daemon
```

Expected:

- `PlaybackNavigationContractTest` fails and lists the files still containing `onNavigateToPlayer()` or `NavigateToPlayer`.
- `resolveAndPlay success does not emit navigation event` fails because current code emits `SearchViewModel.PlayEvent.NavigateToPlayer`.

- [ ] **Step 4: Commit the failing tests**

```bash
git add app/src/test/java/com/zili/android/musicfreeandroid/navigation/PlaybackNavigationContractTest.kt \
  feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModelTest.kt
git commit -m "test(playback): 覆盖歌曲点击不自动跳播放器页"
```

### Task 2: Search Result Playback Stays On Current Screen

**Files:**
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt:442-522`
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt:92-141`
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/navigation/SearchNavigation.kt:10-28`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt:151-193`

- [ ] **Step 1: Remove the search success navigation event from SearchViewModel**

Replace the `PlayEvent` declaration in `SearchViewModel.kt` with:

```kotlin
    sealed interface PlayEvent {
        data class Failed(val message: String) : PlayEvent
    }
```

In `resolveAndPlay`, replace the success branch after the `when (appPreferences.clickMusicInSearch.first())` block with this exact shape:

```kotlin
                    when (appPreferences.clickMusicInSearch.first()) {
                        SearchResultClickAction.PlayMusic -> playerController.playItem(resolvedItem)
                        SearchResultClickAction.PlayMusicAndReplace -> {
                            playerController.playQueue(resolvedQueue, startIndex)
                        }
                    }
```

Keep both failure branches unchanged:

```kotlin
                    _playEvent.emit(PlayEvent.Failed("播放失败，请重试"))
```

- [ ] **Step 2: Remove the search screen player navigation parameter**

In `SearchScreen.kt`, change the function signature to:

```kotlin
fun SearchScreen(
    onBack: () -> Unit,
    onOpenAlbumDetail: (AlbumItemBase) -> Unit,
    onOpenArtistDetail: (ArtistItemBase) -> Unit,
    onOpenSheetDetail: (MusicSheetItemBase) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
```

Replace the play event observer with:

```kotlin
    LaunchedEffect(Unit) {
        viewModel.playEvent.onEach { event ->
            when (event) {
                is SearchViewModel.PlayEvent.Failed ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }.launchIn(this)
    }
```

- [ ] **Step 3: Remove the search navigation wrapper parameter**

In `SearchNavigation.kt`, replace the function with:

```kotlin
fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onOpenAlbumDetail: (AlbumItemBase) -> Unit,
    onOpenArtistDetail: (ArtistItemBase) -> Unit,
    onOpenSheetDetail: (MusicSheetItemBase) -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(
            onBack = onBack,
            onOpenAlbumDetail = onOpenAlbumDetail,
            onOpenArtistDetail = onOpenArtistDetail,
            onOpenSheetDetail = onOpenSheetDetail,
        )
    }
}
```

- [ ] **Step 4: Remove the AppNavHost search pass-through**

In `AppNavHost.kt`, update the `searchScreen(` call by deleting this argument:

```kotlin
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
```

The resulting start of the call should be:

```kotlin
        searchScreen(
            onBack = { navController.popBackStack() },
            onOpenAlbumDetail = { album ->
```

- [ ] **Step 5: Run search tests**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.search.SearchViewModelTest --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit search behavior**

```bash
git add feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt \
  feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt \
  feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/navigation/SearchNavigation.kt \
  app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt
git commit -m "fix(search): 点击歌曲结果仅切换播放"
```

### Task 3: Home List Playback Stays On Current Screen

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreen.kt:53-151`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/navigation/SearchMusicListNavigation.kt:8-18`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt:39-66`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt:43-198`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/navigation/LocalNavigation.kt:8-23`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt:45-110`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/navigation/HistoryNavigation.kt:8-19`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt:38-123`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailNavigation.kt:8-20`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt:52-128`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetDetailNavigation.kt:8-17`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt:40-109`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListDetailNavigation.kt:11-22`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt:44-120`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/navigation/AlbumDetailNavigation.kt:8-16`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt:31-91`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/navigation/ArtistDetailNavigation.kt:8-16`

- [ ] **Step 1: Update SearchMusicListScreen and tests**

In `SearchMusicListScreen.kt`, remove `onNavigateToPlayer` from the function signature:

```kotlin
fun SearchMusicListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchMusicListViewModel = hiltViewModel(),
) {
```

Replace the filtered row click with:

```kotlin
                            SearchMusicListItem(
                                item = item,
                                onClick = {
                                    viewModel.playFilteredItem(index)
                                },
                            )
```

In `SearchMusicListNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.searchMusicListScreen(
    onBack: () -> Unit,
) {
    composable<SearchMusicListRoute> {
        SearchMusicListScreen(
            onBack = onBack,
        )
    }
}
```

In `SearchMusicListScreenFocusTest.kt`, update both `SearchMusicListScreen` calls to:

```kotlin
                SearchMusicListScreen(
                    onBack = {},
                    viewModel = viewModel,
                )
```

- [ ] **Step 2: Update LocalScreen and LocalNavigation**

In `LocalScreen.kt`, remove `onNavigateToPlayer` from the function signature and replace `onItemClick` with:

```kotlin
            onItemClick = { item, items ->
                viewModel.playItem(item, items)
            },
```

In `LocalNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.localScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: () -> Unit,
    onNavigateToMusicListEditor: () -> Unit,
    onNavigateToDownloading: () -> Unit,
) {
    composable<LocalRoute> {
        LocalScreen(
            onBack = onBack,
            onNavigateToSearchMusicList = onNavigateToSearchMusicList,
            onNavigateToMusicListEditor = onNavigateToMusicListEditor,
            onNavigateToDownloading = onNavigateToDownloading,
        )
    }
}
```

- [ ] **Step 3: Update HistoryScreen and HistoryNavigation**

In `HistoryScreen.kt`, remove `onNavigateToPlayer` from the signature and replace the row `combinedClickable` click with:

```kotlin
                                    onClick = { viewModel.playAt(index) },
```

In `HistoryNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.historyScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: () -> Unit,
) {
    composable<HistoryRoute> {
        HistoryScreen(
            onBack = onBack,
            onNavigateToSearchMusicList = onNavigateToSearchMusicList,
        )
    }
}
```

- [ ] **Step 4: Update PlaylistDetailScreen and PlaylistDetailNavigation**

In `PlaylistDetailScreen.kt`, remove `onNavigateToPlayer` from the signature. Replace header playback with:

```kotlin
                onPlayAll = {
                    viewModel.playAll()
                },
```

Replace row click with:

```kotlin
                            onClick = {
                                viewModel.playAll(startIndex = index)
                            },
```

In `PlaylistDetailNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.playlistDetailScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: (String) -> Unit,
    onNavigateToMusicListEditorLite: (String) -> Unit,
) {
    composable<PlaylistDetailRoute> {
        PlaylistDetailScreen(
            onBack = onBack,
            onNavigateToSearchMusicList = onNavigateToSearchMusicList,
            onNavigateToMusicListEditorLite = onNavigateToMusicListEditorLite,
        )
    }
}
```

- [ ] **Step 5: Update plugin sheet and top list detail screens**

In `PluginSheetDetailScreen.kt`, remove `onNavigateToPlayer` from the signature and replace the row click coroutine with:

```kotlin
                            onClick = {
                                scope.launch {
                                    viewModel.playAt(index)
                                }
                            },
```

In `PluginSheetDetailNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.pluginSheetDetailScreen(
    onBack: () -> Unit,
) {
    composable<PluginSheetDetailRoute> {
        PluginSheetDetailScreen(
            onBack = onBack,
        )
    }
}
```

In `TopListDetailScreen.kt`, keep `onOpenMusicDetail`, remove `onNavigateToPlayer`, and replace the row click coroutine with:

```kotlin
                                    onClick = {
                                        scope.launch {
                                            viewModel.playAt(index)
                                        }
                                    },
```

In `TopListDetailNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.topListDetailScreen(
    onBack: () -> Unit,
    onOpenMusicDetail: (MusicItem) -> Unit,
) {
    composable<TopListDetailRoute> {
        TopListDetailScreen(
            onBack = onBack,
            onOpenMusicDetail = onOpenMusicDetail,
        )
    }
}
```

- [ ] **Step 6: Update album and artist detail screens**

In `AlbumDetailScreen.kt`, remove `onNavigateToPlayer` and replace the row click coroutine with:

```kotlin
                                    onClick = {
                                        scope.launch {
                                            viewModel.playAt(index)
                                        }
                                    },
```

In `AlbumDetailNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.albumDetailScreen(
    onBack: () -> Unit,
) {
    composable<AlbumDetailRoute> {
        AlbumDetailScreen(
            onBack = onBack,
        )
    }
}
```

In `ArtistDetailScreen.kt`, remove `onNavigateToPlayer` and replace the row click with:

```kotlin
                                .clickable {
                                    scope.launch {
                                        viewModel.playAt(index)
                                    }
                                }
```

In `ArtistDetailNavigation.kt`, replace the wrapper with:

```kotlin
fun NavGraphBuilder.artistDetailScreen(
    onBack: () -> Unit,
) {
    composable<ArtistDetailRoute> {
        ArtistDetailScreen(
            onBack = onBack,
        )
    }
}
```

- [ ] **Step 7: Run feature home tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.searchmusiclist.SearchMusicListScreenFocusTest --no-daemon
./gradlew :feature:home:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit home screen behavior**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/navigation/SearchMusicListNavigation.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/navigation/LocalNavigation.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/navigation/HistoryNavigation.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailNavigation.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetDetailNavigation.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListDetailNavigation.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/navigation/AlbumDetailNavigation.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/navigation/ArtistDetailNavigation.kt
git commit -m "fix(home): 歌曲列表点击停留当前页"
```

### Task 4: AppNavHost Cleanup And Contract Pass

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt:118-333`
- Test: `app/src/test/java/com/zili/android/musicfreeandroid/navigation/PlaybackNavigationContractTest.kt`

- [ ] **Step 1: Remove obsolete AppNavHost pass-through arguments**

In `AppNavHost.kt`, remove `onNavigateToPlayer = { navController.navigate(PlayerRoute) },` from these destination calls:

```kotlin
        localScreen(
            onBack = { navController.popBackStack() },
            onNavigateToSearchMusicList = {
                navController.navigate(SearchMusicListRoute.localLibrary())
            },
            onNavigateToMusicListEditor = {
                navController.navigate(MusicListEditorLiteRoute.localLibrary())
            },
            onNavigateToDownloading = { navController.navigate(DownloadingRoute) },
        )
```

```kotlin
        playlistDetailScreen(
            onBack = { navController.popBackStack() },
            onNavigateToSearchMusicList = { playlistId ->
                navController.navigate(SearchMusicListRoute.playlist(playlistId))
            },
            onNavigateToMusicListEditorLite = { playlistId ->
                navController.navigate(MusicListEditorLiteRoute(playlistId))
            },
        )
```

```kotlin
        historyScreen(
            onBack = { navController.popBackStack() },
            onNavigateToSearchMusicList = {
                navController.navigate(SearchMusicListRoute.history())
            },
        )
```

```kotlin
        searchMusicListScreen(
            onBack = { navController.popBackStack() },
        )
```

```kotlin
        topListDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenMusicDetail = { item ->
                val seedToken = MusicDetailSeedStore.put(item)
                navController.navigate(
                    MusicDetailRoute(
                        pluginPlatform = item.platform,
                        musicId = item.id,
                        title = item.title,
                        artist = item.artist,
                        album = item.album,
                        artwork = item.artwork,
                        durationMs = item.duration,
                        seedToken = seedToken,
                    ),
                )
            },
        )
```

```kotlin
        pluginSheetDetailScreen(
            onBack = { navController.popBackStack() },
        )
```

```kotlin
        albumDetailScreen(
            onBack = { navController.popBackStack() },
        )
        artistDetailScreen(
            onBack = { navController.popBackStack() },
        )
```

Remove the now-unused import:

```kotlin
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
```

- [ ] **Step 2: Run app compile and contract tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.zili.android.musicfreeandroid.navigation.PlaybackNavigationContractTest --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: PASS. The contract test must no longer list any forbidden snippets.

- [ ] **Step 3: Commit navigation cleanup**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt \
  app/src/test/java/com/zili/android/musicfreeandroid/navigation/PlaybackNavigationContractTest.kt
git commit -m "fix(navigation): 移除歌曲播放自动跳转"
```

### Task 5: Final Verification And Merge-Ready Review

**Files:**
- Review all touched files from Tasks 1-4.

- [ ] **Step 1: Run dev harness grep check**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: PASS with no forbidden harness matches.

- [ ] **Step 2: Run targeted tests**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --no-daemon
./gradlew :feature:home:testDebugUnitTest --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: all three commands PASS.

- [ ] **Step 3: Run Debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run diff hygiene checks**

Run:

```bash
git diff --check
git status --short --branch
```

Expected:

- `git diff --check` prints no whitespace errors.
- `git status --short --branch` shows branch `rn-play-click-stay` with no unstaged changes after final commits.

- [ ] **Step 5: Manual smoke when an emulator is available**

Run:

```bash
adb devices
```

When a device is listed, install the Debug APK and smoke these entry points:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Manual checks:

1. Search a song, tap a music result, confirm the current screen stays on search results and MiniPlayer updates.
2. Open a plugin sheet or top list detail, tap a song row, confirm the current screen stays on the detail page and MiniPlayer updates.
3. Open local music, tap a song row, confirm the current screen stays on local music and MiniPlayer updates.
4. Tap MiniPlayer, confirm `PlayerRoute` still opens.
