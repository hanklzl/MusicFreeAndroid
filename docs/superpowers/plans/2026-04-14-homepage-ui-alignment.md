# 首页 UI 对齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对齐 Android 首页 UI 与 RN 原版，修复 6 项首页布局问题并重构 MiniPlayer。

**Architecture:** 增量修改现有 Compose 组件，首页修复集中在 `:feature:home` 模块，MiniPlayer 重构在 `:feature:player-ui` 模块。所有改动使用 mock 数据驱动，不涉及真实数据源。

**Tech Stack:** Jetpack Compose, Canvas API (drawArc), pointerInput gesture detection, rpx() responsive sizing

**Spec:** `docs/superpowers/specs/2026-04-14-homepage-ui-alignment-design.md`

**并行化说明:** Tasks 1-4（`:feature:home` 模块）和 Tasks 5-8（`:feature:player-ui` 模块）之间无代码依赖，可由两个 agent 并行执行，在 Task 9 汇合。

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `feature/home/.../component/HomeNavBar.kt` | Modify | #1 搜索栏 weight, #2 间距 |
| `feature/home/.../component/HomeOperations.kt` | Modify | #2 间距 |
| `feature/home/.../sheets/HomeSheetsHeader.kt` | Modify | #3 垂直对齐 |
| `feature/home/.../sheets/HomeSheetUiModel.kt` | Modify | 新增 isDefault 字段, #6 副标题格式 |
| `feature/home/.../sheets/HomeSheetsList.kt` | Modify | #4 删除图标, #5 心形遮罩 |
| `feature/home/.../HomeMockVisualFactory.kt` | Modify | mock 数据补充 isDefault + 副标题格式 |
| `feature/home/src/main/res/drawable/ic_home_trash_outline.xml` | Create | 删除图标资源 |
| `feature/home/src/main/res/drawable/ic_home_heart.xml` | Create | 心形遮罩图标资源 |
| `feature/home/src/test/.../HomeSheetUiModelTest.kt` | Modify | 副标题格式断言更新 |
| `feature/home/src/test/.../HomeSheetsViewModelTest.kt` | Modify | 副标题格式断言更新 |
| `feature/home/src/test/.../HomeSheetsSectionTest.kt` | Modify | 副标题格式断言更新 |
| `feature/player-ui/.../component/MiniPlayerUiModel.kt` | Modify | 模型重构 |
| `feature/player-ui/.../component/MiniPlayer.kt` | Modify | toMiniPlayerUiModel() 映射更新 |
| `feature/player-ui/.../component/MiniPlayerContent.kt` | Modify | 布局重构 |
| `feature/player-ui/.../component/CircularPlayButton.kt` | Create | 圆形进度环组件 |
| `feature/player-ui/.../component/MiniPlayerMockFactory.kt` | Create | MiniPlayer mock 数据工厂 |
| `feature/player-ui/.../component/MiniPlayerContentTest.kt` | Modify | 测试同步更新 |
| `app/.../MainActivity.kt` | Modify | 使用新 mock factory |

**路径前缀约定（下文省略）：**
- `home/` = `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/`
- `home-res/` = `feature/home/src/main/res/drawable/`
- `home-test/` = `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/`
- `player-ui/` = `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/`
- `player-ui-test/` = `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/`

---

## Task 1: 搜索栏宽度 + NavBar/操作卡片间距 (#1, #2)

**Files:**
- Modify: `home/component/HomeNavBar.kt`
- Modify: `home/component/HomeOperations.kt`

**RN 参考:**
- `../MusicFree/src/pages/home/components/navBar.tsx` — search bar: `flex: 1`, `marginHorizontal: rpx(24)`
- `../MusicFree/src/pages/home/components/homeBody/operations.tsx` — container: `marginVertical: rpx(32)`, `paddingHorizontal: rpx(24)`, item spacing: `marginLeft: rpx(24)`

- [ ] **Step 1: 修复 HomeNavBar 搜索栏宽度**

在 `HomeNavBar.kt` 中找到搜索栏的 `Modifier`，将 `.fillMaxWidth()` **替换**为 `.weight(1f)`。搜索栏是 Row 内的子元素，`weight(1f)` 才能正确表达"占 menu 按钮之外的剩余空间"（`fillMaxWidth()` 在 Row 中语义为占满整行，可能越界）。

- [ ] **Step 2: 调整 HomeOperations 间距**

在 `HomeOperations.kt` 中：
- 将 vertical padding 从 `rpx(24)` 改为 `rpx(32)` 以对齐 RN 的 `marginVertical: rpx(32)`
- 将 item spacing 从 `Arrangement.spacedBy(rpx(16))` 改为 `Arrangement.spacedBy(rpx(24))` 以对齐 RN 的 `marginLeft: rpx(24)`

- [ ] **Step 3: 构建验证**

```bash
./gradlew :feature:home:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add feature/home/
git commit -m "fix(home): align search bar width and operations spacing with RN"
```

---

## Task 2: 歌单 Tab header 垂直对齐 (#3)

**Files:**
- Modify: `home/sheets/HomeSheetsHeader.kt`

**RN 参考:**
- `../MusicFree/src/pages/home/components/homeBody/sheets.tsx` — sub-title container: `flexDirection: "row"`, vertical center alignment

- [ ] **Step 1: 修复 HomeSheetsHeader 垂直对齐**

在 `HomeSheetsHeader.kt` 中：
- 外层 Row 当前 `verticalAlignment = Alignment.Top`，改为 `Alignment.CenterVertically`
- 注意：内层左侧 Row 使用 `verticalAlignment = Alignment.Bottom`（用于 tab 下划线定位），右侧 action buttons 有 `padding(top = rpx(3))`。改完外层后需要视觉验证这些内层对齐是否仍然正确，可能需要同步移除 `padding(top = rpx(3))` 来达到真正的垂直居中效果。

- [ ] **Step 2: 构建验证**

```bash
./gradlew :feature:home:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/home/
git commit -m "fix(home): vertically center sheet tab header with action buttons"
```

---

## Task 3: 歌单数据模型扩展 + 副标题格式 + mock 数据 (#6, 为 #4/#5 做准备)

**Files:**
- Modify: `home/sheets/HomeSheetUiModel.kt` — 新增 isDefault 字段 + 修改 fromPlaylist() 副标题格式
- Modify: `home/HomeMockVisualFactory.kt` — mock 数据更新
- Modify: `home-test/sheets/HomeSheetUiModelTest.kt` — 副标题断言更新
- Modify: `home-test/sheets/HomeSheetsViewModelTest.kt` — 副标题断言更新
- Modify: `home-test/sheets/HomeSheetsSectionTest.kt` — 副标题断言更新

- [ ] **Step 1: 在 HomeSheetUiModel 中新增 isDefault 字段**

在 `HomeSheetUiModel` data class 中新增 `val isDefault: Boolean = false` 字段。更新工厂方法：
- `fromPlaylist()` 需要新增 `isDefault` 参数，默认 `false`
- `fromStarredSheet()` 始终 `isDefault = false`

- [ ] **Step 2: 修改 fromPlaylist() 副标题格式**

在 `HomeSheetUiModel.fromPlaylist()` 中，将 `subtitle = "${musicCount} 首歌曲"` 改为 `subtitle = "${musicCount}首"`。

注意：本轮仅修改 `HomeSheetUiModel.fromPlaylist()` 和 mock 数据中的格式。其他文件（`PlaylistDetailScreen.kt`、`MusicListEditorLiteScreen.kt`）中如有类似格式，留待后续统一处理。

- [ ] **Step 3: 更新 mock 数据**

在 `HomeMockVisualFactory.kt` 中：
- 将第一条 mine row 改名为"我喜欢"，设置 `isDefault = true`
- 副标题格式统一改为 `"N首"` 格式

- [ ] **Step 4: 更新测试断言**

更新以下测试文件中的副标题断言：
- `HomeSheetUiModelTest.kt` — `assertEquals("12 首歌曲", ...)` → `assertEquals("12首", ...)`
- `HomeSheetsViewModelTest.kt` — 相关断言同步修改
- `HomeSheetsSectionTest.kt` — 相关断言同步修改

- [ ] **Step 5: 构建 + 测试验证**

```bash
./gradlew :feature:home:assembleDebug && ./gradlew :feature:home:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, ALL TESTS PASSED

- [ ] **Step 6: Commit**

```bash
git add feature/home/
git commit -m "feat(home): add isDefault field, fix subtitle format, and update mock data"
```

---

## Task 4: 歌单列表 — 删除图标 + 心形遮罩 (#4, #5)

**Files:**
- Modify: `home/sheets/HomeSheetsList.kt`
- Create: `home-res/ic_home_trash_outline.xml`
- Create: `home-res/ic_home_heart.xml`

**RN 参考:**
- `../MusicFree/src/pages/home/components/homeBody/sheets.tsx` — ListItem with trash-outline icon, heart maskIcon on default sheet

- [ ] **Step 1: 添加图标资源**

从 RN 项目或 Material Icons 获取 trash-outline 和 heart 图标，创建 vector drawable XML 文件。命名为 `ic_home_trash_outline.xml` 和 `ic_home_heart.xml`。图标 viewport 建议 24x24。

- [ ] **Step 2: 修改 HomeSheetRow 添加删除图标**

在 `HomeSheetsList.kt` 的 `HomeSheetRow` composable 中：
- 在 Row 末尾添加 trash-outline `Icon`
- 条件：`if (!uiModel.isDefault)` 才显示删除图标
- 图标不响应点击，仅展示
- 图标颜色使用 `MusicFreeTheme.colors.textSecondary`
- 图标大小建议 `rpx(42)`（对齐 RN 的 normal icon size）

- [ ] **Step 3: 添加心形遮罩**

在 `HomeSheetRow` 的封面图片区域：
- 条件：`if (uiModel.isDefault)` 才显示心形 overlay
- 在封面占位图（当前是 music note icon on gray background）上叠加 `Box` + `Icon` (heart)
- 心形图标居中在封面区域底部，使用较小尺寸

- [ ] **Step 4: 构建验证**

```bash
./gradlew :feature:home:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add feature/home/
git commit -m "feat(home): add delete icon and heart overlay in sheet list"
```

---

## Task 5: MiniPlayer 全量重构（模型 + 圆形按钮 + 布局，原子提交）

> 本 Task 合并了模型重构、CircularPlayButton 和布局重构，确保 `:feature:player-ui` 模块在任何中间状态都不会编译失败。

**Files:**
- Modify: `player-ui/MiniPlayerUiModel.kt`
- Modify: `player-ui/MiniPlayer.kt`
- Create: `player-ui/MiniPlayerMockFactory.kt`
- Create: `player-ui/CircularPlayButton.kt`
- Modify: `player-ui/MiniPlayerContent.kt`

**RN 参考:**
- `../MusicFree/src/components/musicBar/index.tsx` — height `rpx(132)`, CircularPlayBtn radius `rpx(36)`, active stroke `rpx(4)`, inactive stroke `rpx(2)`, playlist icon `rpx(56)`, spacing `rpx(36)`
- `../MusicFree/src/components/musicBar/musicInfo.tsx` — artwork `rpx(96)` circle, title content fontSize + artist description fontSize 60% alpha

- [ ] **Step 1: 重构 MiniPlayerUiModel**

替换当前 5 字段模型为新模型：

```kotlin
data class MiniPlayerUiModel(
    val coverUri: String?,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val progress: Float,
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val prevTitle: String?,
    val nextTitle: String?,
)
```

变更说明：
- `subtitle` → 拆分为 `title` + `artist`
- `showQueueButton` → 移除，队列按钮改为始终显示
- 新增 `progress`, `hasPrev`, `hasNext`, `prevTitle`, `nextTitle`

- [ ] **Step 2: 更新 toMiniPlayerUiModel() 映射**

在 `MiniPlayer.kt` 中更新 `PlayerState.toMiniPlayerUiModel()` 扩展函数：
- `title` = `currentItem?.title ?: ""`
- `artist` = `currentItem?.artist ?: ""`
- `progress` = if (duration > 0) `position.toFloat() / duration` else `0f`
- `hasPrev` / `hasNext` 暂时固定 `true`
- `prevTitle` / `nextTitle` 暂时 `null`

同时更新 `MiniPlayer` composable：移除 `onNavigateToQueue` 的 nullable 参数模式，`onOpenQueue` 始终传入 `MiniPlayerContent`。

- [ ] **Step 3: 创建 MiniPlayerMockFactory**

```kotlin
object MiniPlayerMockFactory {
    private val mockSongs = listOf(
        Triple("In the End", "Linkin Park", null as String?),
        Triple("半兽人", "周杰伦", null),
        Triple("Bohemian Rhapsody", "Queen", null),
    )

    fun buildMockUiModel(
        currentIndex: Int = 0,
        isPlaying: Boolean = true,
    ): MiniPlayerUiModel {
        val current = mockSongs[currentIndex % mockSongs.size]
        val prev = mockSongs[(currentIndex - 1 + mockSongs.size) % mockSongs.size]
        val next = mockSongs[(currentIndex + 1) % mockSongs.size]
        return MiniPlayerUiModel(
            coverUri = current.third,
            title = current.first,
            artist = current.second,
            isPlaying = isPlaying,
            progress = 0.35f,
            hasPrev = true,
            hasNext = true,
            prevTitle = "${prev.first} - ${prev.second}",
            nextTitle = "${next.first} - ${next.second}",
        )
    }
}
```

- [ ] **Step 4: 创建 CircularPlayButton**

创建 `CircularPlayButton.kt`，使用 `Icons.Default.Pause` / `Icons.Default.PlayArrow`（与现有代码一致，不需要额外 drawable 资源）：

```kotlin
@Composable
fun CircularPlayButton(
    isPlaying: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diameter = rpx(72)
    val activeStrokeWidth = rpx(4)
    val inactiveStrokeWidth = rpx(2)
    val activeColor = MusicFreeTheme.colors.musicBarText
    val inactiveColor = MusicFreeTheme.colors.textSecondary.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .size(diameter)
            .clickable(onClick = onTogglePlayPause),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = inactiveColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = inactiveStrokeWidth.toPx()),
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = activeStrokeWidth.toPx()),
            )
        }
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = MusicFreeTheme.colors.musicBarText,
        )
    }
}
```

- [ ] **Step 5: 重构 MiniPlayerContent 布局**

重写 `MiniPlayerContent.kt`，新参数签名：

```kotlin
@Composable
fun MiniPlayerContent(
    uiModel: MiniPlayerUiModel,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenQueue: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    modifier: Modifier = Modifier,
)
```

布局结构：

```
Row (height = rpx(132), bg = musicBar, verticalAlignment = CenterVertically)
├── MusicInfo area (Modifier.weight(1f), clickable → onOpenPlayer)
│   ├── 封面占位 Box (rpx(96) circle, marginLeft = rpx(24))
│   ├── Spacer(rpx(24))
│   └── Text Row (single line, ellipsis)
│       ├── Text(title, fontSize = content, color = musicBarText)
│       ├── Text(" - ", color = musicBarText 60% alpha)
│       └── Text(artist, fontSize = description, color = musicBarText 60% alpha)
│
├── CircularPlayButton (rpx(72))
│
└── Icon (playlist, size = rpx(56), marginLeft = rpx(36), marginRight = rpx(24))
```

MusicInfo 区域暂时只处理点击，滑动手势在 Task 6 添加。

注意：本轮不实现视觉 carousel 动画（RN 的 3 帧滑动过渡效果），仅实现滑动触发切歌。carousel 动画作为后续优化。

- [ ] **Step 6: 构建验证**

```bash
./gradlew :feature:player-ui:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add feature/player-ui/
git commit -m "feat(player-ui): redesign MiniPlayer with new model, circular progress ring, and RN-aligned layout"
```

---

## Task 6: MiniPlayer 滑动手势

**Files:**
- Modify: `player-ui/MiniPlayerContent.kt`

**RN 参考:**
- `../MusicFree/src/components/musicBar/musicInfo.tsx` — pan + tap race gesture, threshold 30% width or 1500px/s velocity

- [ ] **Step 1: 添加滑动手势到 MusicInfo 区域**

在 MiniPlayerContent 的 MusicInfo 区域替换 `clickable` 为 `pointerInput`：

```kotlin
val infoWidth = remember { mutableIntStateOf(0) }

Modifier
    .onSizeChanged { infoWidth.intValue = it.width }
    .pointerInput(Unit) {
        detectTapGestures(onTap = { onOpenPlayer() })
    }
    .pointerInput(Unit) {
        var totalDrag = 0f
        val width = size.width.toFloat()  // size 来自 PointerInputScope
        detectHorizontalDragGestures(
            onDragEnd = {
                if (abs(totalDrag) > width * 0.3f) {
                    if (totalDrag < 0) onSkipNext() else onSkipPrev()
                }
                totalDrag = 0f
            },
            onDragCancel = { totalDrag = 0f },
            onHorizontalDrag = { _, dragAmount ->
                totalDrag += dragAmount
            },
        )
    }
```

需要 `import kotlin.math.abs`。

注意：`size` 是 `PointerInputScope` 的属性，在 `detectHorizontalDragGestures` 调用之前捕获。Compose 中多个 `pointerInput` modifier 共存，系统自动处理手势竞争。

velocity 判断（> 1500px/s）可后续通过 `VelocityTracker` 优化，初始版本先用位移阈值 30%。

- [ ] **Step 2: 构建验证**

```bash
./gradlew :feature:player-ui:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/player-ui/
git commit -m "feat(player-ui): add swipe gesture for skip next/prev in MiniPlayer"
```

---

## Task 7: 测试同步更新

**Files:**
- Modify: `player-ui-test/MiniPlayerContentTest.kt`

- [ ] **Step 1: 更新测试用例**

根据新的 `MiniPlayerUiModel` 结构和 `MiniPlayerContent` 参数签名，更新所有测试：
- 更新 test fixture 创建代码，使用新字段（`artist`, `progress`, `hasPrev`, `hasNext`, `prevTitle`, `nextTitle`）
- 更新回调验证，新增 `onSkipNext` 和 `onSkipPrev` 的 mock 回调
- 移除 `showQueueButton` 相关测试（队列按钮现在始终显示）
- 保留核心交互测试：点击打开播放器、点击播放/暂停、点击队列

- [ ] **Step 2: 运行测试**

```bash
./gradlew :feature:player-ui:testDebugUnitTest
```
Expected: ALL TESTS PASSED

- [ ] **Step 3: Commit**

```bash
git add feature/player-ui/
git commit -m "test(player-ui): update MiniPlayerContent tests for new model and callbacks"
```

---

## Task 8: MainActivity mock 数据集成

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`

- [ ] **Step 1: 替换 MainActivity 中的 mock 数据**

将 MainActivity 中手工构建 `MiniPlayerUiModel` 的代码替换为 `MiniPlayerMockFactory.buildMockUiModel()`。

更新 `MiniPlayerContent` 调用，传入新的回调参数：
- `onSkipNext` / `onSkipPrev` 在 mock 场景下可以切换 `currentIndex` state

```kotlin
var mockSongIndex by remember { mutableIntStateOf(0) }
val mockMiniPlayer = MiniPlayerMockFactory.buildMockUiModel(
    currentIndex = mockSongIndex,
    isPlaying = isHomeMockPlaying,
)

MiniPlayerContent(
    uiModel = mockMiniPlayer,
    onOpenPlayer = {},
    onTogglePlayPause = { isHomeMockPlaying = !isHomeMockPlaying },
    onOpenQueue = {},
    onSkipNext = { mockSongIndex++ },
    onSkipPrev = { mockSongIndex-- },
)
```

- [ ] **Step 2: 全量构建验证**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/
git commit -m "feat(app): use MiniPlayerMockFactory for home mock mini player"
```

---

## Task 9: 端到端验证

- [ ] **Step 1: 全量构建 + 测试**

```bash
./gradlew assembleDebug && ./gradlew :feature:player-ui:testDebugUnitTest && ./gradlew :feature:home:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, ALL TESTS PASSED

- [ ] **Step 2: 安装到模拟器/设备并截图对比**

```bash
./gradlew installDebug
```

验证清单：
- [ ] 搜索栏占满 NavBar 剩余宽度
- [ ] NavBar 与操作卡片间距适中（对齐 RN）
- [ ] 歌单 Tab 文字与右侧图标垂直居中
- [ ] 非默认歌单右侧显示垃圾桶图标
- [ ] "我喜欢" 显示心形遮罩，无垃圾桶图标
- [ ] 歌单副标题格式为 "N首"
- [ ] MiniPlayer 高度对齐 rpx(132)
- [ ] MiniPlayer 显示圆形进度环
- [ ] MiniPlayer 歌曲信息为单行双色（歌名 + 艺术家）
- [ ] MiniPlayer 左右滑动可触发切歌回调
- [ ] MiniPlayer 点击播放/暂停正常
