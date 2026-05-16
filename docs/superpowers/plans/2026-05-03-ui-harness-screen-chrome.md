# UI Harness Screen Chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish enforceable UI harness rules for Screen transitions, ordinary AppBar pages, and immersive status bar handling, then migrate existing ordinary AppBar Screens to the shared Compose entry points.

**Architecture:** Rules live in common repository docs and are referenced from `AGENTS.md`. Navigation transition defaults live in `:app` because `AppNavHost` owns destination assembly. Shared screen chrome composables live in `:core` so `:feature:*` modules can reuse them without depending on `:app`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Navigation Compose type-safe routes, Gradle unit tests, Markdown project docs.

---

## File Structure

- Create: `docs/ui-harness/screen-chrome-rules.md`
  - Single current rules source for AI Coding tools and human reviewers.
- Modify: `AGENTS.md`
  - Adds a short mandatory pointer to the UI harness rules.
- Modify: `docs/DOCS_STATUS.md`
  - Registers the rules document as `当前规范`.
- Create: `app/src/main/java/com/hank/musicfree/navigation/MusicFreeNavTransitions.kt`
  - Centralizes ordinary Screen transition duration and transition builders.
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
  - Replaces inline `250ms` transitions with shared `100ms` transition functions.
- Create: `app/src/test/java/com/hank/musicfree/navigation/MusicFreeNavTransitionsTest.kt`
  - Locks the RN-aligned `100ms` default in a JVM unit test.
- Create: `core/src/main/java/com/hank/musicfree/core/ui/MusicFreeScreenChrome.kt`
  - Provides `MusicFreeStatusBarChrome`, `MusicFreeTopAppBar`, and `MusicFreeScreenScaffold`.
- Modify: `app/src/main/java/com/hank/musicfree/MainActivity.kt`
  - Removes the route-specific top safe-area whitelist and leaves top chrome to Screens.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreenContent.kt`
  - Adds explicit top status bar spacing for the custom Home chrome.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/local/LocalScreen.kt`
  - Adds explicit top status bar spacing for the current no-AppBar Local page.
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
  - Uses the shared status bar chrome helper for its custom search bar.
- Modify ordinary AppBar Screens:
  - `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`
  - `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsScreen.kt`
  - `feature/settings/src/main/java/com/hank/musicfree/feature/settings/fileselector/FileSelectorLiteScreen.kt`
  - `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`
  - `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/PluginSortScreen.kt`
  - `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/PluginSubscriptionScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/history/HistoryScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListDetailScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/musicdetail/MusicDetailScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/AlbumDetailScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/artistdetail/ArtistDetailScreen.kt`
  - `feature/home/src/main/java/com/hank/musicfree/feature/home/musiclisteditor/MusicListEditorLiteScreen.kt`

---

### Task 1: Add Rules Entry Points

**Files:**
- Create: `docs/ui-harness/screen-chrome-rules.md`
- Modify: `AGENTS.md`
- Modify: `docs/DOCS_STATUS.md`

- [ ] **Step 1: Create the UI harness rules document**

Create `docs/ui-harness/screen-chrome-rules.md` with this exact content:

```markdown
# UI Harness Screen Chrome Rules

> 文档状态：当前规范
> 适用范围：Screen 切换动画、普通 AppBar 页面、沉浸式状态栏、后续 AI Coding Screen 改动。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../DOCS_STATUS.md)、[AGENTS](../../AGENTS.md)
> 设计来源：[UI Harness 与 Screen Chrome 规范设计](../superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md)
> 最后校验：2026-05-03

## 强制入口

新增或修改 Compose Screen 前，必须先读取本文件。

本文件是 Screen 切换动画、普通 AppBar、状态栏沉浸式处理的唯一当前规则来源。`docs/superpowers/plans/*.md` 中出现的旧动画时长、旧 AppBar 写法、旧状态栏做法均视为历史执行快照，不可作为当前规范。

## Screen 切换动画

- 普通页面 MUST 使用全局默认 `slide_from_right` 动画。
- 普通页面前进动画 MUST 为新页面从右向左进入、旧页面向左退出。
- 普通页面返回动画 MUST 为上一页从左侧回入、当前页向右退出。
- 普通页面默认动画时长 MUST 为 `100ms`，对齐 `../MusicFree/src/entry/index.tsx` 中的 `animationDuration: 100`。
- `AppNavHost` MUST 引用集中 transition helper，MUST NOT 在 `NavHost` 参数里手写 `tween(250)` 或其他局部时长。
- Screen 内部 MUST NOT 用局部 `AnimatedContent`、`AnimatedVisibility` 或自定义 offset 动画伪装页面切换。
- 特殊页面若需要不同页面切换动画，MUST 在 route/destination 注册处显式覆盖，并在本文件“特殊 Chrome 页面”中登记原因。

## 普通 AppBar 页面

普通 AppBar 页面 MUST 使用 `com.hank.musicfree.core.ui.MusicFreeScreenScaffold` 或 `MusicFreeTopAppBar`。

普通 AppBar 页面 MUST NOT 直接手写以下模式：

```kotlin
TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MusicFreeTheme.colors.appBar,
    ),
)
```

普通 AppBar 页面状态栏规则：

- Activity 级别保持 edge-to-edge。
- 系统状态栏保持透明。
- `MusicFreeTheme.colors.appBar` MUST 铺到状态栏后方。
- AppBar 内容 MUST 从状态栏下方开始。
- AppBar 内容高度 MUST 对齐 RN `rpx(88)`。
- 标题文字 MUST 使用 `FontSizes.appBar` 和 `MusicFreeTheme.colors.appBarText`，除非页面设计文档声明了特殊标题内容。

## 特殊 Chrome 页面

以下页面不使用普通 AppBar，但 MUST 自行负责状态栏背景和顶部 inset：

- `HomeRoute` / `HomeScreen`：首页使用自定义 `HomeNavBar`，状态栏区域保持首页背景，不依赖 `MainActivity` 顶部 safe inset。
- `SearchRoute` / `SearchScreen`：搜索页使用自定义搜索栏，`appBar` 色延伸到状态栏后方。
- `PlayerRoute` / `PlayerScreen`：播放器是全屏沉浸式页面，顶部内容可以绘制到系统栏区域。
- `LocalRoute` / `LocalScreen`：当前 Android 实现没有普通 AppBar，必须显式添加顶部 status bar spacer，避免内容进入状态栏。

新增特殊 Chrome 页面时，必须在本节登记 route、Screen、原因和状态栏策略。

## MainActivity 责任边界

`MainActivity` 负责 App 级 `Scaffold`、MiniPlayer、横向 safe inset、底部 safe inset。

`MainActivity` MUST NOT 维护“普通页面统一补顶部 safe inset，某些页面排除”的隐式白名单。顶部 chrome 是 Screen 或公共 UI harness 的责任。

## 新增 Screen 默认做法

新增普通页面时，默认结构为：

```kotlin
MusicFreeScreenScaffold(
    title = "页面标题",
    onBack = onBack,
    modifier = modifier,
) { innerPadding ->
    // Page content starts here.
}
```

新增自定义顶部页面时，必须先说明为什么不能使用普通 AppBar，并使用 `MusicFreeStatusBarChrome` 或等价实现显式处理顶部状态栏区域。
```

- [ ] **Step 2: Add the AGENTS pointer**

In `AGENTS.md`, add this section after “核心设计约束” and before “主题系统”:

```markdown
### UI Harness Rules

新增或修改 Compose Screen 前，必须读取并遵守 [screen-chrome-rules](docs/ui-harness/screen-chrome-rules.md)。

- Screen 切换动画、普通 AppBar、沉浸式状态栏处理必须走公共 harness 入口。
- 普通 AppBar 页面不得直接手写分散的 `TopAppBar` + `TopAppBarDefaults.topAppBarColors(...)`。
- 特殊 Chrome 页面必须在规则文档中登记，并自行负责状态栏背景和顶部 inset。
- `docs/superpowers/plans/*.md` 中旧动画或 AppBar 写法不作为当前 UI Harness 规范来源。
```

- [ ] **Step 3: Register the rules document in DOCS_STATUS**

In `docs/DOCS_STATUS.md`, add this row immediately after the `docs/DOCS_STATUS.md` row:

```markdown
| [docs/ui-harness/screen-chrome-rules.md](./ui-harness/screen-chrome-rules.md) | 当前规范（UI Harness Rules） | 是 | Screen 切换动画、普通 AppBar 与沉浸式状态栏强制规则 |
```

Keep the existing design spec row for `2026-05-03-ui-harness-screen-chrome-design.md`.

- [ ] **Step 4: Commit Task 1**

Run:

```bash
git add AGENTS.md docs/DOCS_STATUS.md docs/ui-harness/screen-chrome-rules.md
git commit -m "docs: add UI harness screen chrome rules"
```

Expected: commit succeeds with only the three files above.

---

### Task 2: Centralize Navigation Transitions

**Files:**
- Create: `app/src/main/java/com/hank/musicfree/navigation/MusicFreeNavTransitions.kt`
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
- Create: `app/src/test/java/com/hank/musicfree/navigation/MusicFreeNavTransitionsTest.kt`

- [ ] **Step 1: Write the transition duration test**

Create `app/src/test/java/com/hank/musicfree/navigation/MusicFreeNavTransitionsTest.kt`:

```kotlin
package com.hank.musicfree.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFreeNavTransitionsTest {
    @Test
    fun ordinaryScreenTransitionDurationMatchesRn() {
        assertEquals(100, MusicFreeScreenTransitionDurationMillis)
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.navigation.MusicFreeNavTransitionsTest
```

Expected: fails because `MusicFreeScreenTransitionDurationMillis` is not defined.

- [ ] **Step 3: Add centralized transition helpers**

Create `app/src/main/java/com/hank/musicfree/navigation/MusicFreeNavTransitions.kt`:

```kotlin
package com.hank.musicfree.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

internal const val MusicFreeScreenTransitionDurationMillis = 100

private val musicFreeScreenTransitionSpec = tween<IntOffset>(
    durationMillis = MusicFreeScreenTransitionDurationMillis,
)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreeEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = musicFreeScreenTransitionSpec,
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreeExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = musicFreeScreenTransitionSpec,
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreePopEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = musicFreeScreenTransitionSpec,
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreePopExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = musicFreeScreenTransitionSpec,
    )
```

- [ ] **Step 4: Wire AppNavHost to the helpers**

In `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`, remove these imports:

```kotlin
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
```

Replace the four `NavHost` transition lambdas with:

```kotlin
enterTransition = { musicFreeEnterTransition() },
exitTransition = { musicFreeExitTransition() },
popEnterTransition = { musicFreePopEnterTransition() },
popExitTransition = { musicFreePopExitTransition() },
```

- [ ] **Step 5: Run the transition test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.navigation.MusicFreeNavTransitionsTest
```

Expected: pass.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
git add app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt app/src/main/java/com/hank/musicfree/navigation/MusicFreeNavTransitions.kt app/src/test/java/com/hank/musicfree/navigation/MusicFreeNavTransitionsTest.kt
git commit -m "feat(app): centralize screen navigation transitions"
```

Expected: commit succeeds.

---

### Task 3: Add Core Screen Chrome Composables

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/ui/MusicFreeScreenChrome.kt`

- [ ] **Step 1: Create the shared chrome file**

Create `core/src/main/java/com/hank/musicfree/core/ui/MusicFreeScreenChrome.kt`:

```kotlin
package com.hank.musicfree.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.IconSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx

@Composable
fun MusicFreeStatusBarChrome(
    modifier: Modifier = Modifier,
    color: Color = MusicFreeTheme.colors.appBar,
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(color),
    )
}

@Composable
fun MusicFreeTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    MusicFreeTopAppBar(
        titleContent = {
            Text(
                text = title,
                color = MusicFreeTheme.colors.appBarText,
                fontSize = FontSizes.appBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onBack = onBack,
        modifier = modifier,
        actions = actions,
    )
}

@Composable
fun MusicFreeTopAppBar(
    titleContent: @Composable RowScope.() -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MusicFreeTheme.colors
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.appBar),
    ) {
        MusicFreeStatusBarChrome(color = colors.appBar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rpx(88))
                .padding(horizontal = rpx(24)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.appBarText,
                    modifier = Modifier.size(IconSizes.normal),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = rpx(24)),
                verticalAlignment = Alignment.CenterVertically,
                content = titleContent,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun MusicFreeScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MusicFreeTheme.colors.pageBackground,
    content: @Composable (PaddingValues) -> Unit,
) {
    MusicFreeScreenScaffold(
        titleContent = {
            Text(
                text = title,
                color = MusicFreeTheme.colors.appBarText,
                fontSize = FontSizes.appBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onBack = onBack,
        modifier = modifier,
        actions = actions,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        content = content,
    )
}

@Composable
fun MusicFreeScreenScaffold(
    titleContent: @Composable RowScope.() -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MusicFreeTheme.colors.pageBackground,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MusicFreeTopAppBar(
                titleContent = titleContent,
                onBack = onBack,
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(0.dp),
        content = content,
    )
}
```

- [ ] **Step 2: Build core**

Run:

```bash
./gradlew :core:assembleDebug
```

Expected: build succeeds.

- [ ] **Step 3: Commit Task 3**

Run:

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/MusicFreeScreenChrome.kt
git commit -m "feat(core): add shared screen chrome components"
```

Expected: commit succeeds.

---

### Task 4: Move Top Insets Out of MainActivity

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/MainActivity.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreenContent.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/local/LocalScreen.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`

- [ ] **Step 1: Simplify MainActivity**

In `MainActivity.kt`, remove these imports:

```kotlin
import androidx.compose.foundation.layout.windowInsetsPadding
import com.hank.musicfree.core.navigation.HomeRoute
import com.hank.musicfree.core.navigation.SearchRoute
```

Remove these values:

```kotlin
val isHomeRoute = destination?.hasRoute<HomeRoute>() == true
val isSearchRoute = destination?.hasRoute<SearchRoute>() == true
val applyTopSafeInset = !isPlayerRoute && !isSearchRoute
```

Replace the `AppNavHost` modifier block with:

```kotlin
modifier = Modifier.padding(innerPadding),
```

- [ ] **Step 2: Add explicit Home status spacing**

In `HomeScreenContent.kt`, add imports:

```kotlin
import androidx.compose.ui.graphics.Color
import com.hank.musicfree.core.ui.MusicFreeStatusBarChrome
```

Inside the `LazyColumn` block, insert this item before the existing `HomeNavBar` item:

```kotlin
item {
    MusicFreeStatusBarChrome(color = Color.Transparent)
}
```

- [ ] **Step 3: Add explicit Local status spacing**

In `LocalScreen.kt`, add imports:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.weight
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.MusicFreeStatusBarChrome
```

Replace the final `LocalMusicContent(...)` call with:

```kotlin
Column(
    modifier = modifier
        .fillMaxSize()
        .testTag(FidelityAnchors.Screen.LocalRoot)
        .semantics { testTagsAsResourceId = true },
) {
    MusicFreeStatusBarChrome(color = MusicFreeTheme.colors.pageBackground)
    LocalMusicContent(
        uiState = localUiState,
        onItemClick = { item, items ->
            viewModel.playItem(item, items)
            onNavigateToPlayer()
        },
        onItemLongClick = { item -> addToPlaylistItem = item },
        onRetry = {
            if (hasAudioPermission == false) {
                permissionLauncher.launch(permission)
            } else {
                viewModel.scanLocalMusic()
            }
        },
        modifier = Modifier.weight(1f),
    )
}
```

- [ ] **Step 4: Use the shared Search status chrome**

In `SearchScreen.kt`, remove these imports:

```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
```

Add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeStatusBarChrome
```

Replace:

```kotlin
Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
```

with:

```kotlin
MusicFreeStatusBarChrome(color = colors.appBar)
```

- [ ] **Step 5: Build app**

Run:

```bash
./gradlew :app:build
```

Expected: build succeeds.

- [ ] **Step 6: Commit Task 4**

Run:

```bash
git add app/src/main/java/com/hank/musicfree/MainActivity.kt feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreenContent.kt feature/home/src/main/java/com/hank/musicfree/feature/home/local/LocalScreen.kt feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt
git commit -m "feat(app): move top inset ownership to screens"
```

Expected: commit succeeds.

---

### Task 5: Migrate Settings Feature AppBars

**Files:**
- Modify: settings feature Screens listed in File Structure.

- [ ] **Step 1: Migrate `SettingsScreen.kt`**

In `SettingsScreen.kt`, remove Material3 AppBar imports:

```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
```

Remove:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
```

Add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
```

Remove `@OptIn(ExperimentalMaterial3Api::class)`.

Replace the outer `Scaffold(...) { innerPadding -> ... }` with:

```kotlin
MusicFreeScreenScaffold(
    title = "设置",
    onBack = onBack,
    modifier = modifier
        .fillMaxSize()
        .testTag(FidelityAnchors.Screen.SettingsRoot)
        .semantics { testTagsAsResourceId = true },
) { innerPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = rpx(24)),
    ) {
        // Keep the existing LazyColumn items unchanged.
    }
}
```

Move the existing `LazyColumn` item content into the lambda above without changing item order, strings, test tags, or click handlers.

- [ ] **Step 2: Migrate `PermissionsScreen.kt`**

Apply the same import cleanup as `SettingsScreen.kt`. Add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
```

Replace the outer `Scaffold` with:

```kotlin
MusicFreeScreenScaffold(
    title = "权限管理",
    onBack = onBack,
    modifier = modifier.fillMaxSize(),
) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        // Keep the existing permission cards and body content unchanged.
    }
}
```

- [ ] **Step 3: Migrate `FileSelectorLiteScreen.kt`**

Add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
```

Replace the outer `Scaffold` with:

```kotlin
MusicFreeScreenScaffold(
    title = "选择目录",
    onBack = onBack,
    modifier = modifier.fillMaxSize(),
) { innerPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // Keep the existing file selector list items unchanged.
    }
}
```

- [ ] **Step 4: Migrate `PluginListScreen.kt`**

Add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
```

Replace its outer `Scaffold` with:

```kotlin
MusicFreeScreenScaffold(
    title = "插件管理",
    onBack = onBack,
    modifier = modifier,
    actions = {
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("订阅设置") },
                    onClick = { showMenu = false; onNavigateToPluginSubscription() },
                )
                DropdownMenuItem(
                    text = { Text("排序") },
                    onClick = { showMenu = false; onNavigateToPluginSort() },
                )
                DropdownMenuItem(
                    text = { Text("卸载全部") },
                    onClick = { showMenu = false; showUninstallAllConfirm = true },
                )
            }
        }
    },
    floatingActionButton = {
        Box {
            FloatingActionButton(onClick = { showFabMenu = true }) {
                Icon(Icons.Default.Add, contentDescription = "安装插件")
            }
            DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                DropdownMenuItem(
                    text = { Text("从本地安装") },
                    onClick = { showFabMenu = false; showInstallLocalDialog = true },
                )
                DropdownMenuItem(
                    text = { Text("从网络安装") },
                    onClick = { showFabMenu = false; showInstallUrlDialog = true },
                )
                DropdownMenuItem(
                    text = { Text("更新全部插件") },
                    onClick = { showFabMenu = false; viewModel.updateAllPlugins() },
                )
                DropdownMenuItem(
                    text = { Text("更新订阅") },
                    onClick = { showFabMenu = false; viewModel.updateSubscriptions() },
                )
            }
        }
    },
) { padding ->
    Column(modifier = Modifier.padding(padding)) {
        // Keep the existing install state and plugin list content unchanged.
    }
}
```

- [ ] **Step 5: Migrate `PluginSortScreen.kt`**

Add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
```

Replace the outer `Scaffold` with:

```kotlin
MusicFreeScreenScaffold(
    title = "插件排序",
    onBack = onBack,
    modifier = modifier,
    actions = {
        TextButton(onClick = {
            viewModel.saveOrder()
            onBack()
        }) {
            Text("完成")
        }
    },
) { padding ->
    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
        verticalArrangement = Arrangement.spacedBy(rpx(8)),
        modifier = Modifier.padding(padding),
    ) {
        // Keep the existing reorderable items unchanged.
    }
}
```

- [ ] **Step 6: Migrate `PluginSubscriptionScreen.kt`**

Add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
```

Replace the outer `Scaffold` with:

```kotlin
MusicFreeScreenScaffold(
    title = "订阅设置",
    onBack = onBack,
    modifier = modifier,
    floatingActionButton = {
        FloatingActionButton(onClick = { openAddDialog() }) {
            Icon(Icons.Default.Add, contentDescription = "添加订阅")
        }
    },
) { padding ->
    // Keep the existing empty state, LazyColumn, dialogs, and clipboard behavior unchanged.
}
```

- [ ] **Step 7: Build settings**

Run:

```bash
./gradlew :feature:settings:assembleDebug
```

Expected: build succeeds.

- [ ] **Step 8: Commit Task 5**

Run:

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsScreen.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/fileselector/FileSelectorLiteScreen.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/PluginSortScreen.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/PluginSubscriptionScreen.kt
git commit -m "feat(settings): use shared screen chrome"
```

Expected: commit succeeds.

---

### Task 6: Migrate Home Feature Ordinary AppBars

**Files:**
- Modify home feature Screens listed in File Structure.

- [ ] **Step 1: Add the shared import to ordinary home Screens**

For each ordinary home Screen file, add:

```kotlin
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
```

For files that still need direct `Icon`, `IconButton`, `TextButton`, or `OutlinedTextField` in actions or title content, keep those imports.

- [ ] **Step 2: Migrate `PlaylistDetailScreen.kt`**

Replace the outer `Column(modifier = Modifier.fillMaxSize())` and its `TopAppBar` with:

```kotlin
MusicFreeScreenScaffold(
    title = playlist?.name ?: "",
    onBack = onBack,
    actions = {
        IconButton(onClick = { onNavigateToMusicListEditorLite(viewModel.playlistId) }) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑歌曲",
                tint = MusicFreeTheme.colors.appBarText,
            )
        }
        IconButton(onClick = { onNavigateToSearchMusicList(viewModel.playlistId) }) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = MusicFreeTheme.colors.appBarText,
            )
        }
    },
) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // Keep the existing empty state and playlist LazyColumn unchanged.
    }
}
```

- [ ] **Step 3: Migrate `HistoryScreen.kt`**

Replace the outer `Column` and `TopAppBar` with:

```kotlin
MusicFreeScreenScaffold(
    title = "播放历史",
    onBack = onBack,
    modifier = modifier
        .fillMaxSize()
        .testTag(FidelityAnchors.Screen.HistoryRoot)
        .semantics { testTagsAsResourceId = true },
    actions = {
        IconButton(onClick = onNavigateToSearchMusicList) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = MusicFreeTheme.colors.appBarText,
            )
        }
        if (history.isNotEmpty()) {
            IconButton(onClick = viewModel::clearHistory) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "清空历史",
                    tint = MusicFreeTheme.colors.appBarText,
                )
            }
        }
    },
) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // Keep the existing empty state and LazyColumn unchanged.
    }
}
```

- [ ] **Step 4: Migrate `SearchMusicListScreen.kt`**

Use the title-content overload because the title area is an `OutlinedTextField`:

```kotlin
MusicFreeScreenScaffold(
    titleContent = {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::updateQuery,
            placeholder = {
                Text(
                    text = "搜索音乐",
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.content,
                )
            },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MusicFreeTheme.colors.textSecondary,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MusicFreeTheme.colors.text,
                unfocusedTextColor = MusicFreeTheme.colors.text,
                cursorColor = MusicFreeTheme.colors.primary,
                focusedBorderColor = MusicFreeTheme.colors.primary,
                unfocusedBorderColor = MusicFreeTheme.colors.divider,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    },
    onBack = onBack,
    modifier = modifier.fillMaxSize(),
) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // Keep the existing search result states unchanged.
    }
}
```

- [ ] **Step 5: Migrate simple title detail/list Screens**

For each file below, replace the outer `Column` and `TopAppBar` with `MusicFreeScreenScaffold`. Keep the statements that currently follow the `TopAppBar` as the scaffold content, wrapped in a `Column` with `Modifier.fillMaxSize().padding(innerPadding)`. Use these exact title expressions:

| File | Title expression |
|---|---|
| `TopListScreen.kt` | `"榜单"` |
| `TopListDetailScreen.kt` | `uiState.title` |
| `RecommendSheetsScreen.kt` | `"推荐歌单"` |
| `PluginSheetDetailScreen.kt` | `uiState.title` |
| `MusicDetailScreen.kt` | `uiState.musicItem?.title ?: "歌曲详情"` |
| `AlbumDetailScreen.kt` | `uiState.title` |
| `ArtistDetailScreen.kt` | `uiState.title` |
| `MusicListEditorLiteScreen.kt` | `uiState.playlistName.ifBlank { "歌单编辑" }` |

For `MusicListEditorLiteScreen.kt`, keep the save action:

```kotlin
actions = {
    if (uiState.hasPendingChanges) {
        TextButton(onClick = viewModel::saveChanges) {
            Text("保存", color = MusicFreeTheme.colors.appBarText)
        }
    }
}
```

- [ ] **Step 6: Build home**

Run:

```bash
./gradlew :feature:home:assembleDebug
```

Expected: build succeeds.

- [ ] **Step 7: Commit Task 6**

Run:

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/history/HistoryScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListDetailScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/pluginsheet/PluginSheetDetailScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/musicdetail/MusicDetailScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/AlbumDetailScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/artistdetail/ArtistDetailScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/musiclisteditor/MusicListEditorLiteScreen.kt
git commit -m "feat(home): use shared screen chrome"
```

Expected: commit succeeds.

---

### Task 7: Final Verification

**Files:**
- No new source files.

- [ ] **Step 1: Scan for forbidden ordinary AppBar patterns**

Run:

```bash
rg -n "TopAppBarDefaults\\.topAppBarColors|containerColor = MusicFreeTheme\\.colors\\.appBar|windowInsetsTopHeight\\(WindowInsets\\.statusBars\\)|tween\\(250\\)" app core feature -g '*.kt'
```

Expected:

- No `tween(250)` in `AppNavHost`.
- No ordinary AppBar `TopAppBarDefaults.topAppBarColors(containerColor = MusicFreeTheme.colors.appBar)` in migrated Screens.
- `SearchScreen` uses `MusicFreeStatusBarChrome(color = colors.appBar)`.
- Special pages may still use custom top chrome if they do not duplicate ordinary AppBar logic.

- [ ] **Step 2: Run unit tests**

Run:

```bash
./gradlew test
```

Expected: all unit tests pass.

- [ ] **Step 3: Build app**

Run:

```bash
./gradlew :app:build
```

Expected: build succeeds.

- [ ] **Step 4: Runtime smoke check on device or emulator**

Run:

```bash
./gradlew :app:installDebug
adb shell am start -n com.hank.musicfree/.MainActivity
```

Expected:

- Home content starts below the status bar.
- Search status bar area is appBar orange.
- Settings and plugin pages show appBar color behind the status bar.
- Player remains full-screen immersive.
- MiniPlayer remains visible on non-player pages.

- [ ] **Step 5: Commit verification adjustments**

If verification required code fixes, commit only those fixes:

```bash
git add AGENTS.md docs app core feature
git commit -m "fix(ui): polish shared screen chrome migration"
```

Expected: create this commit only when files changed after verification. If no files changed, skip this step.
