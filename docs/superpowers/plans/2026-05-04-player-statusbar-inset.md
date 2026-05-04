# Player Status Bar Inset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the player detail page visually immersive while moving player controls and title content below the Android status bar.

**Architecture:** `PlayerScreen` remains the owner of player-specific immersive chrome. The background layers continue to draw full-screen, and a small internal `PlayerContentLayer` composable applies only top status-bar inset to interactive content. A focused Robolectric Compose test verifies that injected top insets move content down without adding horizontal inset.

**Tech Stack:** Kotlin, Jetpack Compose Foundation window insets, Robolectric, Compose UI test, Gradle Android unit tests.

---

### Task 1: Add Failing Inset Test

**Files:**
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`:

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

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.playerui.PlayerScreenInsetsTest"
```

Expected: `FAIL` because `PlayerContentLayer` is not defined.

### Task 2: Apply Top Inset To Player Content Layer

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Test: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`

- [ ] **Step 1: Add the required Compose window inset imports**

In `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`, add these imports with the existing `androidx.compose.foundation.layout` imports:

```kotlin
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
```

- [ ] **Step 2: Replace the content Column call with PlayerContentLayer**

In `PlayerScreen`, replace the current Layer 3 content wrapper:

```kotlin
        // Layer 3: 内容
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
```

with:

```kotlin
        // Layer 3: 内容
        PlayerContentLayer {
```

Keep the existing closing brace for the content block. Do not move background layers or apply inset to the root `Box`.

- [ ] **Step 3: Add the internal PlayerContentLayer composable**

Add this composable below `PlayerScreen` and above `PlayerNavBar` in `PlayerScreen.kt`:

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

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.playerui.PlayerScreenInsetsTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run the full player-ui unit test suite**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

### Task 3: Build And Runtime Acceptance

**Files:**
- Verify: `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
- Verify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`

- [ ] **Step 1: Confirm MainActivity top inset policy remains unchanged**

Run:

```bash
sed -n '70,95p' app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt
```

Expected output still includes:

```kotlin
contentWindowInsets = WindowInsets.safeDrawing.only(
    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
),
```

- [ ] **Step 2: Confirm the background layers remain full-screen**

Run:

```bash
rg -n "Layer 1|Layer 2|Layer 3|PlayerContentLayer|fillMaxSize|windowInsetsPadding" feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt
```

Expected: Layer 1 and Layer 2 still use `fillMaxSize()`, and only `PlayerContentLayer` uses `windowInsetsPadding`.

- [ ] **Step 3: Build the app module**

Run:

```bash
./gradlew :app:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Perform device or emulator visual acceptance**

Run:

```bash
adb devices
```

Expected: at least one device row with `device`. If no device is connected, record that runtime visual acceptance could not be completed in this environment.

When a device is available, run:

```bash
./gradlew :app:installDebug
adb shell am start -n com.zili.android.musicfreeandroid.debug/com.zili.android.musicfreeandroid.MainActivity
```

Manual acceptance:

- Start playback from any visible music list item.
- Tap the MiniPlayer to open `PlayerScreen`.
- Confirm the blurred or black player background extends behind the status bar.
- Confirm the back button, title, platform tag, and share button start below the status bar icons.
- Confirm the MiniPlayer is hidden while `PlayerScreen` is visible.

### Task 4: Commit Implementation

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt`

- [ ] **Step 1: Review the diff**

Run:

```bash
git diff -- feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt
```

Expected: the diff only adds `PlayerContentLayer`, applies it to Layer 3 content, and adds the focused test.

- [ ] **Step 2: Commit the implementation**

Run:

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreenInsetsTest.kt
git commit -m "fix(player-ui): keep player content below status bar"
```

Expected: a new commit on branch `fix-player-statusbar-inset`.
