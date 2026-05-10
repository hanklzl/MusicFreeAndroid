# Player Operation Buttons RN Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the player detail operation rows above the seek bar use RN-aligned fixed sizing on both cover and lyric pages.

**Architecture:** Keep the change local to `:feature:player-ui`, with drawable resources added to `:core` only for missing RN-equivalent icons. Add a small private operation-slot abstraction so Material3 default `IconButton` sizing and text glyph metrics cannot make one item appear larger than another.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Robolectric Compose tests, Android vector drawables, Gradle.

---

> 文档状态：当前执行计划
> 适用范围：`docs/superpowers/specs/2026-05-10-player-operation-buttons-rn-align-design.md`
> worktree：`.worktrees/fix-player-operation-buttons-rn-align`

## File Structure

- Modify `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
  - Add local test tags for operation slots and visual content.
  - Replace the cover operation row's raw `IconButton` / `Text` mix with fixed-size operation slots.
  - Render `ic_quality_standard.png` and `ic_rate_100.png` at `rpx(52)`.
- Modify `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperations.kt`
  - Replace text glyph controls with fixed-size RN-equivalent icons.
  - Add fixed-size operation slots and test tags for lyric operations.
- Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerOperationsBarTest.kt`
  - Verify cover operation row height, slot count, and visual content sizes.
- Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperationsTest.kt`
  - Verify lyric operation row height, slot count, icon sizes, and click callbacks.
- Create `core/src/main/res/drawable/ic_font_size.xml`
- Create `core/src/main/res/drawable/ic_arrows_left_right.xml`
- Create `core/src/main/res/drawable/ic_translation.xml`

## Task 1: Add Failing Operation-Row Size Tests

**Files:**
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerOperationsBarTest.kt`
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperationsTest.kt`

- [ ] **Step 1: Add cover operation row tests**

Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerOperationsBarTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerOperationsBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `cover operation row uses RN height and six fixed slots`() {
        setContent()

        val rowHeight = composeRule.onNodeWithTag(PlayerOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val slotHeights = composeRule.onAllNodesWithTag(PlayerOperationSlotTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
            .distinct()

        with(composeRule.density) {
            assertTrue(rowHeight.toDp() > 0.dp)
            assertEquals(1, slotHeights.size)
            assertTrue(slotHeights.single().toDp() > 0.dp)
            assertTrue(rowHeight.toDp() >= slotHeights.single().toDp())
        }
        assertEquals(6, composeRule.onAllNodesWithTag(PlayerOperationSlotTestTag).fetchSemanticsNodes().size)
    }

    @Test
    fun `cover operation visuals match RN icon and image sizes`() {
        setContent()

        val iconHeights = composeRule.onAllNodesWithTag(PlayerOperationIconVisualTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
            .distinct()
        val imageHeights = composeRule.onAllNodesWithTag(PlayerOperationImageVisualTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
            .distinct()

        with(composeRule.density) {
            assertEquals(1, iconHeights.size)
            assertEquals(1, imageHeights.size)
            assertTrue(imageHeights.single().toDp() > iconHeights.single().toDp())
        }
    }

    @Test
    fun `cover operation callbacks remain wired`() {
        var favoriteClicks = 0
        var lyricClicks = 0
        setContent(onToggleFav = { favoriteClicks++ }, onToggleLyrics = { lyricClicks++ })

        composeRule.onNodeWithContentDescription("收藏").performClick()
        composeRule.onNodeWithContentDescription("歌词").performClick()

        composeRule.runOnIdle {
            assertEquals(1, favoriteClicks)
            assertEquals(1, lyricClicks)
        }
    }

    private fun setContent(
        onToggleFav: () -> Unit = {},
        onToggleLyrics: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                    PlayerOperationsBar(
                        isFav = false,
                        hasCurrentItem = true,
                        onToggleFav = onToggleFav,
                        onAddToPlaylist = {},
                        onToggleLyrics = onToggleLyrics,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add lyric operation row tests**

Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperationsTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerLyricsOperationsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `lyric operation row uses RN height and five fixed slots`() {
        setContent()

        val rowHeight = composeRule.onNodeWithTag(PlayerLyricsOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val slotHeights = composeRule.onAllNodesWithTag(PlayerLyricsOperationSlotTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
            .distinct()

        with(composeRule.density) {
            assertTrue(rowHeight.toDp() > 0.dp)
            assertEquals(1, slotHeights.size)
            assertTrue(slotHeights.single().toDp() > 0.dp)
            assertTrue(rowHeight.toDp() >= slotHeights.single().toDp())
        }
        assertEquals(5, composeRule.onAllNodesWithTag(PlayerLyricsOperationSlotTestTag).fetchSemanticsNodes().size)
    }

    @Test
    fun `lyric operation icons share one RN visual size`() {
        setContent()

        val iconHeights = composeRule.onAllNodesWithTag(PlayerLyricsOperationIconVisualTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
            .distinct()

        with(composeRule.density) {
            assertEquals(1, iconHeights.size)
            assertTrue(iconHeights.single().toDp() > 0.dp)
        }
    }

    @Test
    fun `lyric operation callbacks remain wired`() {
        var fontClicks = 0
        var offsetClicks = 0
        var searchClicks = 0
        var translationClicks = 0
        var moreClicks = 0
        setContent(
            onFontSize = { fontClicks++ },
            onOffset = { offsetClicks++ },
            onSearch = { searchClicks++ },
            onToggleTranslation = { translationClicks++ },
            onMore = { moreClicks++ },
        )

        composeRule.onNodeWithContentDescription("调整歌词字号").performClick()
        composeRule.onNodeWithContentDescription("调整歌词进度").performClick()
        composeRule.onNodeWithContentDescription("搜索歌词").performClick()
        composeRule.onNodeWithContentDescription("切换歌词翻译").performClick()
        composeRule.onNodeWithContentDescription("歌词更多").performClick()

        composeRule.runOnIdle {
            assertEquals(1, fontClicks)
            assertEquals(1, offsetClicks)
            assertEquals(1, searchClicks)
            assertEquals(1, translationClicks)
            assertEquals(1, moreClicks)
        }
    }

    private fun setContent(
        onFontSize: () -> Unit = {},
        onOffset: () -> Unit = {},
        onSearch: () -> Unit = {},
        onToggleTranslation: () -> Unit = {},
        onMore: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                    PlayerLyricsOperations(
                        state = PlayerLyricsUiState(hasTranslation = true, showTranslation = true),
                        onFontSize = onFontSize,
                        onOffset = onOffset,
                        onSearch = onSearch,
                        onToggleTranslation = onToggleTranslation,
                        onMore = onMore,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Run focused tests to verify they fail**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerOperationsBarTest' --tests '*PlayerLyricsOperationsTest' --no-daemon
```

Expected: compilation fails because the new test tags do not exist yet, or assertions fail against the current text-based/default-size implementation.

- [ ] **Step 4: Commit failing tests**

```bash
git add feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerOperationsBarTest.kt feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperationsTest.kt
git commit -m "test: cover player operation row sizing"
```

## Task 2: Implement RN-Aligned Operation Slots

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperations.kt`
- Create: `core/src/main/res/drawable/ic_font_size.xml`
- Create: `core/src/main/res/drawable/ic_arrows_left_right.xml`
- Create: `core/src/main/res/drawable/ic_translation.xml`

- [ ] **Step 1: Add missing RN-equivalent drawables**

Create `core/src/main/res/drawable/ic_font_size.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="15"
    android:viewportHeight="15">
    <path
        android:fillColor="@color/white"
        android:fillType="evenOdd"
        android:pathData="M2.782,2.217C2.707,2.142 2.606,2.1 2.499,2.1C2.393,2.1 2.292,2.142 2.217,2.217L0.217,4.217C0.06,4.373 0.06,4.627 0.217,4.783C0.373,4.939 0.626,4.939 0.782,4.783L2.099,3.466V11.534L0.782,10.217C0.626,10.061 0.373,10.061 0.217,10.217C0.06,10.373 0.06,10.627 0.217,10.783L2.217,12.783C2.292,12.858 2.393,12.9 2.499,12.9C2.606,12.9 2.707,12.858 2.782,12.783L4.782,10.783C4.939,10.627 4.939,10.373 4.782,10.217C4.626,10.061 4.373,10.061 4.217,10.217L2.899,11.534V3.466L4.217,4.783C4.373,4.939 4.626,4.939 4.782,4.783C4.939,4.627 4.939,4.373 4.782,4.217L2.782,2.217ZM10.5,2.75C10.711,2.75 10.899,2.882 10.97,3.08L13.97,11.4C14.064,11.66 13.929,11.946 13.67,12.04C13.41,12.134 13.123,11.999 13.03,11.739L12.048,9.016H8.952L7.97,11.739C7.877,11.999 7.59,12.134 7.33,12.04C7.071,11.946 6.936,11.66 7.03,11.4L10.03,3.08C10.101,2.882 10.289,2.75 10.5,2.75ZM10.5,4.724L11.741,8.166H9.259L10.5,4.724Z" />
</vector>
```

Create `core/src/main/res/drawable/ic_arrows_left_right.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M7.5,21L3,16.5M3,16.5L7.5,12M3,16.5H16.5M16.5,3L21,7.5M21,7.5L16.5,12M21,7.5H7.5"
        android:strokeColor="@color/white"
        android:strokeWidth="1.5"
        android:fillColor="#00000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

Create `core/src/main/res/drawable/ic_translation.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/white"
        android:pathData="M7.14,6.988C6.709,6.18 5.773,4.918 4.978,4L3.971,4.646C4.727,5.604 5.646,6.936 6.059,7.762L7.14,6.988ZM14.395,14.006H17.977V12.82H14.395V11.238H13.081V12.82H9.535V14.006H13.081V15.896H8.542V16.419C8.436,16.186 8.326,15.936 8.256,15.716L6.814,16.779V9.168H3V10.46H5.518V16.85C5.518,17.733 4.978,18.344 4.639,18.599C4.89,18.814 5.232,19.302 5.395,19.57C5.628,19.262 6.059,18.884 8.542,16.977V17.105H13.081V20.058H14.395V17.105H19.146V15.896H14.395V14.006ZM16.535,5.727C15.832,6.698 14.914,7.546 13.837,8.285C12.826,7.564 12,6.698 11.367,5.727H16.535ZM17.559,4.488L17.344,4.541H8.779V5.727H10.053C10.756,6.971 11.675,8.03 12.773,8.948C11.262,9.832 9.552,10.5 7.896,10.895C8.128,11.163 8.454,11.686 8.599,12.029C10.378,11.541 12.215,10.785 13.837,9.761C15.274,10.697 16.913,11.418 18.697,11.849C18.873,11.506 19.233,10.983 19.523,10.75C17.832,10.39 16.285,9.796 14.931,9.005C16.408,7.907 17.616,6.575 18.407,4.971L17.559,4.488Z" />
</vector>
```

- [ ] **Step 2: Add cover operation test tags and helper**

In `PlayerScreen.kt`, add constants next to the existing operation test tag constants:

```kotlin
internal const val PlayerOperationSlotTestTag = "player.operations.slot"
internal const val PlayerOperationIconVisualTestTag = "player.operations.iconVisual"
internal const val PlayerOperationImageVisualTestTag = "player.operations.imageVisual"
```

Add private helpers near `PlayerOperationsBar`:

```kotlin
@Composable
private fun PlayerOperationSlot(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(rpx(64))
            .testTag(PlayerOperationSlotTestTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun PlayerOperationIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
    tint: Color,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(IconSizes.normal)
            .testTag(PlayerOperationIconVisualTestTag),
    )
}

@Composable
private fun PlayerOperationImage(
    @DrawableRes image: Int,
    contentDescription: String,
) {
    Image(
        painter = painterResource(image),
        contentDescription = contentDescription,
        modifier = Modifier
            .size(rpx(52))
            .testTag(PlayerOperationImageVisualTestTag),
    )
}
```

Add imports:

```kotlin
import androidx.compose.foundation.Image
```

- [ ] **Step 3: Replace cover row content**

In `PlayerOperationsBar`, keep the row size and spacing, then replace children with:

```kotlin
PlayerOperationSlot(
    onClick = onToggleFav,
    enabled = hasCurrentItem,
    contentDescription = if (isFav) "取消收藏" else "收藏",
) {
    PlayerOperationIcon(
        icon = if (isFav) R.drawable.ic_heart else R.drawable.ic_heart_outline,
        contentDescription = if (isFav) "取消收藏" else "收藏",
        tint = if (isFav) Color(0xFFE54B4B) else Color.White.copy(alpha = 0.7f),
    )
}
PlayerOperationSlot(onClick = {}, contentDescription = "音质") {
    PlayerOperationImage(R.drawable.ic_quality_standard, "音质")
}
PlayerOperationSlot(onClick = {}, contentDescription = "下载") {
    PlayerOperationIcon(R.drawable.ic_arrow_down_tray, "下载", Color.White.copy(alpha = 0.7f))
}
PlayerOperationSlot(onClick = {}, contentDescription = "倍速") {
    PlayerOperationImage(R.drawable.ic_rate_100, "倍速")
}
PlayerOperationSlot(
    onClick = onToggleLyrics,
    enabled = hasCurrentItem,
    contentDescription = "歌词",
) {
    PlayerOperationIcon(R.drawable.ic_chat_bubble, "歌词", Color.White.copy(alpha = 0.7f))
}
Box {
    PlayerOperationSlot(onClick = { menuExpanded = true }, contentDescription = "更多") {
        PlayerOperationIcon(R.drawable.ic_ellipsis_vertical, "更多", Color.White.copy(alpha = 0.7f))
    }
    DropdownMenu(...)
}
```

Preserve the existing `DropdownMenuItem("加入歌单")` behavior.

- [ ] **Step 4: Add lyric operation test tags and helper**

In `PlayerLyricsOperations.kt`, remove text-only icon imports that become unused and add:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
```

Add constants:

```kotlin
internal const val PlayerLyricsOperationsBarTestTag = "player.lyrics.operations.bar"
internal const val PlayerLyricsOperationSlotTestTag = "player.lyrics.operations.slot"
internal const val PlayerLyricsOperationIconVisualTestTag = "player.lyrics.operations.iconVisual"
```

Add helpers:

```kotlin
@Composable
private fun LyricOperationSlot(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(rpx(64))
            .testTag(PlayerLyricsOperationSlotTestTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun LyricOperationIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
    tint: Color,
) {
    Icon(
        painter = painterResource(id = icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(IconSizes.normal)
            .testTag(PlayerLyricsOperationIconVisualTestTag),
    )
}
```

Add imports:

```kotlin
import androidx.annotation.DrawableRes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
```

- [ ] **Step 5: Replace lyric row content**

Add `.testTag(PlayerLyricsOperationsBarTestTag)` to the row modifier and replace children with:

```kotlin
LyricOperationSlot(onClick = onFontSize) {
    LyricOperationIcon(R.drawable.ic_font_size, "调整歌词字号", Color.White)
}
LyricOperationSlot(onClick = onOffset) {
    LyricOperationIcon(R.drawable.ic_arrows_left_right, "调整歌词进度", Color.White)
}
LyricOperationSlot(onClick = onSearch) {
    LyricOperationIcon(R.drawable.ic_magnifying_glass, "搜索歌词", Color.White)
}
LyricOperationSlot(
    onClick = onToggleTranslation,
    enabled = state.hasTranslation,
) {
    LyricOperationIcon(R.drawable.ic_translation, "切换歌词翻译", translationEnabledColor)
}
LyricOperationSlot(onClick = onMore) {
    LyricOperationIcon(R.drawable.ic_ellipsis_vertical, "歌词更多", Color.White)
}
```

- [ ] **Step 6: Run focused tests until they pass**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerOperationsBarTest' --tests '*PlayerLyricsOperationsTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit implementation**

```bash
git add core/src/main/res/drawable/ic_font_size.xml core/src/main/res/drawable/ic_arrows_left_right.xml core/src/main/res/drawable/ic_translation.xml feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperations.kt
git commit -m "fix: align player operation button sizes"
```

## Task 3: Verify Feature and Guard Scope

**Files:**
- Read: `docs/dev-harness/ui/rules.md`
- Read: `docs/dev-harness/player/rules.md`
- Read: `docs/dev-harness/test/rules.md` only if a test failure requires test infrastructure changes

- [ ] **Step 1: Run player-ui tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run app debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run dev harness grep guard**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: all checks pass. In particular, no new raw `TopAppBarDefaults.topAppBarColors(` usage and no `MainActivity` top inset regression.

- [ ] **Step 4: Inspect final diff**

Run:

```bash
git diff HEAD~3..HEAD -- feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsOperations.kt core/src/main/res/drawable
```

Expected: diff is limited to operation-row sizing, icon/image rendering, tests, docs, and the three required drawables.

- [ ] **Step 5: Commit verification note if any tracked files changed**

If verification produces no tracked file changes, do not create an empty commit. If a test snapshot or generated baseline unexpectedly changes, inspect it and only commit if it is necessary for this feature:

```bash
git status --short
```

Expected: clean working tree after implementation commits.
