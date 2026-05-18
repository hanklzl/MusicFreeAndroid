# Release Lint Gate And Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理当前 Android lint 全量报告，并把 lint 接入 release-only 检查链路。

**Architecture:** 以源头修复为边界，不新增 lint baseline、lint.xml、Gradle lint disable、源码 `@SuppressLint` 或 issue 降级配置。代码 / manifest / 资源问题在对应模块修复；版本 freshness 类 warning 通过显式版本升级解决；release gate 只接入 tag 发布路径与本地 `preflight`，不进入日常 dev harness。

**Tech Stack:** Gradle Kotlin DSL, Android Gradle Plugin, Android lint, Jetpack Compose, Media3, Bash, GitHub Actions.

---

## File Map

| 文件 | 责任 |
|---|---|
| `gradle/libs.versions.toml` | 升级 lint 报告点名的 AGP / Compose BOM / Media3 / coroutines / mockk / semver / androidx.test core / org.json，并把 org.json 放进 version catalog |
| `*/build.gradle.kts` | 所有 Android module 从 compileSdk 36.1 升到 37；`:app` targetSdk 升到 37；`:data` 改用 `libs.org.json` |
| `AGENTS.md`, `README.md` | 同步当前构建基线，避免仓库入口文档漂移 |
| `player/src/main/AndroidManifest.xml` | 增加网络状态权限，收紧 `PlaybackService` exported 语义 |
| `app/src/main/AndroidManifest.xml` | 清理冗余 activity label，保留 predictive back 禁用语义并处理 API level lint |
| `core/src/main/java/com/hank/musicfree/core/permissions/*.kt` | 避免 Android 13 权限常量触发 inlined API warning |
| `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsHelpers.kt` | 移除 package visibility query，用 startActivity failure 兜底 |
| `feature/settings/src/test/java/com/hank/musicfree/feature/settings/PermissionsHelpersTest.kt` | 更新 overlay settings 行为测试 |
| `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt` | 移除 overlay permission intent 的 resolve query，改用 startActivity failure 兜底 |
| `core/src/main/java/com/hank/musicfree/core/theme/Rpx.kt` | 用 `LocalWindowInfo.current.containerSize` 计算 RN rpx |
| `core/src/main/java/com/hank/musicfree/core/ui/*.kt`, `feature/*/*.kt` | 修正 Compose `modifier` 参数顺序 |
| `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsContent.kt` | 避免在 composition key 中读取 `LazyListState.layoutInfo` 的频繁变化字段 |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/downloading/DownloadingScreen.kt` | `String.format` 增加显式 `Locale` |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/scanner/LocalMusicScanner.kt`, `data/.../PlaylistCoverStore.kt`, `updater/...`, `core/.../DocumentTreeDirectory.kt`, `feature/player-ui/.../DesktopLyricOverlayController.kt` | 使用 KTX `toUri()` / `toColorInt()` |
| `app/src/main/res/**`, `core/src/main/res/**` | 移动 bitmap 到 `drawable-nodpi`，补 monochrome adaptive icon，删除确认未用模板资源 |
| `app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt` | 同步资源路径 contract |
| `scripts/release/preflight.sh` | 本地 release preflight 加 `./gradlew lint --continue --no-daemon` |
| `.github/workflows/android-release-apk.yml` | tag-only release workflow 加 lint step |

## Task 1: Toolchain And Version Freshness

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `core/build.gradle.kts`
- Modify: `data/build.gradle.kts`
- Modify: `downloader/build.gradle.kts`
- Modify: `logging/build.gradle.kts`
- Modify: `player/build.gradle.kts`
- Modify: `plugin/build.gradle.kts`
- Modify: `updater/build.gradle.kts`
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/listen-stats/build.gradle.kts`
- Modify: `feature/player-ui/build.gradle.kts`
- Modify: `feature/search/build.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `AGENTS.md`
- Modify: `README.md`

- [ ] **Step 1: Ensure Android SDK 37 is available locally**

Run:

```bash
if [ -n "${ANDROID_HOME:-}" ]; then SDK_ROOT="$ANDROID_HOME"; else SDK_ROOT="$ANDROID_SDK_ROOT"; fi
if [ ! -d "$SDK_ROOT/platforms/android-37" ]; then
  "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "platforms;android-37"
fi
```

Expected: `platforms/android-37` exists. If `sdkmanager` is missing, stop and report the exact missing path; do not lower compileSdk to hide lint.

- [ ] **Step 2: Update version catalog**

Edit `gradle/libs.versions.toml`:

```toml
agp = "9.2.1"
androidxTestCore = "1.7.0"
composeBom = "2026.05.00"
coroutines = "1.11.0"
media3 = "1.10.1"
mockk = "1.14.9"
semver = "3.1.0"
orgJson = "20251224"
```

Add under `[libraries]`:

```toml
org-json = { group = "org.json", name = "json", version.ref = "orgJson" }
```

- [ ] **Step 3: Update compileSdk and targetSdk**

In every Android module, replace:

```kotlin
compileSdk {
    version = release(36) {
        minorApiLevel = 1
    }
}
```

with:

```kotlin
compileSdk = 37
```

In `app/build.gradle.kts`, replace:

```kotlin
targetSdk = 36
```

with:

```kotlin
targetSdk = 37
```

- [ ] **Step 4: Move org.json dependency to version catalog**

In `data/build.gradle.kts`, replace:

```kotlin
testImplementation("org.json:json:20231013")
```

with:

```kotlin
testImplementation(libs.org.json)
```

- [ ] **Step 5: Update build baseline docs**

In `AGENTS.md` and `README.md`, update:

```text
Target SDK：37
compileSdk：37
AGP：`9.2.1`
Compose BOM：`2026.05.00`
```

Keep Min SDK 29, Java 17, JDK 21, Gradle Wrapper 9.4.1, Kotlin 2.3.21 unchanged.

- [ ] **Step 6: Verify build model after version changes**

Run:

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If AGP / dependency upgrade breaks APIs, stop and capture the first compiler error before changing unrelated code.

- [ ] **Step 7: Commit version baseline changes**

Run:

```bash
git add gradle/libs.versions.toml */build.gradle.kts feature/*/build.gradle.kts AGENTS.md README.md
git commit -m "chore(deps): 更新 lint 发布检查构建基线"
```

## Task 2: Manifest, Permissions, And Intent Queries

**Files:**
- Modify: `player/src/main/AndroidManifest.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `core/src/main/java/com/hank/musicfree/core/permissions/AudioPermission.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/permissions/NotificationPermission.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsHelpers.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/PermissionsHelpersTest.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`

- [ ] **Step 1: Fix player network permission and service exposure**

In `player/src/main/AndroidManifest.xml`, add:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Change `PlaybackService` to:

```xml
<service
    android:name="com.hank.musicfree.player.service.PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

Reason: this app connects to the service by explicit in-app `ComponentName`; it does not currently expose Android Auto / external controller support as a documented feature.

- [ ] **Step 2: Fix manifest redundancy while preserving predictive back behavior**

In `app/src/main/AndroidManifest.xml`, remove the launcher activity label:

```xml
android:label="@string/app_name"
```

Keep application-level predictive back disabled. Add a source-level API marker to the `<application>` element:

```xml
tools:targetApi="tiramisu"
```

Keep:

```xml
android:enableOnBackInvokedCallback="false"
```

Reason: the attribute is intentionally API 33+ behavior. This is not a lint config exclusion and keeps the existing predictive-back contract.

- [ ] **Step 3: Replace Android 13 permission constants with stable strings**

In `AudioPermission.kt`, replace:

```kotlin
Manifest.permission.READ_MEDIA_AUDIO
```

with:

```kotlin
"android.permission.READ_MEDIA_AUDIO"
```

In `NotificationPermission.kt`, replace:

```kotlin
Manifest.permission.POST_NOTIFICATIONS
```

with:

```kotlin
"android.permission.POST_NOTIFICATIONS"
```

Keep `Manifest.permission.READ_EXTERNAL_STORAGE` for the pre-33 path.

- [ ] **Step 4: Remove settings overlay package query**

In `PermissionsHelpers.kt`, replace `buildOverlaySettingsIntent(context): Intent?` and `openOverlaySettings(context)` with:

```kotlin
internal fun buildOverlaySettingsIntent(context: Context): Intent {
    return Intent(
        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun openOverlaySettings(context: Context): Boolean {
    return runCatching {
        context.startActivity(buildOverlaySettingsIntent(context))
        true
    }.getOrDefault(false)
}
```

Imports must include:

```kotlin
import androidx.core.net.toUri
```

Remove unused `android.net.Uri`.

- [ ] **Step 5: Update settings overlay tests**

In `PermissionsHelpersTest.kt`, replace the package-manager mock test with a startActivity failure test:

```kotlin
@Test
fun `openOverlaySettings returns false when settings activity cannot start`() {
    val context = OverlaySettingsTestContext(
        baseContext = RuntimeEnvironment.getApplication(),
        packageNameValue = "com.example.app",
        failStartActivity = true,
    )

    assertFalse(openOverlaySettings(context))
    assertNull(context.startedIntent)
}
```

Update `OverlaySettingsTestContext` constructor and `startActivity`:

```kotlin
private class OverlaySettingsTestContext(
    baseContext: Context,
    private val packageNameValue: String,
    private val failStartActivity: Boolean = false,
) : ContextWrapper(baseContext) {
    var startedIntent: Intent? = null
        private set

    override fun getPackageName(): String = packageNameValue

    override fun startActivity(intent: Intent) {
        if (failStartActivity) {
            throw android.content.ActivityNotFoundException("no settings activity")
        }
        startedIntent = intent
    }
}
```

Remove unused `PackageManager`, Mockito imports, and `getPackageManager()` override.

- [ ] **Step 6: Remove player overlay package query**

In `PlayerScreen.kt`, replace:

```kotlin
Uri.parse("package:${context.packageName}")
```

with:

```kotlin
"package:${context.packageName}".toUri()
```

Replace the resolve check with:

```kotlin
context.startActivity(intent)
true
```

inside the existing `runCatching`, so `ActivityNotFoundException` returns `false`.

Imports must include:

```kotlin
import androidx.core.net.toUri
```

Remove unused `android.net.Uri`.

- [ ] **Step 7: Verify targeted tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*PermissionsHelpersTest' --no-daemon
./gradlew :app:testDebugUnitTest --tests '*SplashScreenResourceContractTest' --no-daemon
```

Expected: both commands pass.

- [ ] **Step 8: Commit manifest and intent fixes**

Run:

```bash
git add app/src/main/AndroidManifest.xml player/src/main/AndroidManifest.xml core/src/main/java/com/hank/musicfree/core/permissions feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsHelpers.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/PermissionsHelpersTest.kt feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt
git commit -m "fix(lint): 修复权限与 intent 查询告警"
```

## Task 3: Kotlin And Compose Source Warnings

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/theme/Rpx.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/MusicItemMoreMenu.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/PlayAllBar.kt`
- Modify: `feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/SongDetailRow.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueSheetContent.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsContent.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/DesktopLyricOverlayController.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/downloading/DownloadingScreen.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/scanner/LocalMusicScanner.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/cover/PlaylistCoverStore.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/storage/DocumentTreeDirectory.kt`
- Modify: `updater/src/main/java/com/hank/musicfree/updater/installer/InstallIntents.kt`
- Modify: `updater/src/main/java/com/hank/musicfree/updater/ui/ManualUpdateDialog.kt`
- Modify: `updater/src/main/java/com/hank/musicfree/updater/ui/UpdateDialogHost.kt`

- [ ] **Step 1: Update rpx implementation**

Replace `Rpx.kt` body with:

```kotlin
package com.hank.musicfree.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
@ReadOnlyComposable
fun rpx(value: Int): Dp {
    val minEdge = minWindowEdgeDp()
    return ((value.toFloat() / 750f) * minEdge).dp
}

@Composable
@ReadOnlyComposable
fun rpxSp(value: Int): TextUnit {
    val minEdge = minWindowEdgeDp()
    return ((value.toFloat() / 750f) * minEdge).sp
}

@Composable
@ReadOnlyComposable
private fun minWindowEdgeDp(): Float {
    val containerSize = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        min(containerSize.width, containerSize.height).toDp().value
    }
}
```

- [ ] **Step 2: Fix modifier parameter order**

Apply this signature pattern:

```kotlin
fun ExampleComposable(
    required: Type,
    modifier: Modifier = Modifier,
    optional: Type = default,
)
```

Concrete changes:

```kotlin
fun MusicItemMoreMenu(
    actions: Set<MusicItemAction>,
    isFavorite: Boolean,
    onAction: (MusicItemAction) -> Unit,
    triggerIcon: Painter,
    modifier: Modifier = Modifier,
    contentDescription: String = "更多",
)
```

```kotlin
fun PlayAllBar(
    onPlayAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    starred: Boolean? = null,
    onToggleStarred: (() -> Unit)? = null,
    showAddToPlaylist: Boolean = true,
)
```

```kotlin
fun SongDetailRow(
    song: ListenedSong,
    modifier: Modifier = Modifier,
    showFirstSeen: Boolean = false,
)
```

For private `IconTextButton`, `BasicSettingsContent`, and `SettingsScreen`, move `modifier` before the first optional parameter and keep call sites named.

- [ ] **Step 3: Fix frequently changing layout reads**

In `PlayerLyricsContent.kt`, replace the `remember` keys for `centerVisibleLine` with stable keys:

```kotlin
val centerVisibleLine by remember(document, lines) {
    derivedStateOf {
        centerVisibleLyricLine(
            lines = lines,
            visibleItems = listState.layoutInfo.visibleItemsInfo.map {
                VisibleLyricListItem(
                    index = it.index,
                    offset = it.offset,
                    size = it.size,
                )
            },
            viewportStartOffset = listState.layoutInfo.viewportStartOffset,
            viewportHeight = listState.layoutInfo.viewportSize.height,
        )
    }
}
```

The key rule is that `listState.layoutInfo.visibleItemsInfo`, `viewportStartOffset`, and `viewportSize.height` must not be read in the composition key list.

- [ ] **Step 4: Fix locale formatting**

In `DownloadingScreen.kt`, add:

```kotlin
import java.util.Locale
```

Change:

```kotlin
String.format("%.1fKB", kb)
String.format("%.1fMB", mb)
```

to:

```kotlin
String.format(Locale.US, "%.1fKB", kb)
String.format(Locale.US, "%.1fMB", mb)
```

- [ ] **Step 5: Fix obsolete SDK and KTX warnings**

Use KTX imports:

```kotlin
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
```

Concrete replacements:

```kotlin
Uri.parse(value) -> value.toUri()
Color.parseColor(raw) -> raw.toColorInt()
```

In `DesktopLyricOverlayController.kt`, replace the API check with:

```kotlin
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
```

because `minSdk` is 29.

- [ ] **Step 6: Verify source changes**

Run:

```bash
./gradlew :core:compileDebugKotlin :feature:player-ui:compileDebugKotlin :feature:home:compileDebugKotlin :feature:settings:compileDebugKotlin :updater:compileDebugKotlin :data:compileDebugKotlin --no-daemon
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

Expected: both commands pass.

- [ ] **Step 7: Commit source warning fixes**

Run:

```bash
git add core/src/main/java data/src/main/java feature/home/src/main/java feature/listen-stats/src/main/java feature/player-ui/src/main/java feature/settings/src/main/java updater/src/main/java
git commit -m "fix(lint): 清理 Kotlin 与 Compose 源码告警"
```

## Task 4: Resource Warnings

**Files:**
- Move: `app/src/main/res/drawable/splashscreen_image.png` to `app/src/main/res/drawable-nodpi/splashscreen_image.png`
- Move: `app/src/main/res/drawable/spashscreen_branding_image.png` to `app/src/main/res/drawable-nodpi/spashscreen_branding_image.png`
- Delete: `app/src/main/res/drawable/splashscreen.xml`
- Move: `core/src/main/res/drawable/album_default.jpg` to `core/src/main/res/drawable-nodpi/album_default.jpg`
- Move: `core/src/main/res/drawable/ic_quality_*.png` to `core/src/main/res/drawable-nodpi/`
- Move: `core/src/main/res/drawable/ic_rate_*.png` to `core/src/main/res/drawable-nodpi/`
- Move: `app/src/main/res/mipmap-anydpi-v26/*.xml` to `app/src/main/res/mipmap-anydpi/*.xml`
- Move: `app/src/debug/res/mipmap-anydpi-v26/*.xml` to `app/src/debug/res/mipmap-anydpi/*.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- Modify: `app/src/debug/res/mipmap-anydpi/ic_launcher.xml`
- Modify: `app/src/debug/res/mipmap-anydpi/ic_launcher_round.xml`
- Modify: `app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt`

- [ ] **Step 1: Move density-independent bitmap resources**

Run:

```bash
mkdir -p app/src/main/res/drawable-nodpi core/src/main/res/drawable-nodpi
git mv app/src/main/res/drawable/splashscreen_image.png app/src/main/res/drawable-nodpi/splashscreen_image.png
git mv app/src/main/res/drawable/spashscreen_branding_image.png app/src/main/res/drawable-nodpi/spashscreen_branding_image.png
git mv core/src/main/res/drawable/album_default.jpg core/src/main/res/drawable-nodpi/album_default.jpg
git mv core/src/main/res/drawable/ic_quality_high.png core/src/main/res/drawable-nodpi/ic_quality_high.png
git mv core/src/main/res/drawable/ic_quality_low.png core/src/main/res/drawable-nodpi/ic_quality_low.png
git mv core/src/main/res/drawable/ic_quality_standard.png core/src/main/res/drawable-nodpi/ic_quality_standard.png
git mv core/src/main/res/drawable/ic_quality_super.png core/src/main/res/drawable-nodpi/ic_quality_super.png
git mv core/src/main/res/drawable/ic_rate_050.png core/src/main/res/drawable-nodpi/ic_rate_050.png
git mv core/src/main/res/drawable/ic_rate_075.png core/src/main/res/drawable-nodpi/ic_rate_075.png
git mv core/src/main/res/drawable/ic_rate_100.png core/src/main/res/drawable-nodpi/ic_rate_100.png
git mv core/src/main/res/drawable/ic_rate_125.png core/src/main/res/drawable-nodpi/ic_rate_125.png
git mv core/src/main/res/drawable/ic_rate_150.png core/src/main/res/drawable-nodpi/ic_rate_150.png
git mv core/src/main/res/drawable/ic_rate_175.png core/src/main/res/drawable-nodpi/ic_rate_175.png
git mv core/src/main/res/drawable/ic_rate_200.png core/src/main/res/drawable-nodpi/ic_rate_200.png
```

Resource names remain unchanged, so `R.drawable.*` call sites stay stable.

- [ ] **Step 2: Remove unused splash wrapper and template colors**

Run:

```bash
git rm app/src/main/res/drawable/splashscreen.xml
```

Edit `app/src/main/res/values/colors.xml` so it only keeps used colors:

```xml
<resources>
    <color name="splashscreen_background">#27282C</color>
</resources>
```

- [ ] **Step 3: Move adaptive icon XML out of v26 directories**

Run:

```bash
mkdir -p app/src/main/res/mipmap-anydpi app/src/debug/res/mipmap-anydpi
git mv app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi/ic_launcher.xml
git mv app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
git mv app/src/debug/res/mipmap-anydpi-v26/ic_launcher.xml app/src/debug/res/mipmap-anydpi/ic_launcher.xml
git mv app/src/debug/res/mipmap-anydpi-v26/ic_launcher_round.xml app/src/debug/res/mipmap-anydpi/ic_launcher_round.xml
rmdir app/src/main/res/mipmap-anydpi-v26 app/src/debug/res/mipmap-anydpi-v26
```

- [ ] **Step 4: Add monochrome icon layer**

For main icon XML files, use:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

For debug icon XML files, use:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground_debug"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground_debug"/>
</adaptive-icon>
```

- [ ] **Step 5: Update splash resource contract**

In `SplashScreenResourceContractTest.kt`, keep theme assertions as `@drawable/splashscreen_image` and `@drawable/spashscreen_branding_image`; Android resource lookup is folder-independent.

Replace copied resource list entries with explicit RN-to-Android mapping:

```kotlin
val copiedResources = listOf(
    "res/drawable/splashscreen_image.png" to "res/drawable-nodpi/splashscreen_image.png",
    "res/drawable/spashscreen_branding_image.png" to "res/drawable-nodpi/spashscreen_branding_image.png",
    "res/values/ic_launcher_background.xml" to "res/values/ic_launcher_background.xml",
    "res/mipmap-anydpi-v26/ic_launcher.xml" to "res/mipmap-anydpi/ic_launcher.xml",
    "res/mipmap-anydpi-v26/ic_launcher_round.xml" to "res/mipmap-anydpi/ic_launcher_round.xml",
)
```

Then compare `expected = rnMain.resolve(rnRelativePath)` and `actual = appMain.resolve(androidRelativePath)`.

Do not compare adaptive icon XML byte-for-byte after adding `<monochrome>`; instead assert the XML contains the same background and foreground plus a monochrome entry.

- [ ] **Step 6: Verify resources and contract**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*SplashScreenResourceContractTest' --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

Expected: both commands pass.

- [ ] **Step 7: Commit resource fixes**

Run:

```bash
git add app/src/main/res app/src/debug/res core/src/main/res app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt
git commit -m "fix(lint): 清理资源目录与启动图标告警"
```

## Task 5: Release-Only Lint Gate

**Files:**
- Modify: `scripts/release/preflight.sh`
- Modify: `.github/workflows/android-release-apk.yml`
- Modify: `RELEASE.md`

- [ ] **Step 1: Add local preflight lint**

In `scripts/release/preflight.sh`, after version consistency check and before `[dry] Build Release APK`, add:

```bash
echo "[dry] Run release lint"
./gradlew lint --continue --no-daemon
```

- [ ] **Step 2: Add tag-only workflow lint**

In `.github/workflows/android-release-apk.yml`, after `Set up Android SDK` and before `Validate release secrets`, add:

```yaml
      - name: Run release lint
        if: github.event_name == 'push' && github.ref_type == 'tag'
        run: ./gradlew lint --continue --no-daemon
```

Do not add this step to `schedule` or `workflow_dispatch`.

- [ ] **Step 3: Update release docs**

In `RELEASE.md`, under local dry-run CI steps, add a section:

````markdown
### `[dry] Run release lint`

```bash
./gradlew lint --continue --no-daemon
```

发布 tag 前必须通过 lint。日常 dev harness 不默认运行 lint；lint 仅作为发布性检查。
````

In "日常发布步骤", keep `bash scripts/release/preflight.sh v1.2.3` as the single local command; mention that it now includes lint.

- [ ] **Step 4: Verify shell and workflow syntax**

Run:

```bash
bash -n scripts/release/preflight.sh
ruby -e 'require "yaml"; YAML.load_file(".github/workflows/android-release-apk.yml"); puts "workflow yaml ok"'
```

Expected: `workflow yaml ok`.

- [ ] **Step 5: Commit release gate**

Run:

```bash
git add scripts/release/preflight.sh .github/workflows/android-release-apk.yml RELEASE.md
git commit -m "ci(release): 发布检查加入 lint 守门"
```

## Task 6: Full Verification And Cleanup Readiness

**Files:**
- No planned edits. This task verifies the integrated branch.

- [ ] **Step 1: Run full lint**

Run:

```bash
./gradlew lint --continue --no-daemon
```

Expected: `BUILD SUCCESSFUL` and no current lint issues in module reports.

- [ ] **Step 2: Run debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run release preflight**

Run:

```bash
tag="v$(awk -F= '/^versionName/{print $2}' version.properties | tr -d '[:space:]')"
bash scripts/release/preflight.sh "$tag"
```

Expected: lint step passes. If local release signing env is absent, preflight may warn and skip real release APK build as designed; do not treat missing signing env as failure for this lint task.

- [ ] **Step 4: Run dev harness smoke**

Run:

```bash
bash scripts/dev-harness/check.sh --allow-empty-symlinks
```

Expected: `Grep guards`, compile-only test sources, and contract tests pass. This confirms lint was not added to daily harness.

- [ ] **Step 5: Check no lint hiding was introduced**

Run:

```bash
rg -n "lint-baseline|baseline =|disable\\(|ignore\\(|checkOnly|abortOnError|warningsAsErrors|@SuppressLint|tools:ignore" . --glob '!docs/superpowers/**'
```

Expected: no new lint hiding for this task. Existing unrelated matches, if any, must be inspected before final summary.

- [ ] **Step 6: Final diff checks**

Run:

```bash
git diff --check
git status --short --branch
```

Expected: no whitespace errors. Working tree may be clean if all task commits were made.

- [ ] **Step 7: Prepare merge-back summary**

Summarize:

```text
- lint issues fixed by category
- release preflight/workflow lint gate added
- commands run and outcomes
- any version upgrade caveats
```

Do not merge back to `main` until the user asks for merge-back or approves final integration.

## Self-Review

- Spec coverage: all spec goals map to Task 1 through Task 6.
- Placeholder scan: no placeholder tasks remain; every task has exact file paths and commands.
- Scope check: version upgrade, source cleanup, resource cleanup, and release gate are coupled by the no-lint-config constraint; keeping them in one plan is appropriate.
- Risk notes: compileSdk 37 requires local SDK installation; targetSdk 37 and dependency upgrades need full debug build and targeted tests before merge-back.
