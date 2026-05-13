# Player More Lyric Options Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将全屏播放器封面页与歌词页三点按钮对齐到 RN `MusicItemLyricOptions` 风格底部浮窗。

**Architecture:** 新增独立 `component/more/PlayerMoreOptionsSheet.kt`，把可测试内容和 `ModalBottomSheet` 容器分离。`PlayerScreen` 只负责显示状态、调用现有歌词导入/删除链路和 toast。

**Tech Stack:** Kotlin, Jetpack Compose, Material3 `ModalBottomSheet`, Coil, Compose UI Test, Robolectric.

---

## Files

- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/more/PlayerMoreOptionsSheet.kt`
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/more/PlayerMoreOptionsSheetTest.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerOperationsBarTest.kt`
- Modify: `docs/DOCS_STATUS.md`

## Task 1: Add The Sheet Component

- [ ] **Step 1: Create `PlayerMoreOptionsSheetContent`**

Implement a content composable that accepts `MusicItem`, `desktopLyricEnabled`, and callbacks:

```kotlin
internal fun PlayerMoreOptionsSheetContent(
    item: MusicItem,
    desktopLyricEnabled: Boolean,
    onToggleDesktopLyric: () -> Unit,
    onImportRawLyric: () -> Unit,
    onImportTranslatedLyric: () -> Unit,
    onDeleteLocalLyric: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Rows must render in this order: `ID`, `作者`, optional `专辑`, desktop lyric toggle, raw upload, translation upload, local lyric delete.

- [ ] **Step 2: Add `PlayerMoreOptionsSheet` wrapper**

Wrap content in `ModalBottomSheet`, use `RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28))`, and apply `Modifier.windowInsetsPadding(WindowInsets.statusBars)`.

- [ ] **Step 3: Copy info rows to clipboard**

Use `LocalClipboardManager` inside content rows. Copy `{"platform":"<platform>","id":"<id>"}` for ID, artist for 作者, album for 专辑. Trigger a toast callback after copy.

## Task 2: Cover The Component With Tests

- [ ] **Step 1: Add content rendering test**

Create `PlayerMoreOptionsSheetTest` and assert visible texts for sample item:

```kotlin
MusicItem(
    id = "150571",
    platform = "元力 KW",
    title = "孤单北半球",
    artist = "欧得洋",
    album = "北半球有欧得洋",
    duration = 248_000L,
    url = null,
    artwork = null,
    qualities = null,
)
```

Expected texts: `孤单北半球`, `欧得洋 - 北半球有欧得洋`, `ID: 元力 KW@150571`, `作者: 欧得洋`, `专辑: 北半球有欧得洋`, `开启桌面歌词`, `上传本地歌词`, `上传本地歌词翻译`, `删除本地歌词`。

- [ ] **Step 2: Add hidden album test**

Use `album = null` and assert no `专辑:` row is displayed.

- [ ] **Step 3: Add callback test**

Click desktop lyric, raw upload, translation upload, and delete rows; assert each callback count increments once.

## Task 3: Wire PlayerScreen

- [ ] **Step 1: Replace cover DropdownMenu behavior**

Remove `menuExpanded` and `DropdownMenu` from `PlayerOperationsBar`; add `onMoreClick` and invoke it from the more slot.

- [ ] **Step 2: Show sheet from cover and lyric pages**

In `PlayerScreen`, add `showMoreOptionsSheet`. Pass `onMoreClick = { showMoreOptionsSheet = true }` to cover operations and `onMore = { showMoreOptionsSheet = true }` to lyric operations.

- [ ] **Step 3: Reuse existing lyric actions**

When sheet action is clicked:

- raw upload: close sheet, set `pendingImportKind = LocalLyricKind.Raw`, launch `OpenDocument`
- translation upload: close sheet, set `pendingImportKind = LocalLyricKind.Translation`, launch `OpenDocument`
- delete: call `viewModel.deleteLocalLyric()`, close sheet, toast `已删除本地歌词`
- desktop lyric: toast `桌面歌词暂未接入`

## Task 4: Update Existing Tests

- [ ] **Step 1: Update `PlayerOperationsBarTest`**

Replace the current “更多 -> 加入歌单” assertion with a direct `onMoreClick` callback assertion. Keep row height, slot count, and visual size assertions unchanged.

- [ ] **Step 2: Run targeted tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerOperationsBarTest' --tests '*PlayerMoreOptionsSheetTest' --no-daemon
```

Expected: both test classes pass.

## Task 5: Verify And Merge

- [ ] **Step 1: Run full player-ui unit tests**

```bash
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

- [ ] **Step 2: Run harness grep check**

```bash
python3 scripts/dev-harness/grep-check.py
```

- [ ] **Step 3: Run Debug build**

```bash
./gradlew :app:assembleDebug --no-daemon
```

- [ ] **Step 4: Squash merge to main**

From the main checkout:

```bash
git merge --squash player-more-sheet-rn
git commit -m "feat(player-ui): 对齐播放器三点歌词操作浮窗"
```
