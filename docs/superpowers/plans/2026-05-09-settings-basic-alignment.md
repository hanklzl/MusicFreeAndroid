# Settings Basic Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align Android settings with RN typed settings navigation and deliver a Basic Settings page that shows all RN sections while only enabling currently supported runtime-backed settings.

**Architecture:** Convert `SettingsRoute` into a typed route, keep settings screens under `:feature:settings`, and expose Basic settings through a stateful `SettingsScreen` wrapper plus stateless content components. Persist only runtime-backed settings through the existing DataStore `AppPreferences`; disabled RN parity rows remain UI-only.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose typed routes, Hilt ViewModel, DataStore Preferences, Robolectric Compose tests, Android instrumentation navigation tests.

---

## Scope Check

The approved spec is focused enough for one implementation plan:

- It changes one navigation surface: typed settings route.
- It changes one feature module surface: `:feature:settings`.
- It keeps storage on the existing `AppPreferences`.
- It explicitly excludes playback interruption policy, plugin auto update runtime, cache cleanup runtime, desktop lyrics, and debug logging runtime.

Do not implement excluded runtime systems in this plan.

## File Structure

- Modify `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`
  - Owns `SettingsType` and typed `SettingsRoute`.
- Modify `app/src/test/java/com/hank/musicfree/RoutesTest.kt`
  - Proves typed settings routes serialize and default to Basic.
- Modify `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
  - Adds Basic settings anchors and preserves existing settings entry anchors.
- Modify `feature/settings/build.gradle.kts`
  - Adds Robolectric Compose UI test dependencies.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`
  - Owns `BasicSettingsUiState` and DataStore-backed setters.
- Modify `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt`
  - Verifies Basic settings state and setters.
- Create `feature/settings/src/main/java/com/hank/musicfree/feature/settings/components/SettingsRows.kt`
  - Reusable card and row components for settings pages.
- Create `feature/settings/src/test/java/com/hank/musicfree/feature/settings/components/SettingsRowsTest.kt`
  - Verifies row enabled/disabled behavior.
- Create `feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt`
  - Stateless Basic settings content with RN section order.
- Create `feature/settings/src/test/java/com/hank/musicfree/feature/settings/BasicSettingsContentTest.kt`
  - Verifies section visibility, enabled rows, disabled rows, and dialog behavior.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`
  - Switches between `SettingsType` pages.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation/SettingsNavigation.kt`
  - Passes route type into `SettingsScreen`.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt`
  - Sends drawer settings entries to typed settings routes.
- Modify `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
  - Navigates to typed settings routes and keeps plugin list navigation.
- Modify `app/src/androidTest/java/com/hank/musicfree/HomeEntryNavigationTest.kt`
  - Verifies drawer entries land on the right typed settings pages.

## Task 1: Typed Settings Route

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`
- Modify: `app/src/test/java/com/hank/musicfree/RoutesTest.kt`

- [ ] **Step 1: Write failing route serialization tests**

Update imports in `app/src/test/java/com/hank/musicfree/RoutesTest.kt`:

```kotlin
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.core.navigation.SettingsType
```

Replace the existing `SettingsRoute is serializable` test with:

```kotlin
@Test
fun `SettingsRoute defaults to basic type`() {
    val route = SettingsRoute()
    val json = Json.encodeToString(serializer(), route)
    assertNotNull(json)

    val decoded = Json.decodeFromString<SettingsRoute>(json)

    assertEquals(SettingsType.Basic, decoded.type)
}

@Test
fun `SettingsRoute serializes every supported type`() {
    SettingsType.entries.forEach { type ->
        val route = SettingsRoute(type = type)
        val json = Json.encodeToString(serializer(), route)
        assertNotNull(json)

        val decoded = Json.decodeFromString<SettingsRoute>(json)

        assertEquals(route, decoded)
    }
}
```

- [ ] **Step 2: Run route tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.hank.musicfree.RoutesTest"
```

Expected: compile failure mentioning unresolved `SettingsType` or no constructor for `SettingsRoute()`.

- [ ] **Step 3: Implement typed route**

In `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`, replace:

```kotlin
@Serializable
data object SettingsRoute
```

with:

```kotlin
@Serializable
enum class SettingsType {
    Basic,
    Plugin,
    Theme,
    Backup,
    About,
}

@Serializable
data class SettingsRoute(
    val type: SettingsType = SettingsType.Basic,
)
```

- [ ] **Step 4: Run route tests and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.hank.musicfree.RoutesTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit typed route**

```bash
git add core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt app/src/test/java/com/hank/musicfree/RoutesTest.kt
git commit -m "feat(settings): add typed settings route"
```

## Task 2: Settings Anchors And Test Dependencies

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
- Modify: `feature/settings/build.gradle.kts`

- [ ] **Step 1: Add anchors for typed settings pages**

In `FidelityAnchors.Settings`, keep existing entries and add:

```kotlin
object Settings {
    const val BasicRoot = "settings.basic.root"
    const val BasicSectionCommon = "settings.basic.section.common"
    const val BasicSectionSheetAlbum = "settings.basic.section.sheetAlbum"
    const val BasicSectionPlugin = "settings.basic.section.plugin"
    const val BasicSectionPlayback = "settings.basic.section.playback"
    const val BasicSectionDownload = "settings.basic.section.download"
    const val BasicSectionNetwork = "settings.basic.section.network"
    const val BasicSectionLyric = "settings.basic.section.lyric"
    const val BasicSectionCache = "settings.basic.section.cache"
    const val BasicSectionDeveloper = "settings.basic.section.developer"
    const val BasicMaxDownload = "settings.basic.maxDownload"
    const val BasicDefaultDownloadQuality = "settings.basic.defaultDownloadQuality"
    const val BasicUseCellularDownload = "settings.basic.useCellularDownload"
    const val BasicLyricAutoSearch = "settings.basic.lyricAutoSearch"
    const val PluginRoot = "settings.plugin.root"
    const val ThemeRoot = "settings.theme.root"
    const val BackupRoot = "settings.backup.root"
    const val AboutRoot = "settings.about.root"
    const val PluginManagementEntry = "settings.pluginManagement.entry"
    const val ThemeEntry = "settings.theme.entry"
    const val BackupEntry = "settings.backup.entry"
    const val AboutEntry = "settings.about.entry"
}
```

If `FidelityAnchors.Settings` already contains the final four constants, do not duplicate them; replace the whole object with the block above.

- [ ] **Step 2: Add settings Compose test dependencies**

In `feature/settings/build.gradle.kts`, add these dependencies next to existing test dependencies:

```kotlin
testImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

- [ ] **Step 3: Run settings unit tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit anchors and dependencies**

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt feature/settings/build.gradle.kts
git commit -m "test(settings): add anchors and compose test support"
```

## Task 3: Basic Settings ViewModel State

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add failing ViewModel tests**

Append these tests to `SettingsViewModelTest`:

```kotlin
@Test
fun `basic settings state exposes default runtime-backed preferences`() = runTest(mainDispatcherRule.dispatcher) {
    val viewModel = createViewModel(createAppPreferences())

    val state = viewModel.basicSettingsUiState.value

    assertEquals(3, state.maxDownload)
    assertEquals(com.hank.musicfree.core.model.PlayQuality.STANDARD, state.defaultDownloadQuality)
    assertEquals(false, state.useCellularDownload)
    assertEquals(true, state.lyricAutoSearchEnabled)
    assertTrue(!state.storageAccessState.isConfigured)
}

@Test
fun `basic settings setters persist runtime-backed preferences`() = runTest(mainDispatcherRule.dispatcher) {
    val appPreferences = createAppPreferences()
    val viewModel = createViewModel(appPreferences)

    viewModel.setMaxDownload(7)
    viewModel.setDefaultDownloadQuality(com.hank.musicfree.core.model.PlayQuality.SUPER)
    viewModel.setUseCellularDownload(true)
    viewModel.setLyricAutoSearchEnabled(false)
    advanceUntilIdle()

    assertEquals(7, appPreferences.maxDownload.first())
    assertEquals(com.hank.musicfree.core.model.PlayQuality.SUPER, appPreferences.defaultDownloadQuality.first())
    assertEquals(true, appPreferences.useCellularDownload.first())
    assertEquals(false, appPreferences.lyricAutoSearchEnabled.first())
}
```

- [ ] **Step 2: Run ViewModel tests and verify they fail**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.SettingsViewModelTest"
```

Expected: compile failure for unresolved `basicSettingsUiState` and `setLyricAutoSearchEnabled`.

- [ ] **Step 3: Implement Basic settings state**

Replace `SettingsViewModel.kt` with this content:

```kotlin
package com.hank.musicfree.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.storage.DocumentTreeDirectory
import com.hank.musicfree.data.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageAccessState(
    val selectedDirectory: DocumentTreeDirectory? = null,
) {
    val isConfigured: Boolean
        get() = selectedDirectory != null
}

data class BasicSettingsUiState(
    val maxDownload: Int = 3,
    val defaultDownloadQuality: PlayQuality = PlayQuality.STANDARD,
    val useCellularDownload: Boolean = false,
    val lyricAutoSearchEnabled: Boolean = true,
    val storageAccessState: StorageAccessState = StorageAccessState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val storageAccessState: StateFlow<StorageAccessState> = appPreferences.storageDirectoryUri
        .map { uri -> StorageAccessState(uri?.let(DocumentTreeDirectory::fromTreeUri)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageAccessState())

    val maxDownload: StateFlow<Int> = appPreferences.maxDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val useCellularDownload: StateFlow<Boolean> = appPreferences.useCellularDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultDownloadQuality: StateFlow<PlayQuality> = appPreferences.defaultDownloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    val lyricAutoSearchEnabled: StateFlow<Boolean> = appPreferences.lyricAutoSearchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val basicSettingsUiState: StateFlow<BasicSettingsUiState> = combine(
        maxDownload,
        defaultDownloadQuality,
        useCellularDownload,
        lyricAutoSearchEnabled,
        storageAccessState,
    ) { maxDownload, defaultDownloadQuality, useCellularDownload, lyricAutoSearchEnabled, storageAccessState ->
        BasicSettingsUiState(
            maxDownload = maxDownload,
            defaultDownloadQuality = defaultDownloadQuality,
            useCellularDownload = useCellularDownload,
            lyricAutoSearchEnabled = lyricAutoSearchEnabled,
            storageAccessState = storageAccessState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BasicSettingsUiState())

    fun setStorageDirectory(treeUri: String) {
        viewModelScope.launch {
            appPreferences.setStorageDirectoryUri(treeUri)
        }
    }

    fun setMaxDownload(value: Int) = viewModelScope.launch { appPreferences.setMaxDownload(value) }
    fun setUseCellularDownload(value: Boolean) = viewModelScope.launch { appPreferences.setUseCellularDownload(value) }
    fun setDefaultDownloadQuality(quality: PlayQuality) = viewModelScope.launch { appPreferences.setDefaultDownloadQuality(quality) }
    fun setLyricAutoSearchEnabled(value: Boolean) = viewModelScope.launch { appPreferences.setLyricAutoSearchEnabled(value) }
}
```

- [ ] **Step 4: Run ViewModel tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.SettingsViewModelTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit ViewModel state**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): expose basic settings state"
```

## Task 4: Reusable Settings Rows

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/components/SettingsRows.kt`
- Create: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/components/SettingsRowsTest.kt`

- [ ] **Step 1: Write row component tests**

Create `SettingsRowsTest.kt`:

```kotlin
package com.hank.musicfree.feature.settings.components

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `enabled value row invokes click`() {
        var clicks = 0
        composeRule.setContent {
            MusicFreeTheme {
                SettingValueRow(
                    title = "最大同时下载数目",
                    value = "3",
                    enabled = true,
                    testTag = "row.maxDownload",
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("row.maxDownload").performClick()

        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun `disabled value row shows pending label and ignores click`() {
        var clicks = 0
        composeRule.setContent {
            MusicFreeTheme {
                SettingValueRow(
                    title = "软件启动时自动更新插件",
                    value = "待接入",
                    enabled = false,
                    testTag = "row.autoUpdatePlugin",
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithText("待接入").assertIsDisplayed()
        composeRule.onNodeWithTag("row.autoUpdatePlugin").performClick()

        composeRule.runOnIdle {
            assertEquals(0, clicks)
        }
    }

    @Test
    fun `section card displays title and content`() {
        composeRule.setContent {
            MusicFreeTheme {
                SettingSectionCard(
                    title = "下载",
                    testTag = "section.download",
                ) {
                    SettingValueRow(
                        title = "默认下载音质",
                        value = "标准音质",
                        enabled = true,
                        onClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("section.download").assertIsDisplayed()
        composeRule.onNodeWithText("默认下载音质").assertIsDisplayed()
        composeRule.onNodeWithText("不存在").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run row tests and verify they fail**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.components.SettingsRowsTest"
```

Expected: compile failure for unresolved row components.

- [ ] **Step 3: Implement reusable rows**

Create `SettingsRows.kt`:

```kotlin
package com.hank.musicfree.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx

@Composable
fun SettingSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable Column.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        shape = RoundedCornerShape(rpx(16)),
        colors = CardDefaults.cardColors(containerColor = MusicFreeTheme.colors.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = rpx(16)),
        ) {
            Text(
                text = title,
                fontSize = FontSizes.subTitle,
                color = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(8)),
            )
            content()
        }
    }
}

@Composable
fun SettingValueRow(
    title: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    SettingRowShell(
        title = title,
        enabled = enabled,
        modifier = modifier,
        testTag = testTag,
        onClick = onClick,
    ) {
        Text(
            text = value,
            fontSize = FontSizes.description,
            color = MusicFreeTheme.colors.textSecondary,
            modifier = Modifier.padding(start = rpx(16)),
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRowShell(
        title = title,
        enabled = enabled,
        modifier = modifier,
        testTag = testTag,
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
}

@Composable
fun SettingActionRow(
    title: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    trailingText: String = if (enabled) "" else "待接入",
    onClick: () -> Unit,
) {
    SettingValueRow(
        title = title,
        value = trailingText,
        enabled = enabled,
        modifier = modifier,
        testTag = testTag,
        onClick = onClick,
    )
}

@Composable
private fun SettingRowShell(
    title: String,
    enabled: Boolean,
    modifier: Modifier,
    testTag: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .height(rpx(96))
        .alpha(if (enabled) 1f else 0.52f)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
        .padding(horizontal = rpx(24))

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = FontSizes.content,
            color = MusicFreeTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.height(rpx(1)))
        trailing()
    }
}
```

- [ ] **Step 4: Run row tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.components.SettingsRowsTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit row components**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/components/SettingsRows.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/components/SettingsRowsTest.kt
git commit -m "feat(settings): add reusable setting rows"
```

## Task 5: Basic Settings Content

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt`
- Create: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/BasicSettingsContentTest.kt`

- [ ] **Step 1: Write Basic content tests**

Create `BasicSettingsContentTest.kt`:

```kotlin
package com.hank.musicfree.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BasicSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows all rn basic setting sections`() {
        setContent()

        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionCommon).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionSheetAlbum).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionPlugin).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionPlayback).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionDownload).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionNetwork).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionLyric).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionCache).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicSectionDeveloper).assertIsDisplayed()
    }

    @Test
    fun `runtime backed rows are visible and clickable`() {
        var maxDownload = 0
        setContent(onMaxDownloadChange = { maxDownload = it })

        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMaxDownload).performClick()
        composeRule.onNodeWithText("7").performClick()

        composeRule.runOnIdle {
            assertEquals(7, maxDownload)
        }
    }

    @Test
    fun `disabled rows show pending marker`() {
        setContent()

        composeRule.onNodeWithText("软件启动时自动更新插件").assertIsDisplayed()
        composeRule.onNodeWithText("待接入").assertIsDisplayed()
    }

    private fun setContent(
        onMaxDownloadChange: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                BasicSettingsContent(
                    state = BasicSettingsUiState(
                        maxDownload = 3,
                        defaultDownloadQuality = PlayQuality.STANDARD,
                        useCellularDownload = false,
                        lyricAutoSearchEnabled = true,
                    ),
                    onMaxDownloadChange = onMaxDownloadChange,
                    onDefaultDownloadQualityChange = {},
                    onUseCellularDownloadChange = {},
                    onLyricAutoSearchEnabledChange = {},
                    onNavigateToFileSelector = {},
                )
            }
        }
    }
}
```

- [ ] **Step 2: Run Basic content tests and verify they fail**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.BasicSettingsContentTest"
```

Expected: compile failure for unresolved `BasicSettingsContent`.

- [ ] **Step 3: Implement Basic content**

Create `BasicSettingsContent.kt` with this content:

```kotlin
package com.hank.musicfree.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.settings.components.SettingActionRow
import com.hank.musicfree.feature.settings.components.SettingSectionCard
import com.hank.musicfree.feature.settings.components.SettingSwitchRow
import com.hank.musicfree.feature.settings.components.SettingValueRow

private data class Choice<T>(
    val value: T,
    val label: String,
)

@Composable
fun BasicSettingsContent(
    state: BasicSettingsUiState,
    onMaxDownloadChange: (Int) -> Unit,
    onDefaultDownloadQualityChange: (PlayQuality) -> Unit,
    onUseCellularDownloadChange: (Boolean) -> Unit,
    onLyricAutoSearchEnabledChange: (Boolean) -> Unit,
    onNavigateToFileSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var maxDownloadDialog by remember { mutableStateOf(false) }
    var downloadQualityDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Settings.BasicRoot)
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        item { Spacer(modifier = Modifier.height(rpx(8))) }
        item {
            SettingSectionCard("常规", testTag = FidelityAnchors.Settings.BasicSectionCommon) {
                SettingValueRow("历史记录最多保存条数", "50", enabled = false, onClick = {})
                SettingValueRow("打开歌曲详情页时", "默认展示歌曲封面", enabled = false, onClick = {})
                SettingSwitchRow("处于歌曲详情页时常亮", false, enabled = false, onCheckedChange = {})
                SettingValueRow("关联歌词方式", "搜索歌词", enabled = false, onClick = {})
                SettingSwitchRow("通知栏显示关闭按钮 (重启后生效)", false, enabled = false, onCheckedChange = {})
            }
        }
        item {
            SettingSectionCard("歌单&专辑", testTag = FidelityAnchors.Settings.BasicSectionSheetAlbum) {
                SettingValueRow("点击搜索结果内单曲时", "待接入", enabled = false, onClick = {})
                SettingValueRow("点击专辑内单曲时", "待接入", enabled = false, onClick = {})
                SettingValueRow("新建歌单时默认歌曲排序", "待接入", enabled = false, onClick = {})
            }
        }
        item {
            SettingSectionCard("插件", testTag = FidelityAnchors.Settings.BasicSectionPlugin) {
                SettingSwitchRow("软件启动时自动更新插件", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow("安装插件时不校验版本", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow("启用插件懒加载", false, enabled = false, onCheckedChange = {})
            }
        }
        item {
            SettingSectionCard("播放", testTag = FidelityAnchors.Settings.BasicSectionPlayback) {
                SettingSwitchRow("允许与其他应用同时播放", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow("软件启动时自动播放歌曲", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow("播放失败时尝试更换音源", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow("播放失败时自动暂停", false, enabled = false, onCheckedChange = {})
                SettingValueRow("播放被暂时打断时", "待接入", enabled = false, onClick = {})
                SettingValueRow("默认播放音质", "待接入", enabled = false, onClick = {})
                SettingValueRow("默认播放音质缺失时", "待接入", enabled = false, onClick = {})
            }
        }
        item {
            SettingSectionCard("下载", testTag = FidelityAnchors.Settings.BasicSectionDownload) {
                SettingValueRow(
                    title = "下载路径",
                    value = storageDirectoryDescription(state.storageAccessState),
                    enabled = state.storageAccessState.isConfigured,
                    onClick = onNavigateToFileSelector,
                )
                SettingValueRow(
                    title = "最大同时下载数目",
                    value = state.maxDownload.toString(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicMaxDownload,
                    onClick = { maxDownloadDialog = true },
                )
                SettingValueRow(
                    title = "默认下载音质",
                    value = state.defaultDownloadQuality.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicDefaultDownloadQuality,
                    onClick = { downloadQualityDialog = true },
                )
                SettingValueRow("默认下载音质缺失时", "待接入", enabled = false, onClick = {})
            }
        }
        item {
            SettingSectionCard("网络", testTag = FidelityAnchors.Settings.BasicSectionNetwork) {
                SettingSwitchRow("使用移动网络播放", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow(
                    title = "使用移动网络下载",
                    checked = state.useCellularDownload,
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicUseCellularDownload,
                    onCheckedChange = onUseCellularDownloadChange,
                )
            }
        }
        item {
            SettingSectionCard("歌词", testTag = FidelityAnchors.Settings.BasicSectionLyric) {
                SettingSwitchRow(
                    title = "歌词缺失时自动搜索歌词",
                    checked = state.lyricAutoSearchEnabled,
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicLyricAutoSearch,
                    onCheckedChange = onLyricAutoSearchEnabledChange,
                )
                SettingSwitchRow("开启桌面歌词", false, enabled = false, onCheckedChange = {})
                SettingValueRow("桌面歌词位置/样式", "待接入", enabled = false, onClick = {})
            }
        }
        item {
            SettingSectionCard("缓存", testTag = FidelityAnchors.Settings.BasicSectionCache) {
                SettingValueRow("音乐缓存上限", "待接入", enabled = false, onClick = {})
                SettingActionRow("清除音乐缓存", enabled = false, onClick = {})
                SettingActionRow("清除歌词缓存", enabled = false, onClick = {})
                SettingActionRow("清除图片缓存", enabled = false, onClick = {})
            }
        }
        item {
            SettingSectionCard("开发选项", testTag = FidelityAnchors.Settings.BasicSectionDeveloper) {
                SettingSwitchRow("记录错误日志", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow("记录详细日志", false, enabled = false, onCheckedChange = {})
                SettingSwitchRow("调试面板", false, enabled = false, onCheckedChange = {})
                SettingActionRow("查看错误日志", enabled = false, onClick = {})
                SettingActionRow("清空日志", enabled = false, onClick = {})
            }
        }
        item { Spacer(modifier = Modifier.height(rpx(8))) }
    }

    if (maxDownloadDialog) {
        ChoiceDialog(
            title = "最大同时下载数目",
            choices = listOf(1, 3, 5, 7).map { Choice(it, it.toString()) },
            onDismiss = { maxDownloadDialog = false },
            onSelected = {
                onMaxDownloadChange(it)
                maxDownloadDialog = false
            },
        )
    }

    if (downloadQualityDialog) {
        ChoiceDialog(
            title = "默认下载音质",
            choices = listOf(
                Choice(PlayQuality.LOW, "低音质"),
                Choice(PlayQuality.STANDARD, "标准音质"),
                Choice(PlayQuality.HIGH, "高音质"),
                Choice(PlayQuality.SUPER, "超高音质"),
            ),
            onDismiss = { downloadQualityDialog = false },
            onSelected = {
                onDefaultDownloadQualityChange(it)
                downloadQualityDialog = false
            },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                choices.forEach { choice ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .testTag("settings.choice.${choice.label}")
                            .height(rpx(72))
                            .padding(horizontal = rpx(8))
                            .then(
                                Modifier.clickable {
                                    onSelected(choice.value)
                                },
                            ),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false, onClick = { onSelected(choice.value) })
                        Text(choice.label, modifier = Modifier.padding(start = rpx(8)))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun PlayQuality.label(): String = when (this) {
    PlayQuality.LOW -> "低音质"
    PlayQuality.STANDARD -> "标准音质"
    PlayQuality.HIGH -> "高音质"
    PlayQuality.SUPER -> "超高音质"
}
```

- [ ] **Step 4: Run Basic content tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.BasicSettingsContentTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Basic content**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/BasicSettingsContentTest.kt
git commit -m "feat(settings): add basic settings content"
```

## Task 6: Typed Settings Screen

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation/SettingsNavigation.kt`

- [ ] **Step 1: Update `SettingsScreen` signature and content**

Replace `SettingsScreen.kt` with a typed wrapper. Keep imports minimal and remove unused Material3 segmented/slider imports from the old screen.

```kotlin
package com.hank.musicfree.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.navigation.SettingsType
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold

@Composable
fun SettingsScreen(
    type: SettingsType,
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
    onNavigateToPluginList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val basicState by viewModel.basicSettingsUiState.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        title = type.title(),
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.SettingsRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        when (type) {
            SettingsType.Basic -> BasicSettingsContent(
                state = basicState,
                onMaxDownloadChange = viewModel::setMaxDownload,
                onDefaultDownloadQualityChange = viewModel::setDefaultDownloadQuality,
                onUseCellularDownloadChange = viewModel::setUseCellularDownload,
                onLyricAutoSearchEnabledChange = viewModel::setLyricAutoSearchEnabled,
                onNavigateToFileSelector = onNavigateToFileSelector,
                modifier = Modifier.padding(innerPadding),
            )
            SettingsType.Plugin -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.PluginRoot,
                title = "插件管理",
                description = "管理已安装的插件，安装新插件，管理订阅",
                entryTag = FidelityAnchors.Settings.PluginManagementEntry,
                actionText = "进入",
                onClick = onNavigateToPluginList,
                modifier = Modifier.padding(innerPadding),
            )
            SettingsType.Theme -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.ThemeRoot,
                title = "主题设置",
                description = "主题选项将显示在这里。",
                entryTag = FidelityAnchors.Settings.ThemeEntry,
                actionText = "待接入",
                onClick = null,
                modifier = Modifier.padding(innerPadding),
            )
            SettingsType.Backup -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.BackupRoot,
                title = "备份与恢复",
                description = "备份与恢复入口将显示在这里。",
                entryTag = FidelityAnchors.Settings.BackupEntry,
                actionText = "待接入",
                onClick = null,
                modifier = Modifier.padding(innerPadding),
            )
            SettingsType.About -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.AboutRoot,
                title = "关于 MusicFree",
                description = "应用信息与版本详情将显示在这里。",
                entryTag = FidelityAnchors.Settings.AboutEntry,
                actionText = "待接入",
                onClick = null,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun SettingsTypeEntryContent(
    rootTag: String,
    title: String,
    description: String,
    entryTag: String,
    actionText: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(rootTag)
            .padding(horizontal = rpx(24)),
    ) {
        item {
            Spacer(modifier = Modifier.height(rpx(24)))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(entryTag),
                shape = RoundedCornerShape(rpx(16)),
                colors = CardDefaults.cardColors(containerColor = MusicFreeTheme.colors.card),
            ) {
                Column(modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(20))) {
                    Text(text = title, fontSize = FontSizes.content, color = MusicFreeTheme.colors.text)
                    Spacer(modifier = Modifier.height(rpx(6)))
                    Text(text = description, fontSize = FontSizes.description, color = MusicFreeTheme.colors.textSecondary)
                    Spacer(modifier = Modifier.height(rpx(12)))
                    TextButton(onClick = onClick ?: {}, enabled = onClick != null) {
                        Text(actionText)
                    }
                }
            }
        }
    }
}

private fun SettingsType.title(): String = when (this) {
    SettingsType.Basic -> "基本设置"
    SettingsType.Plugin -> "插件管理"
    SettingsType.Theme -> "主题设置"
    SettingsType.Backup -> "备份与恢复"
    SettingsType.About -> "关于 MusicFree"
}
```

- [ ] **Step 2: Update navigation wrapper**

Replace `SettingsNavigation.kt` with:

```kotlin
package com.hank.musicfree.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.feature.settings.SettingsScreen

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
    onNavigateToPluginList: () -> Unit,
) {
    composable<SettingsRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SettingsRoute>()
        SettingsScreen(
            type = route.type,
            onBack = onBack,
            onNavigateToPermissions = onNavigateToPermissions,
            onNavigateToFileSelector = onNavigateToFileSelector,
            onNavigateToLocalFileSelector = onNavigateToLocalFileSelector,
            onNavigateToPluginList = onNavigateToPluginList,
        )
    }
}
```

- [ ] **Step 3: Run settings tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit typed settings screen**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation/SettingsNavigation.kt
git commit -m "feat(settings): render typed settings pages"
```

## Task 7: Drawer And App Navigation Wiring

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
- Modify: `app/src/androidTest/java/com/hank/musicfree/HomeEntryNavigationTest.kt`

- [ ] **Step 1: Update app navigation callback shape**

In `AppNavHost.kt`, import `SettingsType`:

```kotlin
import com.hank.musicfree.core.navigation.SettingsType
```

Update the `homeScreen` call to pass typed settings navigation:

```kotlin
homeScreen(
    onNavigateToSearch = { navController.navigate(SearchRoute) },
    onNavigateToRecommendSheets = { navController.navigate(RecommendSheetsRoute) },
    onNavigateToHistory = { navController.navigate(HistoryRoute) },
    onNavigateToLocal = { navController.navigate(LocalRoute) },
    onNavigateToSettings = { type -> navController.navigate(SettingsRoute(type)) },
    onNavigateToPermissions = { navController.navigate(PermissionsRoute) },
    onNavigateToTopList = { navController.navigate(TopListRoute) },
    onNavigateToPlaylistDetail = { playlistId -> navController.navigate(PlaylistDetailRoute(playlistId)) },
    homeSystemActionHandler = homeSystemActionHandler,
)
```

If the local argument order differs, preserve all existing callbacks and only change `onNavigateToSettings` to accept `SettingsType`.

- [ ] **Step 2: Update HomeScreen callback and drawer mapping**

In `HomeScreen.kt`, change the parameter:

```kotlin
onNavigateToSettings: (SettingsType) -> Unit,
```

Add import:

```kotlin
import com.hank.musicfree.core.navigation.SettingsType
```

Replace the current drawer settings branch with:

```kotlin
when (action) {
    HomeDrawerAction.OpenSettingsRoot -> onNavigateToSettings(SettingsType.Basic)
    HomeDrawerAction.OpenPluginManagement -> onNavigateToSettings(SettingsType.Plugin)
    HomeDrawerAction.OpenThemeSettings -> onNavigateToSettings(SettingsType.Theme)
    HomeDrawerAction.OpenBackup -> onNavigateToSettings(SettingsType.Backup)
    HomeDrawerAction.OpenAbout -> onNavigateToSettings(SettingsType.About)
    HomeDrawerAction.OpenPermissions -> onNavigateToPermissions()
    HomeDrawerAction.BackToDesktop -> homeSystemActionHandler.backToDesktop()
    HomeDrawerAction.ExitApp -> {
        coroutineScope.launch {
            homeSystemActionHandler.exitApp()
        }
    }
    HomeDrawerAction.ShowScheduleClosePanel,
    HomeDrawerAction.ShowLanguageDialog,
    HomeDrawerAction.ShowUpdateCheckDialog -> Unit
}
```

- [ ] **Step 3: Update navigation android tests**

In `HomeEntryNavigationTest`, update settings assertions:

```kotlin
@Test
fun settingsBasicEntry_opensBasicSettingsRoot() {
    openDrawerDestination(FidelityAnchors.Home.DrawerSettingsBasic)
    assertTagExists(FidelityAnchors.Screen.SettingsRoot)
    assertTagExists(FidelityAnchors.Settings.BasicRoot)
}

@Test
fun settingsPluginEntry_exposesSettingsPluginAnchor() {
    openDrawerDestination(FidelityAnchors.Home.DrawerSettingsPlugin)
    assertSettingsFallbackEntry(FidelityAnchors.Settings.PluginManagementEntry)
}

@Test
fun settingsThemeEntry_exposesSettingsThemeAnchor() {
    openDrawerDestination(FidelityAnchors.Home.DrawerSettingsTheme)
    assertTagExists(FidelityAnchors.Screen.SettingsRoot)
    assertTagExists(FidelityAnchors.Settings.ThemeRoot)
    assertSettingsFallbackEntry(FidelityAnchors.Settings.ThemeEntry)
}

@Test
fun backupEntry_exposesSettingsBackupAnchor() {
    openDrawerDestination(FidelityAnchors.Home.DrawerOtherBackup)
    assertTagExists(FidelityAnchors.Screen.SettingsRoot)
    assertTagExists(FidelityAnchors.Settings.BackupRoot)
    assertSettingsFallbackEntry(FidelityAnchors.Settings.BackupEntry)
}

@Test
fun aboutEntry_exposesSettingsAboutAnchor() {
    openDrawerDestination(FidelityAnchors.Home.DrawerSoftwareAbout)
    assertTagExists(FidelityAnchors.Screen.SettingsRoot)
    assertTagExists(FidelityAnchors.Settings.AboutRoot)
    assertSettingsFallbackEntry(FidelityAnchors.Settings.AboutEntry)
}
```

- [ ] **Step 4: Run compile and tests**

Run:

```bash
./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit navigation wiring**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt app/src/androidTest/java/com/hank/musicfree/HomeEntryNavigationTest.kt
git commit -m "feat(settings): wire drawer to typed settings"
```

## Task 8: Final Verification

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run settings unit tests**

```bash
./gradlew :feature:settings:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run app route tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.hank.musicfree.RoutesTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run debug build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run instrumentation navigation test when a device is available**

Check devices:

```bash
adb devices
```

If at least one device or emulator is listed as `device`, run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.HomeEntryNavigationTest
```

Expected: `BUILD SUCCESSFUL`.

If no device is listed, record: `Instrumentation navigation test not run because no Android device or emulator was available.`

- [ ] **Step 5: Inspect git status**

```bash
git status --short
```

Expected: no unstaged or untracked implementation files.

## Self-Review

- Spec coverage:
  - Typed route: Task 1, Task 6, Task 7.
  - Basic RN sections visible: Task 5.
  - Android card grouping: Task 4 and Task 5.
  - DataStore-backed enabled settings: Task 3 and Task 5.
  - Disabled unsupported RN settings: Task 5.
  - Navigation tests and anchors: Task 2 and Task 7.
  - Build/test gates: Task 8.
- Placeholder scan: checked for red-flag placeholder language; none remains.
- Type consistency:
  - `SettingsType` and `SettingsRoute(type)` are defined in Task 1 and reused in Task 6 and Task 7.
  - `BasicSettingsUiState` is defined in Task 3 and reused in Task 5.
  - Anchor names are defined in Task 2 and reused in Task 5 and Task 7.
