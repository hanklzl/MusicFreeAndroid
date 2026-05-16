---
title: 详情页 Header + PlayAllBar RN 对齐 - 执行计划
status: 当前规范（执行计划）
applies_to: feature/home – topListDetail / pluginSheetDetail
authors: AI 自动化（Claude Opus 4.7）
date: 2026-05-15
spec: ../specs/2026-05-15-detail-header-rn-parity-design.md
---

# 执行约束

- Worktree：`.worktrees/feat-detail-header-parity`，分支 `feat-detail-header-parity`，从 `main` 拉
- 子代理：使用 `superpowers:subagent-driven-development` 模式，主代理负责审查 + 集成
- 验证闸门：每个 step 完成后跑相应 Gradle 任务；全部完成后跑 `:app:assembleDebug` + 单测 + emulator install
- 合并：`git merge --squash` 回 `main`，conventional commits 中文 commit
- 不要改 RN 原版仓库 `../MusicFree`

# Step 1 — 抽出共享组件（独立任务）

**目标**：在 `:core:ui` 新增 `PlayAllBar.kt` 与 `MusicSheetPageHeader.kt`，可独立编译。

**输入**：
- 规范 §5.1
- RN 参考：`../MusicFree/src/components/musicSheetPage/components/header.tsx`、`../MusicFree/src/components/base/playAllBar.tsx`

**交付物**：
- `core/src/main/java/com/hank/musicfree/core/ui/PlayAllBar.kt`
- `core/src/main/java/com/hank/musicfree/core/ui/MusicSheetPageHeader.kt`

**约束**：
- 仅做 UI 与 lambda 上抛，不引入业务依赖
- 使用 `rpx(...)` / `MusicFreeTheme.colors.*` / `FontSizes.*` / `IconSizes.normal`
- 使用现有 `R.drawable.ic_play_circle`、`ic_folder_plus`、`ic_heart`、`ic_heart_outline`
- description 区域：内部 `var expanded by remember { mutableStateOf(false) }`，默认 6 行，可点击切换
- 不要给 PlayAllBar 加 default lambda 实现；让调用方显式传入

**验证**：
- `./gradlew :core:compileDebugKotlin :core:lintDebug`
- 可选：补一个 `MusicSheetPageHeaderPreview`（@Preview）用于本地调试
- 单测（如果有 compose-ui-test 基建）：`MusicSheetPageHeaderTest`、`PlayAllBarTest` 验证 starred=null 时不渲染 heart

**独立性**：⭐ 完全独立，可与 Step 2 并行

---

# Step 2 — 行组件提升到 `feature/home/component/`（独立任务）

**目标**：将 `PluginSheetMusicRow` 移动并改名为 `PluginMusicRow`，让 `TopListDetailScreen` 和 `PluginSheetDetailScreen` 复用。

**输入**：
- 现 `feature/home/pluginsheet/PluginSheetDetailScreen.kt` 中的 `PluginSheetMusicRow` + `pluginSheetPlatformTagText` + `pluginSheetDescription`

**交付物**：
- `feature/home/src/main/java/com/hank/musicfree/feature/home/component/PluginMusicRow.kt`（新文件，包含 row + 两个 helper）
- 移除 `PluginSheetDetailScreen.kt` 中旧的 row 与 helpers
- 同时更新 `PluginSheetDetailScreen.kt` 调用点引用新位置

**约束**：
- 保持 row 行为不变（包括 `MusicItemMoreMenu` + `PlatformTag`）
- 公开签名维持 `index, item, isFavorite, onClick, onLongClick, onAction`
- helpers 改为 internal top-level

**验证**：
- `./gradlew :feature:home:compileDebugKotlin`
- 现有 `PluginSheetDetailScreen` 视觉不变（编译期等价）

**独立性**：⭐ 完全独立，可与 Step 1 并行

---

# Step 3 — `PluginSheetDetailViewModel` 增量（依赖：无）

**目标**：补 `playAll` / `showBatchAddToPlaylistSheet` / 改造 `addPendingToPlaylist` / `createPlaylistAndAddPending`。

**输入**：
- 规范 §5.4.1
- 现有 `PlaylistRepository.addMusicsToPlaylist`（已实现）
- 现有 `AddToPlaylistSheetState.batch`（已实现）

**交付物**：
- 修改 `feature/home/src/main/java/com/hank/musicfree/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`

**约束**：
- 改造 `addPendingToPlaylist`：
  - `pendingItems.isEmpty() → return`
  - `pendingItems.size == 1 → addMusicToPlaylist(targetId, item)`（保持现状）
  - `pendingItems.size > 1 → addMusicsToPlaylist(targetId, pendingItems)`（批量）
  - 日志 fields 加 `itemCount`, `added`, `skipped`
- 改造 `createPlaylistAndAddPending`：同上
- `showBatchAddToPlaylistSheet()`：`if (_uiState.value.musicList.isEmpty()) return` else `_sheetState.value = AddToPlaylistSheetState.batch(musicList)`
- `playAll()`：`viewModelScope.launch { playAt(0) }`，日志事件 `plugin_sheet_play_all_*` 走 `runUserAction`

**验证**：
- `./gradlew :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest`
- 如果 `PluginSheetDetailViewModelTest` 存在：补两条用例（参考 §6.2）

**独立性**：⭐ 完全独立，可与 Step 1/2/4 并行

---

# Step 4 — `TopListDetailViewModel` 增量（依赖：无）

**目标**：补与 `PluginSheetDetailViewModel` 同集的接口。

**输入**：
- 规范 §5.4.2
- 现有 `PluginSheetDetailViewModel` 作为参照实现

**交付物**：
- 修改 `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListDetailViewModel.kt`
- Hilt 注入：新增 `PlaylistRepository`

**约束**：
- 暴露：`sheetState`, `allPlaylists`, `isFavoriteFlow(item)`, `toggleFavorite(item)`, `showAddToPlaylistSheet(item)`, `showBatchAddToPlaylistSheet()`, `hideAddToPlaylistSheet()`, `addPendingToPlaylist(targetId)`, `createPlaylistAndAddPending(name)`, `playAll()`
- 不要复制 `runUserAction` 而是按现有 `top_list_detail_*` 日志风格命名事件
- 不要引入 starred-sheet 能力（top list 不可收藏）

**验证**：
- `./gradlew :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest`

**独立性**：⭐ 完全独立，可与 Step 1/2/3 并行

---

# Step 5 — 整合到 `PluginSheetDetailScreen`（依赖：Step 1, 2, 3）

**目标**：在 Screen 中插入 Header + PlayAllBar，移除 AppBar 心形按钮，替换 row 为 `PluginMusicRow`。

**交付物**：
- 修改 `feature/home/src/main/java/com/hank/musicfree/feature/home/pluginsheet/PluginSheetDetailScreen.kt`

**约束**：
- `MusicFreeScreenScaffold` 的 `actions` 改为空（或仅保留未来扩展的占位）
- `LazyColumn` 的第一个 `item { ... }` 渲染 `MusicSheetPageHeader`：
  - cover：`uiState.sheetItem?.artwork ?: uiState.sheetItem?.coverImg`
  - title：`uiState.sheetItem?.title`
  - worksNum / musicListSize：`uiState.sheetItem?.worksNum` / `uiState.musicList.size`
  - description：`uiState.sheetItem?.description`
  - actions：`PlayAllBar(starred = isSheetStarred, onPlayAll = viewModel::playAll, onAddToPlaylist = viewModel::showBatchAddToPlaylistSheet, onToggleStarred = viewModel::toggleSheetStarred)`
- 只有 `uiState.musicList.isNotEmpty() || (!uiState.loading && uiState.errorMessage.isNullOrBlank())` 时渲染 header（即避免 loading/error 与 header 并存）
- 行替换：使用 `PluginMusicRow(...)`

**验证**：
- `./gradlew :feature:home:compileDebugKotlin :feature:home:assembleDebug`
- 装到模拟器：进入推荐歌单 → 任一 sheet：看到 cover/title/共N首/描述/PlayAllBar，心形可切换收藏，添加图标弹出 batch sheet，"播放全部"按下从第一首开始

---

# Step 6 — 整合到 `TopListDetailScreen`（依赖：Step 1, 2, 4）

**目标**：与 Step 5 同形结构；topList 不暴露心形按钮。

**交付物**：
- 修改 `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListDetailScreen.kt`

**约束**：
- 删除 `onOpenMusicDetail` 入参（行内使用 `PluginMusicRow` 的 more menu 体系）
- 同步更新调用方（找 `AppNavHost.kt` 中 `TopListDetailScreen(...)` 的调用，去掉 `onOpenMusicDetail` 参数）
- `PlayAllBar` 传入 `starred = null`
- 其余结构与 Step 5 完全一致

**验证**：
- `./gradlew :app:compileDebugKotlin :app:assembleDebug`
- 模拟器：进入榜单 → 任一榜单：看到 cover/title/共N首/描述/PlayAllBar（无心形）
- 长按某行 → 出现 options sheet；点击行 → 从该位置开始播放

---

# Step 7 — 测试与文档闸门（依赖：Step 5, 6 完成）

**交付物**：
- `:app:assembleDebug` 通过
- `:feature:home:testDebugUnitTest`、`:core:testDebugUnitTest` 通过
- emulator 装上 debug APK，手动走两条链路通过
- 如果项目已有 fidelity-pack：考虑新增截图证据；本期不强制
- 在 `docs/dev-harness/ui/rules.md` 不需要新增规则（沿用现有 Screen 入口规则）

# Step 8 — Squash 合并

```bash
git checkout main
git merge --squash feat-detail-header-parity
git commit -m "feat(detail): 榜单/插件歌单详情顶部 header + PlayAllBar 对齐 RN" --signoff
# 或者按 conventional commits 详细写法（见下）
```

Commit message（HEREDOC）：

```
feat(detail): 榜单/插件歌单详情顶部 header + PlayAllBar 对齐 RN

- 新增 core/ui MusicSheetPageHeader + PlayAllBar 共享组件
- 详情页插入 cover/title/共N首/描述/PlayAllBar，对齐 RN MusicSheetPage
- PluginSheetDetail 心形按钮迁出 AppBar 进入 PlayAllBar
- PluginSheetDetail + TopListDetail 复用 PluginMusicRow（行级 parity）
- TopList/PluginSheet ViewModel 补 playAll / 批量 add-to-playlist 接口

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

合并后：
- `git worktree remove .worktrees/feat-detail-header-parity`
- `git branch -d feat-detail-header-parity`
- 不推送（用户未指示）

# 子代理并行调度

可以同时派发 4 个独立任务：
- Subagent A：Step 1（共享组件）
- Subagent B：Step 2（行组件提升）
- Subagent C：Step 3（PluginSheetDetailViewModel）
- Subagent D：Step 4（TopListDetailViewModel）

它们彼此无文件冲突，按文件路径切片清晰。主代理负责：
- 拆分时给子代理明确的文件清单和验证命令
- 收集每个子代理的 diff，确认编译通过
- 由主代理串行执行 Step 5、6、7、8（依赖前序）

# 风险

- 模拟器没起来时跳过运行态验证并显式说明
- `PluginSheetDetailViewModelTest` 如已存在但 mock 设置不完整，需要修复或临时 `@Ignore` 标注新加测试方法（CLAUDE.md 中 test 规则约束）
- `MusicSheetPageHeader` 的 description 展开 state：必须使用 `rememberSaveable` 让旋转 / 配置变化时保留？本期使用 `remember`，与 RN 行为一致（RN 也未持久化）；如有 lint 异议再换
