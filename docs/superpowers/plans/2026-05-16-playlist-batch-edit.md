# 歌单批量修改 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让歌单详情页按 RN 原版提供“批量编辑”入口，复用现有 `MusicListEditorLite` 批量修改能力。

**Architecture:** `PlaylistDetailScreen` 负责入口展示和导航回调；`MusicListEditorLiteRoute` / `MusicListEditorLiteViewModel` 继续承载批量选择、staged 删除、保存、添加到歌单、下载和下一首播放。批量添加已有歌单统一走 `PlaylistRepository.addMusicsToPlaylist()`。新增测试验证入口可达，既有 ViewModel 测试守住批量行为。

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose typed route, Hilt ViewModel, Gradle debug unit tests.

---

## File Structure

- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
  - 在 AppBar 更多菜单中增加“批量编辑”项，点击调用既有 `onNavigateToMusicListEditorLite(playlist.id)`。
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreenTest.kt`
  - 用 fake ViewModel 或 injectable state 验证菜单项显示与导航回调。
- Modify if needed: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
  - 若当前 Composable 不便测试，抽出一个内部 `PlaylistDetailContent` / `PlaylistDetailRouteContent`，保持 production 行为不变。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`
  - 将已有歌单批量添加从逐首 `addMusicToPlaylist()` 改为一次 `addMusicsToPlaylist()`。
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModelTest.kt`
  - 更新 verify，确保目标歌单批量添加走事务化 API。

### Task 1: 写入口测试

**Files:**
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreenTest.kt`
- Modify if needed: `PlaylistDetailScreen.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `playlist detail menu opens batch editor for current playlist`() {
    var targetPlaylistId: String? = null

    composeRule.setContent {
        PlaylistDetailContent(
            state = PlaylistDetailUiState(
                playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                musics = listOf(track("1")),
                isLoading = false,
            ),
            sheetState = AddToPlaylistSheetState(),
            allPlaylists = emptyList(),
            favoriteResolver = { flowOf(false) },
            onBack = {},
            onNavigateToSearchMusicList = {},
            onNavigateToMusicListEditorLite = { targetPlaylistId = it },
            actions = PlaylistDetailActions.Noop,
        )
    }

    composeRule.onNodeWithContentDescription("更多").performClick()
    composeRule.onNodeWithText("批量编辑").assertIsDisplayed().performClick()

    assertEquals("playlist-1", targetPlaylistId)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*PlaylistDetailScreenTest' --no-daemon
```

Expected: FAIL，因为 `PlaylistDetailContent` / “批量编辑”入口尚不存在。

### Task 2: 实现菜单入口

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`

- [ ] **Step 1: 抽出可测试内容层**

将当前 `PlaylistDetailScreen` 的 body 保持语义不变，抽出内部 Composable，参数包含 state、sheetState、allPlaylists、favoriteResolver、导航回调与动作接口。生产入口仍使用 `hiltViewModel()` 组装这些参数。

- [ ] **Step 2: 新增菜单项**

在“编辑信息”和“排序”之间加入：

```kotlin
DropdownMenuItem(
    text = { Text("批量编辑") },
    onClick = {
        menuExpanded = false
        onNavigateToMusicListEditorLite(playlist.id)
    },
)
```

不要加 `playlist.isDefault` 限制。

- [ ] **Step 3: 跑入口测试确认通过**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*PlaylistDetailScreenTest' --no-daemon
```

Expected: PASS。

### Task 3: 批量添加已有歌单走事务 API

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModelTest.kt`

- [ ] **Step 1: 更新失败测试**

把 `addSelectedToPlaylist uses source order and excludes current playlist from targets` 的 verify 改为：

```kotlin
verify(playlistRepository).addMusicsToPlaylist("playlist-2", items)
verify(playlistRepository, never()).addMusicToPlaylist("playlist-2", items[0])
verify(playlistRepository, never()).addMusicToPlaylist("playlist-2", items[1])
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*MusicListEditorLiteViewModelTest' --no-daemon
```

Expected: FAIL，因为 production 仍逐首调用 `addMusicToPlaylist()`。

- [ ] **Step 3: 修改 production**

将：

```kotlin
selectedItems.forEach { item ->
    playlistRepository.addMusicToPlaylist(targetPlaylistId, item)
}
```

改为：

```kotlin
playlistRepository.addMusicsToPlaylist(targetPlaylistId, selectedItems)
```

- [ ] **Step 4: 跑 ViewModel 测试确认通过**

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*MusicListEditorLiteViewModelTest' --no-daemon
```

Expected: PASS，证明 staged 删除、保存、批量添加、下载和本地来源行为不回退。

### Task 4: 收尾验证

**Files:**
- No additional changes expected.

- [ ] **Step 1: 跑 feature home 单测**

```bash
./gradlew :feature:home:testDebugUnitTest --no-daemon
```

- [ ] **Step 2: 跑 dev harness grep**

```bash
python3 scripts/dev-harness/grep-check.py
```

- [ ] **Step 3: 跑 dev harness check**

```bash
bash scripts/dev-harness/check.sh
```

- [ ] **Step 4: 跑 Debug 构建**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: all commands exit 0. Release 构建不是本任务验收项。
