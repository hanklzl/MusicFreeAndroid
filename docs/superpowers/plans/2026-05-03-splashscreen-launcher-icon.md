# SplashScreen Launcher Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an AndroidX Jetpack SplashScreen and replace launcher assets so the Android native app matches the original RN Android startup visual contract.

**Architecture:** Keep the feature entirely in the `:app` module. AndroidX `core-splashscreen` owns the system splash before Compose starts; `MainActivity` installs it before `super.onCreate`, then the app falls through to the existing Compose content. RN Android resources are copied byte-for-byte where possible, with a source-level contract test protecting the manifest, theme, dependency, and resource layout.

**Tech Stack:** AndroidX `core-splashscreen` 1.2.0, Kotlin, Android resource qualifiers, JUnit4 source-contract tests, Gradle version catalog.

---

## Document Status

- Status: implementation plan snapshot.
- Scope: execute only after reading `docs/superpowers/specs/2026-05-03-splashscreen-launcher-icon-design.md`.
- Source references must remain relative. The RN source is `../MusicFree/android/app/src/main` from the main worktree and `../../MusicFree/android/app/src/main` from `.worktrees/splashscreen-launcher-icon`.

## File Map

- Create `app/src/test/java/com/zili/android/musicfreeandroid/SplashScreenResourceContractTest.kt`
  - JVM source-contract test for dependency aliases, manifest theme wiring, theme XML, activity call order, copied RN assets, and cleanup of default Android template launcher resources.
- Modify `gradle/libs.versions.toml`
  - Add `coreSplashscreen = "1.2.0"` and `androidx-core-splashscreen`.
- Modify `app/build.gradle.kts`
  - Add `implementation(libs.androidx.core.splashscreen)`.
- Modify `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
  - Import and call `installSplashScreen()` before `super.onCreate(savedInstanceState)`.
- Modify `app/src/main/AndroidManifest.xml`
  - Point `MainActivity` to `@style/Theme.MusicFreeAndroid.Splash`.
- Modify `app/src/main/res/values/themes.xml`
  - Add the base splash theme with compat attributes and `postSplashScreenTheme`.
- Create `app/src/main/res/values-v31/themes.xml`
  - Add the Android 12+ style variant with platform branding image.
- Modify `app/src/main/res/values/colors.xml`
  - Add `splashscreen_background`.
- Create or replace RN-copied resources under `app/src/main/res/drawable/`, `app/src/main/res/mipmap-*`, `app/src/main/res/mipmap-anydpi-v26/`, `app/src/main/res/values/ic_launcher_background.xml`, and `app/src/main/ic_launcher-playstore.png`.
- Delete old template resources:
  - `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
  - `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
  - `app/src/main/res/drawable/ic_launcher_background.xml`
  - `app/src/main/res/drawable/ic_launcher_foreground.xml`

## Task 1: Prepare An Isolated Worktree

**Files:**
- Read: `.gitignore`
- Create: `.worktrees/splashscreen-launcher-icon`

- [ ] **Step 1: Confirm `.worktrees/` is ignored**

Run:

```bash
rg -n '^\.worktrees/$' .gitignore
```

Expected:

```text
12:.worktrees/
```

- [ ] **Step 2: Create the implementation worktree**

Run from the main repository root:

```bash
git worktree add .worktrees/splashscreen-launcher-icon -b feat/splashscreen-launcher-icon
```

Expected:

```text
Preparing worktree (new branch 'feat/splashscreen-launcher-icon')
HEAD is now at
```

- [ ] **Step 3: Enter the worktree**

Run:

```bash
cd .worktrees/splashscreen-launcher-icon
git status --short
```

Expected: no output from `git status --short`.

## Task 2: Add A Failing Source Contract Test

**Files:**
- Create: `app/src/test/java/com/zili/android/musicfreeandroid/SplashScreenResourceContractTest.kt`

- [ ] **Step 1: Write the failing contract test**

Create `app/src/test/java/com/zili/android/musicfreeandroid/SplashScreenResourceContractTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SplashScreenResourceContractTest {

    private val projectRoot: Path = locateProjectRoot()
    private val appMain: Path = projectRoot.resolve("app/src/main")
    private val rnMain: Path = locateRnMain(projectRoot)

    @Test
    fun `androidx splash dependency is declared in version catalog and app module`() {
        assertContains(
            projectRoot.resolve("gradle/libs.versions.toml"),
            """coreSplashscreen = "1.2.0"""",
        )
        assertContains(
            projectRoot.resolve("gradle/libs.versions.toml"),
            """androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }""",
        )
        assertContains(
            projectRoot.resolve("app/build.gradle.kts"),
            "implementation(libs.androidx.core.splashscreen)",
        )
    }

    @Test
    fun `main activity installs AndroidX splash before super onCreate`() {
        val source = Files.readString(
            projectRoot.resolve("app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt"),
        )

        assertTrue(
            "MainActivity should import AndroidX installSplashScreen",
            source.contains("import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen"),
        )

        val installIndex = source.indexOf("installSplashScreen()")
        val superIndex = source.indexOf("super.onCreate(savedInstanceState)")

        assertTrue("MainActivity should call installSplashScreen()", installIndex >= 0)
        assertTrue("MainActivity should call super.onCreate(savedInstanceState)", superIndex >= 0)
        assertTrue(
            "installSplashScreen() must run before super.onCreate(savedInstanceState)",
            installIndex < superIndex,
        )
    }

    @Test
    fun `manifest points launcher activity at splash theme`() {
        val manifest = appMain.resolve("AndroidManifest.xml")

        assertContains(manifest, """android:theme="@style/Theme.MusicFreeAndroid"""")
        assertContains(manifest, """android:icon="@mipmap/ic_launcher"""")
        assertContains(manifest, """android:roundIcon="@mipmap/ic_launcher_round"""")
        assertContains(manifest, """android:theme="@style/Theme.MusicFreeAndroid.Splash"""")
    }

    @Test
    fun `splash themes match the AndroidX and RN visual contract`() {
        val baseTheme = appMain.resolve("res/values/themes.xml")
        val v31Theme = appMain.resolve("res/values-v31/themes.xml")

        assertContains(baseTheme, """<style name="Theme.MusicFreeAndroid" parent="android:Theme.Material.Light.NoActionBar" />""")
        assertContains(baseTheme, """<style name="Theme.MusicFreeAndroid.Splash" parent="Theme.SplashScreen">""")
        assertContains(baseTheme, """<item name="windowSplashScreenBackground">@color/splashscreen_background</item>""")
        assertContains(baseTheme, """<item name="windowSplashScreenAnimatedIcon">@drawable/splashscreen_image</item>""")
        assertContains(baseTheme, """<item name="postSplashScreenTheme">@style/Theme.MusicFreeAndroid</item>""")

        assertContains(v31Theme, """<style name="Theme.MusicFreeAndroid.Splash" parent="Theme.SplashScreen">""")
        assertContains(v31Theme, """<item name="windowSplashScreenBackground">@color/splashscreen_background</item>""")
        assertContains(v31Theme, """<item name="windowSplashScreenAnimatedIcon">@drawable/splashscreen_image</item>""")
        assertContains(v31Theme, """<item name="android:windowSplashScreenBrandingImage">@drawable/spashscreen_branding_image</item>""")
        assertContains(v31Theme, """<item name="postSplashScreenTheme">@style/Theme.MusicFreeAndroid</item>""")

        assertContains(
            appMain.resolve("res/values/colors.xml"),
            """<color name="splashscreen_background">#27282C</color>""",
        )
        assertContains(
            appMain.resolve("res/values/ic_launcher_background.xml"),
            """<color name="ic_launcher_background">#27282C</color>""",
        )
    }

    @Test
    fun `RN splash and launcher resources are copied byte for byte`() {
        val copiedResources = listOf(
            "res/drawable/splashscreen_image.png",
            "res/drawable/spashscreen_branding_image.png",
            "res/drawable/splashscreen.xml",
            "res/values/ic_launcher_background.xml",
            "res/mipmap-anydpi-v26/ic_launcher.xml",
            "res/mipmap-anydpi-v26/ic_launcher_round.xml",
            "res/mipmap-mdpi/ic_launcher.webp",
            "res/mipmap-mdpi/ic_launcher_round.webp",
            "res/mipmap-mdpi/ic_launcher_foreground.webp",
            "res/mipmap-hdpi/ic_launcher.webp",
            "res/mipmap-hdpi/ic_launcher_round.webp",
            "res/mipmap-hdpi/ic_launcher_foreground.webp",
            "res/mipmap-xhdpi/ic_launcher.webp",
            "res/mipmap-xhdpi/ic_launcher_round.webp",
            "res/mipmap-xhdpi/ic_launcher_foreground.webp",
            "res/mipmap-xxhdpi/ic_launcher.webp",
            "res/mipmap-xxhdpi/ic_launcher_round.webp",
            "res/mipmap-xxhdpi/ic_launcher_foreground.webp",
            "res/mipmap-xxxhdpi/ic_launcher.webp",
            "res/mipmap-xxxhdpi/ic_launcher_round.webp",
            "res/mipmap-xxxhdpi/ic_launcher_foreground.webp",
            "ic_launcher-playstore.png",
        )

        copiedResources.forEach { relativePath ->
            val expected = rnMain.resolve(relativePath)
            val actual = appMain.resolve(relativePath)

            assertTrue("RN reference resource should exist: $expected", Files.exists(expected))
            assertTrue("Android resource should exist: $actual", Files.exists(actual))
            assertArrayEquals(
                "Android resource should match RN reference byte-for-byte: $relativePath",
                Files.readAllBytes(expected),
                Files.readAllBytes(actual),
            )
        }
    }

    @Test
    fun `default Android template launcher resources are removed`() {
        val removedTemplateResources = listOf(
            "res/mipmap-anydpi/ic_launcher.xml",
            "res/mipmap-anydpi/ic_launcher_round.xml",
            "res/drawable/ic_launcher_background.xml",
            "res/drawable/ic_launcher_foreground.xml",
        )

        removedTemplateResources.forEach { relativePath ->
            val actual = appMain.resolve(relativePath)
            assertFalse("Template launcher resource should be removed: $actual", Files.exists(actual))
        }
    }

    private fun assertContains(path: Path, expected: String) {
        assertTrue("Expected file to exist: $path", Files.exists(path))
        val text = Files.readString(path)
        assertTrue(
            "Expected $path to contain:\n$expected",
            text.contains(expected),
        )
    }

    private fun locateProjectRoot(): Path {
        val userDir = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOfNotNull(userDir, userDir.parent)

        return candidates.firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate project root from $userDir")
    }

    private fun locateRnMain(projectRoot: Path): Path {
        val candidates = listOf(
            projectRoot.resolve("../MusicFree/android/app/src/main").normalize(),
            projectRoot.resolve("../../MusicFree/android/app/src/main").normalize(),
        )

        return candidates.firstOrNull { Files.isDirectory(it) }
            ?: error("Could not locate RN Android source root from $projectRoot")
    }
}
```

- [ ] **Step 2: Run the contract test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.SplashScreenResourceContractTest"
```

Expected: `FAILED` with assertion messages such as `Expected ... libs.versions.toml to contain: coreSplashscreen = "1.2.0"` or `Expected file to exist: .../res/values-v31/themes.xml`.

## Task 3: Wire AndroidX SplashScreen

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-v31/themes.xml`
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Add the SplashScreen version catalog entries**

In `gradle/libs.versions.toml`, add the version beside the other AndroidX versions:

```toml
coreSplashscreen = "1.2.0"
```

Add the library alias beside `androidx-core-ktx`:

```toml
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }
```

- [ ] **Step 2: Add the app dependency**

In `app/build.gradle.kts`, add the dependency in the AndroidX block:

```kotlin
implementation(libs.androidx.core.splashscreen)
```

The AndroidX dependency block should include:

```kotlin
// AndroidX
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.core.splashscreen)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.activity.compose)
```

- [ ] **Step 3: Install the splash screen before `super.onCreate`**

Update the imports in `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`:

```kotlin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
```

Update `onCreate`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
        MusicFreeTheme {
            val navController = rememberNavController()
            val homeSystemActionHandler = remember(this, playerController) {
                AndroidHomeSystemActionHandler(
                    activity = this,
                    playerController = playerController,
                )
            }
            val currentBackStack by navController.currentBackStackEntryAsState()
            val destination = currentBackStack?.destination

            val isHomeRoute = destination?.hasRoute<HomeRoute>() == true
            val isPlayerRoute = destination?.hasRoute<PlayerRoute>() == true
            val isSearchRoute = destination?.hasRoute<SearchRoute>() == true
            val showMiniPlayer = destination != null && !isPlayerRoute
            // 搜索页和播放器页自行处理顶部沉浸式，其余页面统一加顶部安全区
            val applyTopSafeInset = !isPlayerRoute && !isSearchRoute

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
                bottomBar = {
                    if (showMiniPlayer) {
                        MiniPlayer(
                            onNavigateToPlayer = {
                                navController.navigate(PlayerRoute)
                            },
                        )
                    }
                },
            ) { innerPadding ->
                AppNavHost(
                    navController = navController,
                    homeSystemActionHandler = homeSystemActionHandler,
                    modifier = Modifier
                        .padding(innerPadding)
                        .then(
                            if (applyTopSafeInset) {
                                Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Point `MainActivity` at the splash theme**

In `app/src/main/AndroidManifest.xml`, change only the activity theme:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:theme="@style/Theme.MusicFreeAndroid.Splash">
```

Leave the application theme as:

```xml
android:theme="@style/Theme.MusicFreeAndroid"
```

- [ ] **Step 5: Add the base SplashScreen theme**

Replace `app/src/main/res/values/themes.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="Theme.MusicFreeAndroid" parent="android:Theme.Material.Light.NoActionBar" />

    <style name="Theme.MusicFreeAndroid.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splashscreen_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splashscreen_image</item>
        <item name="postSplashScreenTheme">@style/Theme.MusicFreeAndroid</item>
    </style>
</resources>
```

- [ ] **Step 6: Add the Android 12+ branding theme**

Create `app/src/main/res/values-v31/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="Theme.MusicFreeAndroid.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splashscreen_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splashscreen_image</item>
        <item name="android:windowSplashScreenBrandingImage">@drawable/spashscreen_branding_image</item>
        <item name="postSplashScreenTheme">@style/Theme.MusicFreeAndroid</item>
    </style>
</resources>
```

- [ ] **Step 7: Add the RN splash background color**

In `app/src/main/res/values/colors.xml`, keep the existing colors and add:

```xml
<color name="splashscreen_background">#27282C</color>
```

The file should contain:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="splashscreen_background">#27282C</color>
</resources>
```

- [ ] **Step 8: Run the contract test and verify resource references are now the remaining blocker**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.SplashScreenResourceContractTest"
```

Expected: `FAILED`. The failure may be an Android resource linking error for missing `@drawable/splashscreen_image` or `@drawable/spashscreen_branding_image`, because RN resources are copied in the next task. Dependency, activity, manifest, theme, and color text changes should be present in the diff before proceeding.

## Task 4: Copy RN Resources And Remove Template Launcher Assets

**Files:**
- Create or replace: `app/src/main/res/drawable/splashscreen_image.png`
- Create or replace: `app/src/main/res/drawable/spashscreen_branding_image.png`
- Create or replace: `app/src/main/res/drawable/splashscreen.xml`
- Create: `app/src/main/res/values/ic_launcher_background.xml`
- Create or replace: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create or replace: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Replace: `app/src/main/res/mipmap-mdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp`
- Create: `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.webp`
- Replace: `app/src/main/res/mipmap-hdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp`
- Create: `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp`
- Replace: `app/src/main/res/mipmap-xhdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp`
- Create: `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.webp`
- Replace: `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp`
- Create: `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp`
- Replace: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`
- Replace: `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp`
- Create: `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp`
- Create: `app/src/main/ic_launcher-playstore.png`
- Delete: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Delete: `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- Delete: `app/src/main/res/drawable/ic_launcher_background.xml`
- Delete: `app/src/main/res/drawable/ic_launcher_foreground.xml`

- [ ] **Step 1: Resolve the RN source root**

Run from the implementation worktree root:

```bash
RN_SRC="../MusicFree/android/app/src/main"
if [ ! -d "$RN_SRC" ]; then RN_SRC="../../MusicFree/android/app/src/main"; fi
test -d "$RN_SRC"
```

Expected: command exits with status `0`.

- [ ] **Step 2: Create destination directories**

Run:

```bash
mkdir -p app/src/main/res/drawable
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/mipmap-anydpi-v26
mkdir -p app/src/main/res/mipmap-mdpi
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/main/res/mipmap-xhdpi
mkdir -p app/src/main/res/mipmap-xxhdpi
mkdir -p app/src/main/res/mipmap-xxxhdpi
```

Expected: no output.

- [ ] **Step 3: Copy splash resources**

Run:

```bash
cp "$RN_SRC/res/drawable/splashscreen_image.png" app/src/main/res/drawable/splashscreen_image.png
cp "$RN_SRC/res/drawable/spashscreen_branding_image.png" app/src/main/res/drawable/spashscreen_branding_image.png
cp "$RN_SRC/res/drawable/splashscreen.xml" app/src/main/res/drawable/splashscreen.xml
```

Expected: no output.

- [ ] **Step 4: Copy adaptive icon XML and launcher background color**

Run:

```bash
cp "$RN_SRC/res/mipmap-anydpi-v26/ic_launcher.xml" app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
cp "$RN_SRC/res/mipmap-anydpi-v26/ic_launcher_round.xml" app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
cp "$RN_SRC/res/values/ic_launcher_background.xml" app/src/main/res/values/ic_launcher_background.xml
```

Expected: no output.

- [ ] **Step 5: Copy density-specific launcher images**

Run:

```bash
cp "$RN_SRC/res/mipmap-mdpi/ic_launcher.webp" app/src/main/res/mipmap-mdpi/ic_launcher.webp
cp "$RN_SRC/res/mipmap-mdpi/ic_launcher_round.webp" app/src/main/res/mipmap-mdpi/ic_launcher_round.webp
cp "$RN_SRC/res/mipmap-mdpi/ic_launcher_foreground.webp" app/src/main/res/mipmap-mdpi/ic_launcher_foreground.webp
cp "$RN_SRC/res/mipmap-hdpi/ic_launcher.webp" app/src/main/res/mipmap-hdpi/ic_launcher.webp
cp "$RN_SRC/res/mipmap-hdpi/ic_launcher_round.webp" app/src/main/res/mipmap-hdpi/ic_launcher_round.webp
cp "$RN_SRC/res/mipmap-hdpi/ic_launcher_foreground.webp" app/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp
cp "$RN_SRC/res/mipmap-xhdpi/ic_launcher.webp" app/src/main/res/mipmap-xhdpi/ic_launcher.webp
cp "$RN_SRC/res/mipmap-xhdpi/ic_launcher_round.webp" app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp
cp "$RN_SRC/res/mipmap-xhdpi/ic_launcher_foreground.webp" app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.webp
cp "$RN_SRC/res/mipmap-xxhdpi/ic_launcher.webp" app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
cp "$RN_SRC/res/mipmap-xxhdpi/ic_launcher_round.webp" app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp
cp "$RN_SRC/res/mipmap-xxhdpi/ic_launcher_foreground.webp" app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp
cp "$RN_SRC/res/mipmap-xxxhdpi/ic_launcher.webp" app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp
cp "$RN_SRC/res/mipmap-xxxhdpi/ic_launcher_round.webp" app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp
cp "$RN_SRC/res/mipmap-xxxhdpi/ic_launcher_foreground.webp" app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp
```

Expected: no output.

- [ ] **Step 6: Copy the Play Store icon**

Run:

```bash
cp "$RN_SRC/ic_launcher-playstore.png" app/src/main/ic_launcher-playstore.png
```

Expected: no output.

- [ ] **Step 7: Remove old Android template launcher resources**

Run:

```bash
git rm -f app/src/main/res/mipmap-anydpi/ic_launcher.xml
git rm -f app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
git rm -f app/src/main/res/drawable/ic_launcher_background.xml
git rm -f app/src/main/res/drawable/ic_launcher_foreground.xml
```

Expected:

```text
rm 'app/src/main/res/mipmap-anydpi/ic_launcher.xml'
rm 'app/src/main/res/mipmap-anydpi/ic_launcher_round.xml'
rm 'app/src/main/res/drawable/ic_launcher_background.xml'
rm 'app/src/main/res/drawable/ic_launcher_foreground.xml'
```

- [ ] **Step 8: Run the contract test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.SplashScreenResourceContractTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 5: Build And Package-Inspect The APK

**Files:**
- Build output: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Build the debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Inspect the APK resource list**

Run:

```bash
zipinfo -1 app/build/outputs/apk/debug/app-debug.apk > /tmp/musicfree-android-apk-files.txt
rg "splashscreen_image|spashscreen_branding_image|ic_launcher" /tmp/musicfree-android-apk-files.txt
```

Expected: output includes entries for compiled splashscreen image, branding image, and launcher resources. `ic_launcher-playstore.png` is a store asset under `app/src/main/` and is verified by the source contract test rather than by APK resource packaging.

- [ ] **Step 3: Run the existing MainActivity startup instrumentation test when a device is available**

Run:

```bash
adb devices
```

Expected with a device or emulator:

```text
List of devices attached
emulator-5554	device
```

Then run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.MainActivityStartupTest
```

Expected:

```text
BUILD SUCCESSFUL
```

If `adb devices` shows no attached device, do not claim runtime verification. Record that runtime verification remains pending and continue with static/build verification only.

## Task 6: Runtime Visual Verification

**Files:**
- Read: `app/build/outputs/apk/debug/app-debug.apk`
- Optional evidence output: `/tmp/musicfree-android-launch.png`

- [ ] **Step 1: Install the debug APK on a device or emulator**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected:

```text
Success
```

- [ ] **Step 2: Cold start the app**

Run:

```bash
adb shell am force-stop com.zili.android.musicfreeandroid
adb shell am start -W -n com.zili.android.musicfreeandroid/.MainActivity
```

Expected output includes:

```text
Status: ok
Activity: com.zili.android.musicfreeandroid/.MainActivity
```

- [ ] **Step 3: Capture visual evidence after launch**

Run:

```bash
adb shell screencap -p /sdcard/musicfree-android-launch.png
adb pull /sdcard/musicfree-android-launch.png /tmp/musicfree-android-launch.png
```

Expected:

```text
/sdcard/musicfree-android-launch.png: 1 file pulled
```

Manual verification during Step 2: the cold start should show the RN-style `#27282C` splash with the RN startup icon and branding image before the existing Compose home UI appears. The captured image may show the home UI if the device is fast; do not use that as proof of the transient splash unless the splash was observed during launch.

## Task 7: Final Checks And Commit

**Files:**
- All files changed by Tasks 2-4.

- [ ] **Step 1: Run the focused test again**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.SplashScreenResourceContractTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run the debug build again**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Check the git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: changes are limited to `:app` splash/icon resources, app Gradle/catalog wiring, `MainActivity`, manifest/theme/color resources, and the new source contract test.

- [ ] **Step 4: Commit the implementation**

Run:

```bash
git add app/src/test/java/com/zili/android/musicfreeandroid/SplashScreenResourceContractTest.kt
git add gradle/libs.versions.toml app/build.gradle.kts
git add app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt
git add app/src/main/AndroidManifest.xml
git add app/src/main/res/values/themes.xml app/src/main/res/values-v31/themes.xml app/src/main/res/values/colors.xml app/src/main/res/values/ic_launcher_background.xml
git add app/src/main/res/drawable/splashscreen_image.png app/src/main/res/drawable/spashscreen_branding_image.png app/src/main/res/drawable/splashscreen.xml
git add app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
git add app/src/main/res/mipmap-mdpi app/src/main/res/mipmap-hdpi app/src/main/res/mipmap-xhdpi app/src/main/res/mipmap-xxhdpi app/src/main/res/mipmap-xxxhdpi
git add app/src/main/ic_launcher-playstore.png
git add -u app/src/main/res
git commit -m "feat(app): add RN splashscreen launcher assets"
```

Expected:

```text
feat(app): add RN splashscreen launcher assets
```

## Self-Review Notes

- Spec coverage: AndroidX dependency, non-Compose splash, RN resource copy, launcher cleanup, manifest/theme wiring, build verification, and runtime visual verification are all mapped to tasks.
- Placeholder scan: no unspecified implementation steps remain.
- Type consistency: the planned Gradle alias `androidx-core-splashscreen` maps to `libs.androidx.core.splashscreen`; the splash theme name is consistently `Theme.MusicFreeAndroid.Splash`; the RN misspelled branding resource name remains `spashscreen_branding_image` to match the source resource.
