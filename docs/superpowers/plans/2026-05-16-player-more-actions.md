# Player More Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐播放详情页更多面板的 RN 核心歌曲动作：下一首播放、加入歌单、下载去重和清除当前歌曲插件媒体缓存。

**Architecture:** 在 `feature/player-ui` 的现有底部面板补 UI 行并接入 `PlayerViewModel`。队列去重放在 `:player` 的 `PlayQueue.addNext`，缓存删除放在 `:data` 的 `MediaCacheRepository.deleteItem`，避免 UI 层复制底层规则。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, StateFlow, Room repository, Gradle Debug unit tests.

---

### Task 1: 队列 addNext RN 去重语义

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/queue/PlayQueue.kt`
- Modify: `player/src/test/java/com/zili/android/musicfreeandroid/player/queue/PlayQueueTest.kt`

- [ ] 新增测试：队列中已存在目标歌曲时，`addNext(target)` 先移除旧位置再插入当前歌曲后一位，队列中只保留一份。
- [ ] 修改 `PlayQueue.addNext`：按 `id/platform` 匹配旧项，保留当前项索引语义，目标等于当前项时不复制当前项。
- [ ] 运行 `./gradlew :player:testDebugUnitTest --tests '*PlayQueueTest' --no-daemon`。

### Task 2: 单曲媒体缓存删除 API

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/MediaCacheRepository.kt`
- Modify: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/MediaCacheRepositoryDeleteTest.kt`

- [ ] 新增 `MediaCacheRepository.deleteItem(platform: String, id: String)`，内部调用 `dao.delete(platform, id)` 并 `memory.remove(memoryKey(platform, id))`。
- [ ] 增加结构化日志 `delete_media_cache_item`，字段包含 `platform`、`itemId`、`result`、`durationMs`，失败时 `MfLog.error`。
- [ ] 新增测试：多音质缓存行被 `deleteItem` 整行删除，后续 `get` 返回 null。
- [ ] 运行 `./gradlew :data:testDebugUnitTest --tests '*MediaCacheRepositoryDeleteTest' --no-daemon`。

### Task 3: PlayerViewModel 业务动作

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelTest.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelQueueTest.kt`

- [ ] 注入 `MediaCacheRepository`，同步所有测试 fixture。
- [ ] 新增 `playCurrentNext()`，无当前歌曲时记录 skipped；有当前歌曲时调用 `playerController.addNextInQueue(item)` 并记录 `player_add_next`。
- [ ] 修改 `downloadCurrent(quality)` 返回 `Boolean`；已下载或已有下载任务时记录 skipped 并返回 false；成功调用 `downloader.enqueue` 后返回 true。
- [ ] 新增 `clearCurrentPluginCache()`，无当前歌曲 skipped；有当前歌曲时调用 `mediaCacheRepository.deleteItem(item.platform, item.id)`，成功/失败分别记录日志和错误事件。
- [ ] 补 ViewModel 单测：下一首调用 controller、已下载不 enqueue、已有任务不 enqueue、清缓存调用 repository。
- [ ] 运行 `./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerViewModelTest' --tests '*PlayerViewModelQueueTest' --no-daemon`。

### Task 4: 更多面板 UI 与 PlayerScreen 接线

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/more/PlayerMoreOptionsSheet.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/more/PlayerMoreOptionsSheetTest.kt`

- [ ] 给 sheet 增加参数：`isDownloaded`、`onPlayNext`、`onAddToPlaylist`、`onDownload`、`onClearPluginCache`。
- [ ] 在作者/专辑后、歌词类动作前插入四行：`下一首播放`、`加入歌单`、`下载` 或禁用的 `已下载`、`清除插件缓存(播放异常时使用)`。
- [ ] `PlayerScreen` 点击下一首/加入歌单/清缓存后关闭更多面板并 toast；下载行关闭更多面板并打开现有下载音质 sheet。
- [ ] 下载音质确认后仅当 `viewModel.downloadCurrent(quality)` 返回 true 时 toast “已加入下载队列”，否则 toast “歌曲已在下载队列或已下载”。
- [ ] 更新 Compose 测试覆盖新增文案、禁用已下载行、回调。
- [ ] 运行 `./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerMoreOptionsSheetTest' --no-daemon`。

### Task 5: 集成验证与合并

**Files:**
- Verify only.

- [ ] 运行 `./gradlew :feature:player-ui:testDebugUnitTest --no-daemon`。
- [ ] 运行 `./gradlew :player:testDebugUnitTest --no-daemon`。
- [ ] 运行 `./gradlew :data:testDebugUnitTest --no-daemon`。
- [ ] 运行 `bash scripts/dev-harness/check.sh`。
- [ ] 运行 `./gradlew :app:assembleDebug --no-daemon`。
- [ ] 在 worktree 做代码 review，确认未改 release 签名/默认插件策略。
- [ ] 回到主工作区，`git merge --squash feat/music-detail-more-actions`，提交 `feat(player): 补齐播放详情更多动作`。
