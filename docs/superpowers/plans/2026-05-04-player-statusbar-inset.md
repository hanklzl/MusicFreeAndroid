# 播放器状态栏避让实现计划

> **面向自动化执行助手：** 必须使用子技能：用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪。

**目标：** 保持播放详情页的沉浸式背景视觉，同时让播放器控制区和标题内容避开 Android 状态栏。

**架构：** `PlayerScreen` 继续负责播放器专属沉浸式 chrome。背景层保持全屏绘制，新增一个小型内部 `PlayerContentLayer` composable，只对交互内容应用顶部状态栏 inset。用一个聚焦的 Robolectric Compose 测试验证：注入顶部 inset 后内容会下移，并且不会额外增加横向 inset。

**技术栈：** Kotlin、Jetpack Compose Foundation window insets、Robolectric、Compose UI test、Gradle Android unit tests。

**执行目录：** 在仓库根目录下的 `.worktrees/fix-player-statusbar-inset` worktree 中执行。

**前置上下文：**
- 设计输入：`docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md`
- UI Harness 当前规范：`docs/ui-harness/screen-chrome-rules.md`
- 播放器页面代码：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- App 顶层 safe inset 代码：`app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
- RN 对照实现：`../MusicFree/src/pages/musicDetail/index.tsx` 和 `../MusicFree/src/pages/musicDetail/components/navBar.tsx`

**已完成准备：**
- 已创建 worktree：`.worktrees/fix-player-statusbar-inset`
- 已创建分支：`fix-player-statusbar-inset`
- 已提交设计文档：`docs: specify player status bar inset behavior`
- 已提交本实现计划：`docs: plan player status bar inset fix`
- 变更前基线已验证：`./gradlew :feature:player-ui:testDebugUnitTest` 为 `BUILD SUCCESSFUL`

**新 session 执行入口：**
1. 进入 `.worktrees/fix-player-statusbar-inset`。
2. 读取 `docs/DOCS_STATUS.md`、`AGENTS.md`、`docs/ui-harness/screen-chrome-rules.md` 和本计划。
3. 运行 `git status --short`，确认除执行者即将修改的文件外没有未说明变更。
4. 从“任务 1”开始逐项执行，不跳过失败测试步骤。

---

### 任务 1：新增失败的 inset 测试

**文件：**
- 新建：`feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`

- [ ] **步骤 1：编写失败测试**

创建 `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerScreenInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `player content layer applies top status bar inset only`() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(200.dp)) {
                    PlayerContentLayer(
                        statusBarInsets = WindowInsets(top = 24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .testTag(FIRST_CONTENT_TAG),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(FIRST_CONTENT_TAG)
            .assertTopPositionInRootIsEqualTo(24.dp)
        composeRule.onNodeWithTag(FIRST_CONTENT_TAG)
            .assertLeftPositionInRootIsEqualTo(0.dp)
    }

    private companion object {
        const val FIRST_CONTENT_TAG = "player-content-first-child"
    }
}
```

- [ ] **步骤 2：运行聚焦测试并确认失败**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.playerui.PlayerScreenInsetsTest"
```

预期：`FAIL`，原因是 `PlayerContentLayer` 尚未定义。

### 任务 2：给播放器内容层应用顶部 inset

**文件：**
- 修改：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- 测试：`feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`

- [ ] **步骤 0：确认当前 PlayerScreen 结构**

运行：

```bash
sed -n '1,170p' feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt
```

预期：`PlayerScreen` 的根节点是 `Box(modifier = Modifier.fillMaxSize())`，Layer 1 和 Layer 2 是背景层，Layer 3 是承载 `PlayerNavBar`、封面、操作栏、进度条和播放控制的 `Column`。

- [ ] **步骤 1：增加 Compose window inset 所需 imports**

在 `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt` 中，把以下 imports 加到现有 `androidx.compose.foundation.layout` imports 旁边：

```kotlin
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
```

- [ ] **步骤 2：用 PlayerContentLayer 替换内容 Column**

在 `PlayerScreen` 中，将当前 Layer 3 内容容器：

```kotlin
        // Layer 3: 内容
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
```

替换为：

```kotlin
        // Layer 3: 内容
        PlayerContentLayer {
```

保留现有内容 block 的结束大括号。不要移动背景层，也不要给根 `Box` 应用 inset。

- [ ] **步骤 3：新增内部 PlayerContentLayer composable**

在 `PlayerScreen.kt` 中，将以下 composable 添加到 `PlayerScreen` 之后、`PlayerNavBar` 之前：

```kotlin
@Composable
internal fun PlayerContentLayer(
    modifier: Modifier = Modifier,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(statusBarInsets.only(WindowInsetsSides.Top)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
```

- [ ] **步骤 4：运行聚焦测试并确认通过**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.playerui.PlayerScreenInsetsTest"
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：运行完整 player-ui 单元测试**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

### 任务 3：构建与运行态验收

**文件：**
- 校验：`app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
- 校验：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`

- [ ] **步骤 1：确认 MainActivity 顶部 inset 策略未变化**

运行：

```bash
sed -n '70,95p' app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt
```

预期输出仍包含：

```kotlin
contentWindowInsets = WindowInsets.safeDrawing.only(
    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
),
```

- [ ] **步骤 2：确认背景层仍保持全屏**

运行：

```bash
rg -n "Layer 1|Layer 2|Layer 3|PlayerContentLayer|fillMaxSize|windowInsetsPadding" feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt
```

预期：Layer 1 和 Layer 2 仍使用 `fillMaxSize()`，只有 `PlayerContentLayer` 使用 `windowInsetsPadding`。

- [ ] **步骤 3：构建 app 模块**

运行：

```bash
./gradlew :app:build
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 4：执行设备或模拟器视觉验收**

运行：

```bash
adb devices
```

预期：至少有一行设备状态为 `device`。如果没有连接设备，则记录当前环境无法完成运行态视觉验收。

有设备时，运行：

```bash
./gradlew :app:installDebug
adb shell am start -n com.zili.android.musicfreeandroid.debug/com.zili.android.musicfreeandroid.MainActivity
```

手动验收：

- 从任意可见音乐列表项开始播放。
- 点击 MiniPlayer 打开 `PlayerScreen`。
- 确认模糊背景或黑色播放器背景延伸到状态栏后方。
- 确认返回按钮、标题、平台标签和分享按钮从状态栏图标下方开始。
- 确认 `PlayerScreen` 可见时 MiniPlayer 被隐藏。

### 任务 4：提交实现

**文件：**
- 修改：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- 新建：`feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`

- [ ] **步骤 1：检查 diff**

运行：

```bash
git diff -- feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt
```

预期：diff 只新增 `PlayerContentLayer`、将其应用到 Layer 3 内容层，并新增聚焦测试。

- [ ] **步骤 2：提交实现**

运行：

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt
git commit -m "fix(player-ui): keep player content below status bar"
```

预期：在 `fix-player-statusbar-inset` 分支上生成一个新的实现提交。

### 任务 5：完成记录

**文件：**
- 校验：`docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md`
- 校验：`docs/superpowers/plans/2026-05-04-player-statusbar-inset.md`

- [ ] **步骤 1：确认文档仍为中文并且没有占位符**

运行：

```bash
rg -n "T[B]D|TO[D]O|FIX[M]E|待[定]|待[补]" docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md docs/superpowers/plans/2026-05-04-player-statusbar-inset.md
rg -n "/User[s]/" docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md docs/superpowers/plans/2026-05-04-player-statusbar-inset.md
```

预期：两条命令都不输出结果。

- [ ] **步骤 2：最终汇报需要包含的证据**

最终回复必须说明：

- 修改了哪些文件。
- `PlayerContentLayer` 如何保持“背景全屏、内容避让状态栏”。
- 聚焦测试、完整 player-ui 单元测试、`:app:build` 的执行结果。
- 是否完成设备/模拟器运行态验收；如果没有设备，明确说明未完成的原因。
