# Milestone 1: Project Scaffolding & Multi-Module Setup

> 文档状态：历史记录（执行快照）
> 适用范围：当时阶段的实施计划与执行上下文。
> 直接执行：否
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 备注：仅用于回溯，不代表当前仓库可直接执行。
> 最后校验：2026-04-11


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up a multi-module Gradle project with Hilt DI, Compose Navigation, Material3 theme (matching original MusicFree), and placeholder screens for all feature modules.

**Architecture:** Multi-module MVVM with unidirectional dependencies: `:app` → `:feature:*` → `:data`, `:player`, `:plugin` → `:core`. Hilt for DI, Compose Navigation with type-safe routes, custom MusicFreeColors theme system extending Material3.

**Tech Stack:** AGP 9.2.0-alpha04, Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01), Hilt 2.56.2, KSP, Compose Navigation, kotlinx-serialization

**Spec:** `docs/superpowers/specs/2026-03-19-musicfree-android-native-rewrite-design.md`

---

## File Map

### Files to Create

```
core/
  build.gradle.kts
  src/main/java/com/hank/musicfree/core/
    theme/
      MusicFreeColors.kt          → Custom semantic colors (17+ colors, light/dark)
      MusicFreeTheme.kt           → CompositionLocal provider, wraps MaterialTheme
      Dimensions.kt               → Font sizes, icon sizes, animation durations
      Rpx.kt                      → Responsive pixel utility
    navigation/
      Routes.kt                   → All @Serializable route definitions

data/
  build.gradle.kts                → Empty library module scaffold

player/
  build.gradle.kts                → Empty library module scaffold

plugin/
  build.gradle.kts                → Empty library module scaffold

feature/
  home/
    build.gradle.kts
    src/main/java/com/hank/musicfree/feature/home/
      HomeScreen.kt               → Placeholder screen composable
      navigation/
        HomeNavigation.kt         → NavGraphBuilder extension

  player-ui/
    build.gradle.kts
    src/main/java/com/hank/musicfree/feature/playerui/
      PlayerScreen.kt             → Placeholder screen composable
      navigation/
        PlayerNavigation.kt       → NavGraphBuilder extension

  search/
    build.gradle.kts
    src/main/java/com/hank/musicfree/feature/search/
      SearchScreen.kt             → Placeholder screen composable
      navigation/
        SearchNavigation.kt       → NavGraphBuilder extension

  settings/
    build.gradle.kts
    src/main/java/com/hank/musicfree/feature/settings/
      SettingsScreen.kt           → Placeholder screen composable
      navigation/
        SettingsNavigation.kt     → NavGraphBuilder extension

app/
  src/main/java/com/hank/musicfree/
    MusicFreeApplication.kt       → @HiltAndroidApp Application class
    navigation/
      AppNavHost.kt               → NavHost assembling all feature screens
  src/androidTest/java/com/hank/musicfree/
    HiltDiTest.kt                 → Verify DI graph completeness
  src/test/java/com/hank/musicfree/
    RoutesTest.kt                 → Route serialization test
```

### Files to Modify

```
gradle/libs.versions.toml        → Add Hilt, KSP, Navigation, Serialization versions
build.gradle.kts (root)           → Declare new plugins with apply false
gradle.properties                 → Add Hilt/AndroidX flags
settings.gradle.kts               → Include all new modules
app/build.gradle.kts              → Add Hilt, KSP, Navigation, module dependencies
app/src/main/AndroidManifest.xml  → Register MusicFreeApplication
app/src/main/java/.../MainActivity.kt → Replace with Hilt + NavHost + Scaffold
```

---

## Task 1: Version Catalog and Root Build Config

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `gradle.properties`

- [ ] **Step 1: Update version catalog**

Add Hilt, KSP, Navigation, Serialization dependencies to `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.2.0-alpha04"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
hilt = "2.56.2"
ksp = "2.2.10-1.0.31"
navigationCompose = "2.9.0"
kotlinxSerialization = "1.8.1"
hiltNavigationCompose = "1.2.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Update root build.gradle.kts**

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Update gradle.properties**

Append to `gradle.properties`:

```properties
# Enable AndroidX
android.useAndroidX=true
# Hilt
android.enableBuildConfigAsBytecode=true
```

- [ ] **Step 4: Update settings.gradle.kts to include all modules**

This must happen before creating any modules so Gradle can recognize them.

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MusicFreeAndroid"

include(":app")
include(":core")
include(":data")
include(":player")
include(":plugin")
include(":feature:home")
include(":feature:player-ui")
include(":feature:search")
include(":feature:settings")
```

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts gradle.properties settings.gradle.kts
git commit -m "build: add Hilt, KSP, Navigation, Serialization to version catalog and register all modules"
```

---

## Task 2: Create :core Module

**Files:**
- Create: `core/build.gradle.kts`
- Create: `core/src/main/java/com/hank/musicfree/core/.gitkeep`

- [ ] **Step 1: Create core/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hank.musicfree.core"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p core/src/main/java/com/hank/musicfree/core/theme
mkdir -p core/src/main/java/com/hank/musicfree/core/navigation
mkdir -p core/src/main/java/com/hank/musicfree/core/ui
mkdir -p core/src/main/java/com/hank/musicfree/core/model
mkdir -p core/src/main/java/com/hank/musicfree/core/util
mkdir -p core/src/test/java/com/hank/musicfree/core
```

- [ ] **Step 3: Commit**

```bash
git add core/
git commit -m "build: create :core library module"
```

---

## Task 3: Core Theme System

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/theme/MusicFreeColors.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/theme/MusicFreeTheme.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/theme/Dimensions.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/theme/Rpx.kt`

Color values extracted from original: `../MusicFree/src/core/theme.ts`
Dimension values from: `../MusicFree/src/constants/uiConst.ts`
RPX formula from: `../MusicFree/src/utils/rpx.ts`

- [ ] **Step 1: Create MusicFreeColors.kt**

```kotlin
package com.hank.musicfree.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MusicFreeColors(
    val text: Color,
    val textSecondary: Color,
    val primary: Color,
    val background: Color,
    val pageBackground: Color,
    val shadow: Color,
    val appBar: Color,
    val appBarText: Color,
    val musicBar: Color,
    val musicBarText: Color,
    val divider: Color,
    val listActive: Color,
    val mask: Color,
    val backdrop: Color,
    val tabBar: Color,
    val placeholder: Color,
    val success: Color,
    val danger: Color,
    val info: Color,
    val card: Color,
    val notification: Color,
)

// Exact hex values from original MusicFree src/core/theme.ts
val LightMusicFreeColors = MusicFreeColors(
    text = Color(0xFF333333),
    textSecondary = Color(0xB3333333),       // #333333 alpha 0.7
    primary = Color(0xFFF17D34),
    background = Color.Transparent,
    pageBackground = Color(0xFFFAFAFA),
    shadow = Color(0xFF000000),
    appBar = Color(0xFFF17D34),
    appBarText = Color(0xFFFEFEFE),
    musicBar = Color(0xFFF2F2F2),
    musicBarText = Color(0xFF333333),
    divider = Color(0x1A000000),             // rgba(0,0,0,0.1)
    listActive = Color(0x1A000000),          // rgba(0,0,0,0.1)
    mask = Color(0x33333333),                // rgba(51,51,51,0.2)
    backdrop = Color(0xFFF0F0F0),
    tabBar = Color(0xFFF0F0F0),
    placeholder = Color(0xFFEAEAEA),
    success = Color(0xFF08A34C),
    danger = Color(0xFFFC5F5F),
    info = Color(0xFF0A95C8),
    card = Color(0x88E2E2E2),                // #e2e2e288
    notification = Color(0xFFF0F0F0),
)

val DarkMusicFreeColors = MusicFreeColors(
    text = Color(0xFFFCFCFC),
    textSecondary = Color(0xB3FCFCFC),       // #fcfcfc alpha 0.7
    primary = Color(0xFF3FA3B5),
    background = Color.Transparent,
    pageBackground = Color(0xFF202020),
    shadow = Color(0xFF999999),
    appBar = Color(0xFF262626),
    appBarText = Color(0xFFFCFCFC),
    musicBar = Color(0xFF262626),
    musicBarText = Color(0xFFFCFCFC),
    divider = Color(0x1AFFFFFF),             // rgba(255,255,255,0.1)
    listActive = Color(0x1AFFFFFF),          // rgba(255,255,255,0.1)
    mask = Color(0xCC212121),                // rgba(33,33,33,0.8)
    backdrop = Color(0xFF303030),
    tabBar = Color(0xFF303030),
    placeholder = Color(0xFF424242),
    success = Color(0xFF08A34C),
    danger = Color(0xFFFC5F5F),
    info = Color(0xFF0A95C8),
    card = Color(0x88333333),                // #33333388
    notification = Color(0xFF303030),
)

val LocalMusicFreeColors = staticCompositionLocalOf { LightMusicFreeColors }
```

- [ ] **Step 2: Create Rpx.kt**

```kotlin
package com.hank.musicfree.core.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Responsive pixel unit matching original MusicFree's rpx system.
 * Formula: rpx(value) = (value / 750) * min(screenWidth, screenHeight)
 * Based on 750px design width.
 */
@Composable
@ReadOnlyComposable
fun rpx(value: Int): Dp {
    val config = LocalConfiguration.current
    val minEdge = min(config.screenWidthDp, config.screenHeightDp)
    return ((value.toFloat() / 750f) * minEdge).dp
}

@Composable
@ReadOnlyComposable
fun rpxSp(value: Int): TextUnit {
    val config = LocalConfiguration.current
    val minEdge = min(config.screenWidthDp, config.screenHeightDp)
    return ((value.toFloat() / 750f) * minEdge).sp
}
```

- [ ] **Step 3: Create Dimensions.kt**

```kotlin
package com.hank.musicfree.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Design tokens from original MusicFree src/constants/uiConst.ts and src/constants/commonConst.ts.
 * All values use rpx() for responsive sizing.
 */
object FontSizes {
    val tag: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(20)
    val description: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(22)
    val subTitle: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(26)
    val content: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(28)
    val title: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(32)
    val appBar: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(36)
}

object IconSizes {
    val small: Dp @Composable @ReadOnlyComposable get() = rpx(30)
    val light: Dp @Composable @ReadOnlyComposable get() = rpx(36)
    val normal: Dp @Composable @ReadOnlyComposable get() = rpx(42)
    val big: Dp @Composable @ReadOnlyComposable get() = rpx(60)
    val large: Dp @Composable @ReadOnlyComposable get() = rpx(72)
}

object AnimationDurations {
    const val FAST = 150
    const val NORMAL = 250
    const val SLOW = 500
}
```

- [ ] **Step 4: Create MusicFreeTheme.kt**

```kotlin
package com.hank.musicfree.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = LightMusicFreeColors.primary,
    background = LightMusicFreeColors.pageBackground,
    surface = LightMusicFreeColors.pageBackground,
    onPrimary = LightMusicFreeColors.appBarText,
    onBackground = LightMusicFreeColors.text,
    onSurface = LightMusicFreeColors.text,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkMusicFreeColors.primary,
    background = DarkMusicFreeColors.pageBackground,
    surface = DarkMusicFreeColors.pageBackground,
    onPrimary = DarkMusicFreeColors.appBarText,
    onBackground = DarkMusicFreeColors.text,
    onSurface = DarkMusicFreeColors.text,
)

@Composable
fun MusicFreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val musicFreeColors = if (darkTheme) DarkMusicFreeColors else LightMusicFreeColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalMusicFreeColors provides musicFreeColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/**
 * Access custom MusicFree colors from any composable.
 * Usage: MusicFreeTheme.colors.primary
 */
object MusicFreeTheme {
    val colors: MusicFreeColors
        @Composable
        get() = LocalMusicFreeColors.current
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/theme/
git commit -m "feat(core): add MusicFree theme system with colors, rpx, and dimensions"
```

---

## Task 4: Core Navigation Routes

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`

- [ ] **Step 1: Create Routes.kt**

```kotlin
package com.hank.musicfree.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object PlayerRoute

@Serializable
data object SearchRoute

@Serializable
data object SettingsRoute
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/navigation/
git commit -m "feat(core): define navigation routes"
```

---

## Task 5: Create Library Modules (:data, :player, :plugin)

**Files:**
- Create: `data/build.gradle.kts`
- Create: `player/build.gradle.kts`
- Create: `plugin/build.gradle.kts`
- Create: placeholder source files in each

These modules are empty scaffolds for M1. They will be populated in later milestones.

- [ ] **Step 1: Create data/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hank.musicfree.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
```

- [ ] **Step 2: Create data source directory**

```bash
mkdir -p data/src/main/java/com/hank/musicfree/data
mkdir -p data/src/test/java/com/hank/musicfree/data
```

- [ ] **Step 3: Create player/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hank.musicfree.player"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
```

- [ ] **Step 4: Create player source directory**

```bash
mkdir -p player/src/main/java/com/hank/musicfree/player
mkdir -p player/src/test/java/com/hank/musicfree/player
```

- [ ] **Step 5: Create plugin/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hank.musicfree.plugin"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
```

- [ ] **Step 6: Create plugin source directory**

```bash
mkdir -p plugin/src/main/java/com/hank/musicfree/plugin
mkdir -p plugin/src/test/java/com/hank/musicfree/plugin
```

- [ ] **Step 7: Commit**

```bash
git add data/ player/ plugin/
git commit -m "build: create :data, :player, :plugin library modules"
```

---

## Task 6: Create Feature Modules with Placeholder Screens

**Files:**
- Create: `feature/home/build.gradle.kts`
- Create: `feature/home/src/main/java/.../home/HomeScreen.kt`
- Create: `feature/home/src/main/java/.../home/navigation/HomeNavigation.kt`
- Create: `feature/player-ui/build.gradle.kts`
- Create: `feature/player-ui/src/main/java/.../playerui/PlayerScreen.kt`
- Create: `feature/player-ui/src/main/java/.../playerui/navigation/PlayerNavigation.kt`
- Create: `feature/search/build.gradle.kts`
- Create: `feature/search/src/main/java/.../search/SearchScreen.kt`
- Create: `feature/search/src/main/java/.../search/navigation/SearchNavigation.kt`
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/settings/src/main/java/.../settings/SettingsScreen.kt`
- Create: `feature/settings/src/main/java/.../settings/navigation/SettingsNavigation.kt`

All feature modules share the same build.gradle.kts pattern. Each has a placeholder screen and navigation extension.

> **Note:** Feature modules only depend on `:core` in M1. Dependencies on `:data`, `:player`, `:plugin` will be added in later milestones as functionality is implemented.

- [ ] **Step 1: Create feature module build.gradle.kts template**

Each feature module uses this pattern (shown for `:feature:home`, adjust `namespace` for others):

`feature/home/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hank.musicfree.feature.home"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
```

Repeat for each feature module with appropriate namespace:
- `feature/player-ui/build.gradle.kts` → namespace `com.hank.musicfree.feature.playerui`
- `feature/search/build.gradle.kts` → namespace `com.hank.musicfree.feature.search`
- `feature/settings/build.gradle.kts` → namespace `com.hank.musicfree.feature.settings`

- [ ] **Step 2: Create source directories for all feature modules**

```bash
# home
mkdir -p feature/home/src/main/java/com/hank/musicfree/feature/home/navigation
mkdir -p feature/home/src/test/java/com/hank/musicfree/feature/home

# player-ui
mkdir -p feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/navigation
mkdir -p feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui

# search
mkdir -p feature/search/src/main/java/com/hank/musicfree/feature/search/navigation
mkdir -p feature/search/src/test/java/com/hank/musicfree/feature/search

# settings
mkdir -p feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation
mkdir -p feature/settings/src/test/java/com/hank/musicfree/feature/settings
```

- [ ] **Step 3: Create HomeScreen.kt**

```kotlin
package com.hank.musicfree.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hank.musicfree.core.theme.MusicFreeTheme

@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Home",
            color = MusicFreeTheme.colors.text,
        )
    }
}
```

- [ ] **Step 4: Create HomeNavigation.kt**

```kotlin
package com.hank.musicfree.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.HomeRoute
import com.hank.musicfree.feature.home.HomeScreen

fun NavGraphBuilder.homeScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateToPlayer = onNavigateToPlayer,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}
```

- [ ] **Step 5: Create PlayerScreen.kt and PlayerNavigation.kt**

`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`:
```kotlin
package com.hank.musicfree.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hank.musicfree.core.theme.MusicFreeTheme

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Player",
            color = MusicFreeTheme.colors.text,
        )
    }
}
```

`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/navigation/PlayerNavigation.kt`:
```kotlin
package com.hank.musicfree.feature.playerui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PlayerRoute
import com.hank.musicfree.feature.playerui.PlayerScreen

fun NavGraphBuilder.playerScreen(
    onBack: () -> Unit,
) {
    composable<PlayerRoute> {
        PlayerScreen(onBack = onBack)
    }
}
```

- [ ] **Step 6: Create SearchScreen.kt and SearchNavigation.kt**

`feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`:
```kotlin
package com.hank.musicfree.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hank.musicfree.core.theme.MusicFreeTheme

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Search",
            color = MusicFreeTheme.colors.text,
        )
    }
}
```

`feature/search/src/main/java/com/hank/musicfree/feature/search/navigation/SearchNavigation.kt`:
```kotlin
package com.hank.musicfree.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.SearchRoute
import com.hank.musicfree.feature.search.SearchScreen

fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(onBack = onBack)
    }
}
```

- [ ] **Step 7: Create SettingsScreen.kt and SettingsNavigation.kt**

`feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`:
```kotlin
package com.hank.musicfree.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hank.musicfree.core.theme.MusicFreeTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Settings",
            color = MusicFreeTheme.colors.text,
        )
    }
}
```

`feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation/SettingsNavigation.kt`:
```kotlin
package com.hank.musicfree.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.feature.settings.SettingsScreen

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(onBack = onBack)
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add feature/
git commit -m "feat: create feature modules with placeholder screens and navigation"
```

---

## Task 7: Update :app Module

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/.../MainActivity.kt`
- Create: `app/src/main/java/.../MusicFreeApplication.kt`
- Create: `app/src/main/java/.../navigation/AppNavHost.kt`
- Delete: `app/src/main/java/.../ui/theme/` (replaced by :core theme)

- [ ] **Step 1: Update app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hank.musicfree"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hank.musicfree"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.hank.musicfree.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Modules
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":player"))
    implementation(project(":plugin"))
    implementation(project(":feature:home"))
    implementation(project(":feature:player-ui"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Create MusicFreeApplication.kt**

```kotlin
package com.hank.musicfree

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MusicFreeApplication : Application()
```

- [ ] **Step 3: Create navigation/AppNavHost.kt**

```kotlin
package com.hank.musicfree.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.hank.musicfree.core.navigation.HomeRoute
import com.hank.musicfree.core.navigation.PlayerRoute
import com.hank.musicfree.core.navigation.SearchRoute
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.feature.home.navigation.homeScreen
import com.hank.musicfree.feature.playerui.navigation.playerScreen
import com.hank.musicfree.feature.search.navigation.searchScreen
import com.hank.musicfree.feature.settings.navigation.settingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        homeScreen(
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onNavigateToSearch = { navController.navigate(SearchRoute) },
            onNavigateToSettings = { navController.navigate(SettingsRoute) },
        )
        playerScreen(
            onBack = { navController.popBackStack() },
        )
        searchScreen(
            onBack = { navController.popBackStack() },
        )
        settingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
```

- [ ] **Step 4: Update MainActivity.kt**

```kotlin
package com.hank.musicfree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicFreeTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Update AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:name=".MusicFreeApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MusicFreeAndroid">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MusicFreeAndroid">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 6: Delete old theme files (replaced by :core theme)**

```bash
rm app/src/main/java/com/hank/musicfree/ui/theme/Color.kt
rm app/src/main/java/com/hank/musicfree/ui/theme/Theme.kt
rm app/src/main/java/com/hank/musicfree/ui/theme/Type.kt
rmdir app/src/main/java/com/hank/musicfree/ui/theme
rmdir app/src/main/java/com/hank/musicfree/ui
```

- [ ] **Step 7: Create navigation directory**

```bash
mkdir -p app/src/main/java/com/hank/musicfree/navigation
mkdir -p app/src/androidTest/java/com/hank/musicfree
mkdir -p app/src/test/java/com/hank/musicfree
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(app): integrate Hilt, NavHost, Scaffold with MusicFree theme"
```

---

## Task 8: Build Verification

- [ ] **Step 1: Run Gradle sync and build**

```bash
cd .
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: If build fails, fix issues**

Common issues to check:
- Version mismatches in `libs.versions.toml` (KSP version must match Kotlin version exactly: `{kotlin}-{ksp}`)
- Missing `kotlin.android` plugin (if AGP 9.x doesn't handle Kotlin automatically for KSP, add `id("org.jetbrains.kotlin.android") version "{kotlin}" apply false` to root and apply in library modules)
- Namespace conflicts between modules
- Missing transitive dependencies

- [ ] **Step 3: Install and launch on emulator/device**

```bash
./gradlew installDebug
adb shell am start -n com.hank.musicfree/.MainActivity
```

Expected: App launches showing "Home" text centered on screen.

- [ ] **Step 4: Commit any build fixes**

```bash
git add -A
git commit -m "fix: resolve build issues for multi-module setup"
```

---

## Task 9: Write Tests

**Files:**
- Create: `app/src/test/java/com/hank/musicfree/RoutesTest.kt`
- Create: `app/src/androidTest/java/com/hank/musicfree/HiltTestRunner.kt`
- Create: `app/src/androidTest/java/com/hank/musicfree/HiltDiTest.kt`

- [ ] **Step 1: Write route serialization test**

`app/src/test/java/com/hank/musicfree/RoutesTest.kt`:
```kotlin
package com.hank.musicfree

import com.hank.musicfree.core.navigation.HomeRoute
import com.hank.musicfree.core.navigation.PlayerRoute
import com.hank.musicfree.core.navigation.SearchRoute
import com.hank.musicfree.core.navigation.SettingsRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoutesTest {

    @Test
    fun `HomeRoute is serializable`() {
        val json = Json.encodeToString(serializer(), HomeRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<HomeRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `PlayerRoute is serializable`() {
        val json = Json.encodeToString(serializer(), PlayerRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<PlayerRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `SearchRoute is serializable`() {
        val json = Json.encodeToString(serializer(), SearchRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SearchRoute>(json)
        assertNotNull(decoded)
    }

    @Test
    fun `SettingsRoute is serializable`() {
        val json = Json.encodeToString(serializer(), SettingsRoute)
        assertNotNull(json)
        val decoded = Json.decodeFromString<SettingsRoute>(json)
        assertNotNull(decoded)
    }
}
```

- [ ] **Step 2: Run unit tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.hank.musicfree.RoutesTest"
```

Expected: 4 tests PASSED

- [ ] **Step 3: Create HiltTestRunner.kt**

`app/src/androidTest/java/com/hank/musicfree/HiltTestRunner.kt`:
```kotlin
package com.hank.musicfree

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

- [ ] **Step 4: Write Hilt DI graph test**

`app/src/androidTest/java/com/hank/musicfree/HiltDiTest.kt`:
```kotlin
package com.hank.musicfree

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltDiTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Test
    fun hiltDependencyGraphIsComplete() {
        // If this test runs without crashing, the DI graph is valid.
        // Hilt validates all bindings at compile time via KSP,
        // and this test verifies runtime initialization works.
        hiltRule.inject()
    }
}
```

- [ ] **Step 5: Run instrumented tests**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.hank.musicfree.HiltDiTest"
```

Expected: 1 test PASSED (requires connected device/emulator)

- [ ] **Step 6: Commit tests**

```bash
git add app/src/test/ app/src/androidTest/
git commit -m "test: add route serialization and Hilt DI graph tests"
```

---

## Verification Checklist (Milestone 1 Acceptance Criteria)

- [ ] `./gradlew assembleDebug` — all modules compile successfully
- [ ] App installs and launches, showing "Home" placeholder
- [ ] Navigation works between placeholder screens (will be testable when buttons are added; NavHost is wired)
- [ ] `RoutesTest` — 4 route serialization tests pass
- [ ] `HiltDiTest` — DI graph completeness test passes
