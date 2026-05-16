# Set Custom Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 RN 原版 `setCustomTheme` 页面 + `themeSetting` 选项；让 Android `MusicFreeTheme` 从静态切换到 DataStore 驱动的运行时主题（亮 / 暗 / 自定义三档），并支持自定义背景图 + 13 个可配色 + ColorPicker。

**Architecture:** 在 `:core/theme/runtime/` 引入 `ThemeUiState` / `SelectedTheme` / `ThemeRepository` 接口与纯函数 ColorMath/applyOverrides；在 `:data/repository/theme/` 实现 `DefaultThemeRepository`，桥接 `AppPreferences`；`MusicFreeTheme(themeState)` 由 `MainActivity` 收集 Flow 后传入；新增 `:feature:settings/themesetting/` 与 `setcustomtheme/` 子包；新增 `SetCustomThemeRoute` 挂到 `AppNavHost`。

**Tech Stack:** Kotlin + Compose Material3、androidx.palette、Coil（既有）、DataStore Preferences、kotlinx-serialization-json、Hilt、JUnit4 + Robolectric。

**Spec reference:** `docs/superpowers/specs/2026-05-16-set-custom-theme-design.md`（§N 引用都指向该文件）。

**Pre-execution worktree setup**：

```bash
git fetch origin && git checkout main && git pull --ff-only
git worktree add .worktrees/set-custom-theme -b feat/set-custom-theme
cd .worktrees/set-custom-theme
```

后续所有 task 都在 `.worktrees/set-custom-theme` 下执行。最终合并：`git merge --squash` + conventional commit + 中文。

---

## File Structure

### 新建文件

| 路径 | 责任 |
|---|---|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/SelectedTheme.kt` | enum + storageKey 映射 |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ThemeUiState.kt` | UI state + BackgroundInfo |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ColorMath.kt` | grayRate/darken/saturate/parseHex/toHexString 纯函数 |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ApplyOverrides.kt` | `applyOverrides(base, map) -> MusicFreeColors` |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ThemeRepository.kt` | 接口 |
| `core/src/test/java/com/zili/android/musicfreeandroid/core/theme/runtime/ColorMathTest.kt` | |
| `core/src/test/java/com/zili/android/musicfreeandroid/core/theme/runtime/ApplyOverridesTest.kt` | |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/theme/DefaultThemeRepository.kt` | 实现，桥接 AppPreferences + ColorMath + isSystemInDark |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/theme/ThemeColorsJson.kt` | Map<String,String> ↔ JSON helper |
| `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/theme/DefaultThemeRepositoryTest.kt` | runTest + Turbine |
| `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/theme/ThemeColorsJsonTest.kt` | |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/themesetting/ThemeSettingsContent.kt` | 顶层 Composable，替换原 SettingsScreen.kt Theme 分支 |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/themesetting/ThemeSettingsViewModel.kt` | followSystem/selected/ system dark 注入 |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/themesetting/ThemeCard.kt` | 单张主题卡 |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/SetCustomThemeScreen.kt` | 全屏页 |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/SetCustomThemeViewModel.kt` | sliders/colors/pickImage |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/BackgroundPickerSection.kt` | 顶部 460×690 rpx 图片选择区 |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/BlurOpacitySliders.kt` | 两条 slider |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/ConfigurableColorGrid.kt` | 13 个 ColorRow |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/ColorPickerBottomSheet.kt` | HSV + alpha + 文本 + 预设 |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/PaletteExtractor.kt` | 图片 → bitmap → Palette → PaletteColors（含 inSampleSize 缩放） |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/DerivedThemeColors.kt` | RN body.tsx:65-108 抽色推导 |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/navigation/SetCustomThemeNavigation.kt` | NavGraphBuilder ext |
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/themesetting/ThemeSettingsViewModelTest.kt` | |
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/themesetting/ThemeSettingsContentTest.kt` (Robolectric) | |
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/SetCustomThemeViewModelTest.kt` | |
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/DerivedThemeColorsTest.kt` | |
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/setcustomtheme/SetCustomThemeContentTest.kt` (Robolectric) | |
| `app/src/main/java/com/zili/android/musicfreeandroid/ThemeBackgroundLayer.kt` | 仅在自定义主题且 url 存在时绘制 AsyncImage + blur + alpha |

### 改动文件

| 路径 | 改动 |
|---|---|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/MusicFreeTheme.kt` | 增加 `MusicFreeTheme(themeState, content)` 签名；保留无参旧签名用于 @Preview |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt` | 加 `SetCustomThemeRoute` |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt` | `Screen.SetCustomThemeRoot` + `Settings.ThemeSection*` / `ThemeCard*` / `ThemeFollowSystemSwitch` + `object SetCustomTheme` |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt` | 6 个新 Flow + 6 个 setter |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt` | `@Provides ThemeRepository` 绑定 DefaultThemeRepository |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt` | 增加 `onNavigateToSetCustomTheme` 参数；`SettingsType.Theme` 分支改成 `ThemeSettingsContent(...)` |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/navigation/SettingsNavigation.kt` | 增加 `onNavigateToSetCustomTheme` 透传 |
| `feature/settings/build.gradle.kts` | 加 `implementation(libs.androidx.palette)`（如未存在）+ coil-compose 已有，确认 |
| `app/build.gradle.kts` | 无新模块依赖；如果 palette 需要在 :app 暴露则补 |
| `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt` | 注入 `ThemeRepository`；收集 state；`MusicFreeTheme(themeState)`；followSystem 联动 |
| `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt` | `setCustomThemeScreen(...)` + 在 `settingsScreen(...)` 调用里传 `onNavigateToSetCustomTheme` |

每个 task 自含；step 之间允许并行（同一 task 内串行）。

---

## Task 1: 新增运行时主题原语 + ColorMath

**Files (create):**
- `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/SelectedTheme.kt`
- `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ThemeUiState.kt`
- `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ColorMath.kt`
- `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ApplyOverrides.kt`
- `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/runtime/ThemeRepository.kt`
- `core/src/test/java/com/zili/android/musicfreeandroid/core/theme/runtime/ColorMathTest.kt`
- `core/src/test/java/com/zili/android/musicfreeandroid/core/theme/runtime/ApplyOverridesTest.kt`

- [ ] **Step 1: SelectedTheme + ThemeUiState + ThemeRepository 接口**

```kotlin
// SelectedTheme.kt
package com.zili.android.musicfreeandroid.core.theme.runtime
enum class SelectedTheme(val storageKey: String) {
    P_LIGHT("p-light"), P_DARK("p-dark"), CUSTOM("custom");
    companion object {
        fun fromStorageKey(key: String?): SelectedTheme =
            entries.firstOrNull { it.storageKey == key } ?: P_DARK
    }
}

// ThemeUiState.kt
data class ThemeUiState(
    val selected: SelectedTheme,
    val effectiveColors: MusicFreeColors,
    val background: BackgroundInfo?,
    val followSystem: Boolean,
    val isLoading: Boolean,
)
data class BackgroundInfo(val url: String?, val blur: Float, val opacity: Float)

// ThemeRepository.kt
interface ThemeRepository {
    val state: kotlinx.coroutines.flow.Flow<ThemeUiState>
    suspend fun selectTheme(theme: SelectedTheme)
    suspend fun setFollowSystem(enabled: Boolean, currentSystemDark: Boolean)
    suspend fun setBackground(url: String?, blur: Float?, opacity: Float?)
    suspend fun patchCustomColors(patch: Map<String, String>)
    suspend fun replaceCustomColors(colors: Map<String, String>)
}
```

- [ ] **Step 2: ColorMath 纯函数**

```kotlin
// ColorMath.kt
fun grayRate(color: Color): Float {
    // 复现 RN colorUtil.grayRate
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return (r - g) / 255f + (g - b) / 255f
}
fun Color.darken(amount: Float): Color { /* HSL.lightness -= amount.coerceIn(-1f,1f) */ }
fun Color.saturate(amount: Float): Color { /* HSL.saturation += amount.coerceIn(0f, big), 不超 1 */ }
fun Color.toHexString(): String  // #AARRGGBB 大写
fun parseHexColor(hex: String): Color?  // 容错；接受 #RGB #RRGGBB #AARRGGBB
```

实现细节：转 HSL 用 `androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb`；HSL 公式自写约 30 行；不引第三方。`grayRate` RN 源码：`(r - g) / 255 + (g - b) / 255`（RN colorUtil.ts；本仓库无此文件，需对照 RN repo 验证）。

- [ ] **Step 3: applyOverrides**

```kotlin
fun applyOverrides(base: MusicFreeColors, overrides: Map<String, String>): MusicFreeColors {
    if (overrides.isEmpty()) return base
    return base.copy(
        primary = overrides["primary"]?.let(::parseHexColor) ?: base.primary,
        text = overrides["text"]?.let(::parseHexColor) ?: base.text,
        appBar = overrides["appBar"]?.let(::parseHexColor) ?: base.appBar,
        appBarText = overrides["appBarText"]?.let(::parseHexColor) ?: base.appBarText,
        musicBar = overrides["musicBar"]?.let(::parseHexColor) ?: base.musicBar,
        musicBarText = overrides["musicBarText"]?.let(::parseHexColor) ?: base.musicBarText,
        pageBackground = overrides["pageBackground"]?.let(::parseHexColor) ?: base.pageBackground,
        backdrop = overrides["backdrop"]?.let(::parseHexColor) ?: base.backdrop,
        card = overrides["card"]?.let(::parseHexColor) ?: base.card,
        placeholder = overrides["placeholder"]?.let(::parseHexColor) ?: base.placeholder,
        tabBar = overrides["tabBar"]?.let(::parseHexColor) ?: base.tabBar,
        notification = overrides["notification"]?.let(::parseHexColor) ?: base.notification,
    )
}
const val CONFIGURABLE_COLOR_KEYS = listOf(
    "primary","text","appBar","appBarText","musicBar","musicBarText",
    "pageBackground","backdrop","card","placeholder","tabBar","notification",
)
```

- [ ] **Step 4: ColorMath 测试**

- `grayRate(#FFFFFF)` → ~0
- `grayRate(#000000)` → 0
- `grayRate(#FF0000)` → +1（r 大，g/b 小）
- `Color(0xFF888888).darken(0.5f).red < 0.5f`
- `Color.toHexString()` round-trip with `parseHexColor`

- [ ] **Step 5: applyOverrides 测试**

- 空 map → 返回 base 不变
- 13 个 key 全 patch → 13 个字段都被覆盖
- 未知 key → 忽略
- 无效 hex → 该字段保留 base 值

**Verification:**
```bash
./gradlew :core:testDebugUnitTest
```

---

## Task 2: AppPreferences 扩展 + DefaultThemeRepository

**Files (create / modify):**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/theme/ThemeColorsJson.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/theme/DefaultThemeRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/theme/DefaultThemeRepositoryTest.kt`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/theme/ThemeColorsJsonTest.kt`

- [ ] **Step 1: AppPreferences 新增字段**

照 `desktopLyric*` 模式，加 6 对：

```kotlin
val selectedTheme: Flow<SelectedTheme> = dataStore.data.map { prefs ->
    SelectedTheme.fromStorageKey(prefs[KEY_SELECTED_THEME])
}
suspend fun setSelectedTheme(value: SelectedTheme) {
    writeRuntimeSetting(KEY_SELECTED_THEME, value.storageKey) {
        it[KEY_SELECTED_THEME] = value.storageKey
    }
}
val customColorsJson: Flow<String?> = dataStore.data.map { it[KEY_CUSTOM_COLORS_JSON] }
suspend fun setCustomColorsJson(value: String?) { /* remove or set */ }
val themeBackgroundUrl: Flow<String?> = ...
val themeBackgroundBlur: Flow<Float> = dataStore.data.map { (it[KEY_BG_BLUR] ?: 20f).coerceIn(0f,30f) }
val themeBackgroundOpacity: Flow<Float> = dataStore.data.map { (it[KEY_BG_OPACITY] ?: 0.7f).coerceIn(0.3f, 1f) }
val themeFollowSystem: Flow<Boolean> = dataStore.data.map { it[KEY_THEME_FOLLOW_SYSTEM] ?: false }
```

Companion 加 6 个 key。

- [ ] **Step 2: ThemeColorsJson**

```kotlin
internal object ThemeColorsJson {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(map: Map<String, String>): String =
        json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map)
    fun decode(raw: String?): Map<String, String> = runCatching {
        if (raw.isNullOrBlank()) emptyMap() else
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw)
    }.getOrElse { emptyMap() }
}
```

- [ ] **Step 3: DefaultThemeRepository**

```kotlin
@Singleton
class DefaultThemeRepository @Inject constructor(
    private val prefs: AppPreferences,
) : ThemeRepository {
    override val state: Flow<ThemeUiState> = combine(
        prefs.selectedTheme,
        prefs.customColorsJson.map { ThemeColorsJson.decode(it) },
        prefs.themeBackgroundUrl,
        prefs.themeBackgroundBlur,
        prefs.themeBackgroundOpacity,
        prefs.themeFollowSystem,
    ) { selected, customColors, bgUrl, bgBlur, bgOpacity, followSystem ->
        val base = when (selected) {
            SelectedTheme.P_LIGHT -> LightMusicFreeColors
            SelectedTheme.P_DARK -> DarkMusicFreeColors
            SelectedTheme.CUSTOM -> DarkMusicFreeColors
        }
        val effective = if (selected == SelectedTheme.CUSTOM)
            applyOverrides(base, customColors) else base
        val bg = if (selected == SelectedTheme.CUSTOM)
            BackgroundInfo(url = bgUrl?.takeIf { File(URI.create(it).path ?: it).exists() }, blur = bgBlur, opacity = bgOpacity)
            else null
        ThemeUiState(selected, effective, bg, followSystem, isLoading = false)
    }

    override suspend fun selectTheme(theme: SelectedTheme) { prefs.setSelectedTheme(theme) }
    override suspend fun setFollowSystem(enabled: Boolean, currentSystemDark: Boolean) {
        prefs.setThemeFollowSystem(enabled)
        if (enabled) prefs.setSelectedTheme(if (currentSystemDark) SelectedTheme.P_DARK else SelectedTheme.P_LIGHT)
    }
    override suspend fun setBackground(url: String?, blur: Float?, opacity: Float?) {
        if (url != null) prefs.setThemeBackgroundUrl(url)
        blur?.let { prefs.setThemeBackgroundBlur(it.coerceIn(0f, 30f)) }
        opacity?.let { prefs.setThemeBackgroundOpacity(it.coerceIn(0.3f, 1f)) }
    }
    override suspend fun patchCustomColors(patch: Map<String, String>) {
        val current = ThemeColorsJson.decode(prefs.customColorsJson.first()).toMutableMap()
        current.putAll(patch)
        prefs.setCustomColorsJson(ThemeColorsJson.encode(current))
    }
    override suspend fun replaceCustomColors(colors: Map<String, String>) {
        prefs.setCustomColorsJson(ThemeColorsJson.encode(colors))
    }
}
```

注意 file-exists 校验放在 collector 端会拖慢 Flow；改为在 BackgroundInfo 透传后由 UI 层用 `rememberAsyncImagePainter` 自然 fallback。所以 Repository 不查文件，直接透 url。

- [ ] **Step 4: Hilt 绑定**

DataModule 加：

```kotlin
@Binds @Singleton
abstract fun bindThemeRepository(impl: DefaultThemeRepository): ThemeRepository
```

注意如果 DataModule 是 `@Module @InstallIn(SingletonComponent::class) object DataModule`，需要改为 `@Module @InstallIn(SingletonComponent::class) abstract class DataModule`，或新建独立 `ThemeRepositoryModule.kt`。优先后者，避免动既有 object 模块。

- [ ] **Step 5: 测试 ThemeColorsJsonTest**

- encode 空 map → `"{}"`
- decode `null` / `""` / 非法 JSON → emptyMap
- encode/decode round-trip

- [ ] **Step 6: 测试 DefaultThemeRepositoryTest**

用 `runTest(mainDispatcherRule.dispatcher)`，用 `androidx.datastore.preferences.core.PreferenceDataStoreFactory` + `File(tempFolder.newFile().absolutePath.removeSuffix(".tmp"))`（or in-memory fake）构造 `AppPreferences`。Turbine 验证：

- 默认 state：P_DARK / 默认 colors / background=null / followSystem=false
- selectTheme(P_LIGHT) → state.selected=P_LIGHT，effectiveColors=Light
- selectTheme(CUSTOM) + patchCustomColors(primary=#FFFF0000) → effectiveColors.primary=Red
- setBackground url/blur/opacity → CUSTOM 下生效；P_DARK 下 background=null
- setFollowSystem(true, currentSystemDark=true) → followSystem=true, selected=P_DARK

**Verification:**
```bash
./gradlew :data:testDebugUnitTest
```

---

## Task 3: MusicFreeTheme 状态化 + ThemeBackgroundLayer

**Files (create / modify):**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/theme/MusicFreeTheme.kt`
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/ThemeBackgroundLayer.kt`

- [ ] **Step 1: 增加状态化签名**

```kotlin
@Composable
fun MusicFreeTheme(themeState: ThemeUiState, content: @Composable () -> Unit) {
    val isDark = themeState.selected != SelectedTheme.P_LIGHT
    val colorScheme = if (isDark) darkColorScheme(
        primary = themeState.effectiveColors.primary,
        background = themeState.effectiveColors.pageBackground,
        surface = themeState.effectiveColors.pageBackground,
        onPrimary = themeState.effectiveColors.appBarText,
        onBackground = themeState.effectiveColors.text,
        onSurface = themeState.effectiveColors.text,
    ) else lightColorScheme(...)
    CompositionLocalProvider(LocalMusicFreeColors provides themeState.effectiveColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
```

保留旧无参签名转调新签名（已在 spec §3 示例）。

- [ ] **Step 2: ThemeBackgroundLayer**

```kotlin
@Composable
fun ThemeBackgroundLayer(info: BackgroundInfo?, modifier: Modifier = Modifier) {
    val url = info?.url ?: return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .blur(info.blur.dp)
            .graphicsLayer { alpha = info.opacity },
    )
}
```

**Verification:**
```bash
./gradlew :core:compileDebugUnitTestKotlin :app:compileDebugKotlin
```

---

## Task 4: MainActivity 集成 + 路由

**Files (modify):**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`

- [ ] **Step 1: Routes 加 SetCustomThemeRoute**

```kotlin
@Serializable
data object SetCustomThemeRoute
```

- [ ] **Step 2: FidelityAnchors 加 anchor**

`Screen.SetCustomThemeRoot = "screen.setCustomTheme.root"`；`Settings.ThemeSectionMode / ThemeSectionTheme / ThemeFollowSystemSwitch / ThemeCardLight / ThemeCardDark / ThemeCardCustom`；新 `object SetCustomTheme { Image / SliderBlur / SliderOpacity / ColorItemPrefix / ColorPickerSheet / ColorPickerConfirm }`。

- [ ] **Step 3: MainActivity 注入 + 收集**

```kotlin
@Inject lateinit var themeRepository: ThemeRepository

setContent {
    val systemDark = isSystemInDarkTheme()
    val themeState by themeRepository.state.collectAsStateWithLifecycle(
        initialValue = ThemeUiState(
            selected = SelectedTheme.P_DARK,
            effectiveColors = DarkMusicFreeColors,
            background = null,
            followSystem = false,
            isLoading = true,
        )
    )
    LaunchedEffect(systemDark, themeState.followSystem) {
        if (themeState.followSystem) {
            themeRepository.selectTheme(
                if (systemDark) SelectedTheme.P_DARK else SelectedTheme.P_LIGHT
            )
        }
    }
    MusicFreeTheme(themeState = themeState) {
        Box(Modifier.fillMaxSize()) {
            ThemeBackgroundLayer(themeState.background)
            // ... 既有 UpdateDialogHost + Scaffold 树（保持原样）
        }
    }
}
```

注意：既有 `setContent { MusicFreeTheme { ... } }` 无参形式被替换，确保 `enableEdgeToEdge()` 之后的所有内容都在新 `MusicFreeTheme(themeState)` 内。

**Verification:**
```bash
./gradlew :app:assembleDebug
```

---

## Task 5: ThemeSettings 子页（设置 → 主题设置）

**Files (create / modify):**
- Create: `feature/settings/src/main/java/.../themesetting/ThemeSettingsViewModel.kt`
- Create: `feature/settings/src/main/java/.../themesetting/ThemeSettingsContent.kt`
- Create: `feature/settings/src/main/java/.../themesetting/ThemeCard.kt`
- Modify: `feature/settings/src/main/java/.../SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/.../navigation/SettingsNavigation.kt`
- Modify: `app/src/main/java/.../navigation/AppNavHost.kt` (settingsScreen 入参)

- [ ] **Step 1: ThemeSettingsViewModel**

```kotlin
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {
    val state: StateFlow<ThemeUiState> = themeRepository.state.stateIn(viewModelScope, ...)

    fun onFollowSystemToggle(enabled: Boolean, systemDark: Boolean) {
        viewModelScope.launch { themeRepository.setFollowSystem(enabled, systemDark) }
    }
    fun onSelectTheme(theme: SelectedTheme) {
        viewModelScope.launch { themeRepository.selectTheme(theme) }
    }
}
```

- [ ] **Step 2: ThemeCard composable**

照 RN themeCard.tsx 像素：

```kotlin
@Composable
fun ThemeCard(
    selected: Boolean,
    title: String,
    previewColor: Color? = null,
    previewImageUrl: String? = null,
    testTag: String,
    onClick: () -> Unit,
) {
    Column {
        Box(Modifier.size(rpx(160)).clip(RoundedCornerShape(rpx(22)))
                .let { if (selected) it.border(2.dp, MusicFreeTheme.colors.primary, RoundedCornerShape(rpx(22))) else it }
                .clickable(onClick = onClick).testTag(testTag),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(rpx(136)).clip(RoundedCornerShape(rpx(12)))
                    .background(previewColor ?: Color.LightGray)) {
                previewImageUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        Text(title, fontSize = FontSizes.subTitle, color = if (selected) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.text,
            modifier = Modifier.width(rpx(160)), textAlign = TextAlign.Center)
    }
}
```

- [ ] **Step 3: ThemeSettingsContent**

```kotlin
@Composable
fun ThemeSettingsContent(
    state: ThemeUiState,
    onFollowSystemToggle: (Boolean) -> Unit,
    onSelectLight: () -> Unit,
    onSelectDark: () -> Unit,
    onSelectCustom: () -> Unit,
    onNavigateToSetCustomTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().testTag(FidelityAnchors.Settings.ThemeRoot).padding(horizontal = rpx(24)), ...) {
        item {
            SettingSectionCard("显示样式", testTag = FidelityAnchors.Settings.ThemeSectionMode) {
                SettingSwitchRow("跟随系统主题", checked = state.followSystem, enabled = true,
                    testTag = FidelityAnchors.Settings.ThemeFollowSystemSwitch,
                    onCheckedChange = onFollowSystemToggle)
            }
        }
        item {
            SettingSectionCard("主题", testTag = FidelityAnchors.Settings.ThemeSectionTheme) {
                Row(Modifier.padding(horizontal = rpx(24), vertical = rpx(16)),
                    horizontalArrangement = Arrangement.spacedBy(rpx(24))) {
                    ThemeCard(state.selected == SelectedTheme.P_LIGHT, "亮色", Color.White, null,
                        FidelityAnchors.Settings.ThemeCardLight, onSelectLight)
                    ThemeCard(state.selected == SelectedTheme.P_DARK, "暗色", Color(0xFF131313), null,
                        FidelityAnchors.Settings.ThemeCardDark, onSelectDark)
                    ThemeCard(state.selected == SelectedTheme.CUSTOM, "自定义",
                        null, state.background?.url,
                        FidelityAnchors.Settings.ThemeCardCustom) {
                        if (state.selected != SelectedTheme.CUSTOM) onSelectCustom()
                        onNavigateToSetCustomTheme()
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: SettingsScreen 接 onNavigateToSetCustomTheme**

```kotlin
@Composable
fun SettingsScreen(
    type: SettingsType,
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
    onNavigateToSetCustomTheme: () -> Unit,   // 新增
    ...
) {
    ...
    when (type) {
        ...
        SettingsType.Theme -> {
            val themeVm: ThemeSettingsViewModel = hiltViewModel()
            val themeState by themeVm.state.collectAsStateWithLifecycle()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            ThemeSettingsContent(
                state = themeState,
                onFollowSystemToggle = { themeVm.onFollowSystemToggle(it, systemDark) },
                onSelectLight = { themeVm.onSelectTheme(SelectedTheme.P_LIGHT) },
                onSelectDark = { themeVm.onSelectTheme(SelectedTheme.P_DARK) },
                onSelectCustom = { themeVm.onSelectTheme(SelectedTheme.CUSTOM) },
                onNavigateToSetCustomTheme = onNavigateToSetCustomTheme,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
```

- [ ] **Step 5: SettingsNavigation + AppNavHost 透传**

`settingsScreen(... onNavigateToSetCustomTheme: () -> Unit)`；AppNavHost 调用处加 `onNavigateToSetCustomTheme = { navController.navigate(SetCustomThemeRoute) }`。

**Verification:**
```bash
./gradlew :feature:settings:compileDebugKotlin :app:compileDebugKotlin
```

---

## Task 6: SetCustomThemeViewModel + 抽色推导

**Files (create):**
- `feature/settings/src/main/java/.../setcustomtheme/SetCustomThemeViewModel.kt`
- `feature/settings/src/main/java/.../setcustomtheme/DerivedThemeColors.kt`
- `feature/settings/src/main/java/.../setcustomtheme/PaletteExtractor.kt`
- `feature/settings/src/test/java/.../setcustomtheme/DerivedThemeColorsTest.kt`
- `feature/settings/src/test/java/.../setcustomtheme/SetCustomThemeViewModelTest.kt`
- Modify: `feature/settings/build.gradle.kts`（加 androidx.palette 依赖；如不在 libs.versions.toml 则同步加）

- [ ] **Step 1: gradle 加 palette**

确认 `gradle/libs.versions.toml` 是否已有 `androidx-palette = "androidx.palette:palette-ktx:1.0.0"`。如无：
- versions.toml 增加 `androidxPalette = "1.0.0"` 与 `androidx-palette-ktx = { group = "androidx.palette", name = "palette-ktx", version.ref = "androidxPalette" }`
- feature/settings/build.gradle.kts dependencies 中加 `implementation(libs.androidx.palette.ktx)`

- [ ] **Step 2: PaletteExtractor**

```kotlin
suspend fun extractPaletteColors(context: Context, uri: Uri): PaletteColors? = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap = decodeSampled(context, uri, maxDim = 1080)
        val palette = Palette.from(bitmap).generate()
        val fallback = 0xFF3FA3B5.toInt()
        PaletteColors(
            primary = Color(palette.getDominantColor(fallback)),
            average = Color(palette.getMutedColor(palette.getDominantColor(fallback))),
            vibrant = Color(palette.getVibrantColor(palette.getDominantColor(fallback))),
        )
    }.getOrNull()
}

private fun decodeSampled(context: Context, uri: Uri, maxDim: Int): Bitmap {
    // 用 BitmapFactory.Options.inJustDecodeBounds 计算 inSampleSize；缩到不超过 maxDim
}
```

`PaletteColors` data class 含 3 个 Color。

- [ ] **Step 3: DerivedThemeColors（RN body.tsx 抽色）**

```kotlin
fun deriveCustomColors(palette: PaletteColors): Map<String, String> {
    val primary = palette.primary
    val gray = grayRate(primary)
    val primaryHex = primary.toHexString()
    return when {
        gray < -0.4f -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.darken(gray * 5f).toHexString(),
            "musicBar" to primaryHex,
            "card" to "#33000000",
            "tabBar" to primary.copy(alpha = 0.2f).toHexString(),
        )
        gray > 0.4f -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.darken(gray * 5f).toHexString(),
            "musicBar" to primaryHex,
            "card" to "#33000000",
        )
        else -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.saturate(kotlin.math.abs(gray) * 2f + 2f).toHexString(),
            "musicBar" to primaryHex,
            "card" to "#33000000",
        )
    }
}
```

DerivedThemeColorsTest：3 个分支 sanity case + 已知 RN 输入对照（可用 RN 源里现实的常见值如 `#3FA3B5` 走 else 分支）。

- [ ] **Step 4: SetCustomThemeViewModel**

```kotlin
@HiltViewModel
class SetCustomThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    val state: StateFlow<ThemeUiState> = themeRepository.state.stateIn(viewModelScope, ...)

    private val _activeColorKey = MutableStateFlow<String?>(null)
    val activeColorKey: StateFlow<String?> = _activeColorKey.asStateFlow()

    fun openColorPicker(key: String) { _activeColorKey.value = key }
    fun dismissColorPicker() { _activeColorKey.value = null }

    fun onColorConfirmed(key: String, hex: String) {
        viewModelScope.launch {
            themeRepository.patchCustomColors(mapOf(key to hex))
            _activeColorKey.value = null
        }
    }

    fun onBlurChanged(blur: Float) {
        viewModelScope.launch { themeRepository.setBackground(url = null, blur = blur, opacity = null) }
    }
    fun onOpacityChanged(opacity: Float) {
        viewModelScope.launch { themeRepository.setBackground(url = null, blur = null, opacity = opacity) }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val copied = copyImageToInternal(context, uri) ?: return@launch
            val palette = extractPaletteColors(context, copied) ?: return@launch
            val derived = deriveCustomColors(palette)
            themeRepository.replaceCustomColors(derived)
            themeRepository.setBackground(url = copied.toString(), blur = null, opacity = null)
            themeRepository.selectTheme(SelectedTheme.CUSTOM)
        }
    }
}

private suspend fun copyImageToInternal(context: Context, uri: Uri): Uri? = withContext(Dispatchers.IO) {
    val ext = context.contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
    val dest = File(context.filesDir, "theme_background.$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
    }
    Uri.fromFile(dest)
}
```

- [ ] **Step 5: ViewModel 测试**

用 Robolectric / fake context 或 mock context；mock `extractPaletteColors`（提取为顶层 internal 函数以便测试覆盖）/ 或注入接口。

简化路径：把 `extractPaletteColors` 与 `copyImageToInternal` 抽到 `interface ImageAndPaletteLoader` 注入 VM，测试用 fake 实现。

测试断言：
- `openColorPicker("primary")` → activeColorKey=primary
- `onColorConfirmed("primary","#FFFF0000")` → repo.patchCustomColors(mapOf("primary" to "#FFFF0000")) 被调用、activeColorKey=null
- `onBlurChanged(5f)` → repo.setBackground(blur=5f) 被调用
- `onImagePicked(uri)` → fake loader 返回固定 palette → repo.replaceCustomColors / setBackground / selectTheme(CUSTOM) 顺序调用

**Verification:**
```bash
./gradlew :feature:settings:testDebugUnitTest
```

---

## Task 7: SetCustomThemeScreen + ColorPicker UI

**Files (create / modify):**
- `feature/settings/src/main/java/.../setcustomtheme/SetCustomThemeScreen.kt`
- `feature/settings/src/main/java/.../setcustomtheme/BackgroundPickerSection.kt`
- `feature/settings/src/main/java/.../setcustomtheme/BlurOpacitySliders.kt`
- `feature/settings/src/main/java/.../setcustomtheme/ConfigurableColorGrid.kt`
- `feature/settings/src/main/java/.../setcustomtheme/ColorPickerBottomSheet.kt`
- `feature/settings/src/main/java/.../setcustomtheme/navigation/SetCustomThemeNavigation.kt`
- `feature/settings/src/test/java/.../setcustomtheme/SetCustomThemeContentTest.kt`
- Modify: `app/src/main/java/.../navigation/AppNavHost.kt` 加 `setCustomThemeScreen(onBack = ...)`

- [ ] **Step 1: BackgroundPickerSection**

```kotlin
@Composable
fun BackgroundPickerSection(currentUrl: String?, onImagePicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onImagePicked)
    }
    Box(Modifier.padding(top = rpx(36)).size(width = rpx(460), height = rpx(690))
            .clip(RoundedCornerShape(rpx(12))).clickable {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.testTag(FidelityAnchors.SetCustomTheme.Image),
        contentAlignment = Alignment.Center) {
        if (currentUrl != null) {
            AsyncImage(model = currentUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Add, contentDescription = "选择背景图",
                tint = MusicFreeTheme.colors.textSecondary, modifier = Modifier.size(rpx(96)))
        }
    }
}
```

- [ ] **Step 2: BlurOpacitySliders**

两个 SliderRow，标签 + Slider；`onValueChangeFinished` 触发回调。Slider 颜色：`colors = SliderDefaults.colors(thumbColor=primary, activeTrackColor=primary, inactiveTrackColor=textSecondary)`。

- [ ] **Step 3: ConfigurableColorGrid**

LazyVerticalGrid（columns = Fixed(2)） 或 Row+Wrap；每个 cell：

```kotlin
@Composable
fun ColorItem(key: String, currentColor: Color, onClick: () -> Unit) {
    Column(Modifier.padding(bottom = rpx(36))) {
        Text(colorKeyToLabel(key), color = MusicFreeTheme.colors.text)
        Spacer(Modifier.height(rpx(18)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = rpx(76), height = rpx(50))
                    .border(1.dp, Color.LightGray).clickable(onClick = onClick)
                    .testTag(FidelityAnchors.SetCustomTheme.ColorItemPrefix + key)) {
                CheckerboardBackground(modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(currentColor))
            }
            Spacer(Modifier.width(rpx(8)))
            Text(currentColor.toHexString(), fontSize = FontSizes.subTitle,
                color = MusicFreeTheme.colors.text)
        }
    }
}

private fun colorKeyToLabel(key: String): String = when (key) {
    "primary" -> "主色调"
    "text" -> "文字"
    "appBar" -> "顶栏"
    "appBarText" -> "顶栏文字"
    "musicBar" -> "底栏"
    "musicBarText" -> "底栏文字"
    "pageBackground" -> "页面背景"
    "backdrop" -> "蒙层"
    "card" -> "卡片"
    "placeholder" -> "占位"
    "tabBar" -> "Tab 栏"
    "notification" -> "通知"
    else -> key
}
```

CheckerboardBackground：自绘 Canvas 重复 10×10 灰白方块。

- [ ] **Step 4: ColorPickerBottomSheet**

```kotlin
@Composable
fun ColorPickerBottomSheet(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (hex: String) -> Unit,
) {
    var hsv by remember { mutableStateOf(initialColor.toHsv()) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }
    var textValue by remember { mutableStateOf(initialColor.toHexString()) }

    ModalBottomSheet(onDismissRequest = onDismiss,
        modifier = Modifier.testTag(FidelityAnchors.SetCustomTheme.ColorPickerSheet)) {
        Column(Modifier.padding(rpx(24)).windowInsetsPadding(WindowInsets.statusBars)) {
            // 1. HSV 圆盘 - 自绘 Canvas，pointerInput 处理 H/S 选取
            HsvWheel(hsv, onHsvChange = { hsv = it })
            // 2. Value slider
            ValueSlider(hsv.value) { hsv = hsv.copy(value = it) }
            // 3. alpha slider
            Slider(value = alpha, onValueChange = { alpha = it })
            // 4. 预设
            Row { presetColors.forEach { ColorChip(it, onClick = { ... }) } }
            // 5. 文本输入
            OutlinedTextField(textValue, onValueChange = { textValue = it }, label = { Text("HEX") })
            // 6. 确认 / 取消
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(
                    onClick = {
                        val final = parseHexColor(textValue) ?: hsv.toColor().copy(alpha = alpha)
                        onConfirm(final.toHexString())
                    },
                    modifier = Modifier.testTag(FidelityAnchors.SetCustomTheme.ColorPickerConfirm)
                ) { Text("确认") }
            }
        }
    }
}
```

Hsv 圆盘可用简化版：径向 = saturation，角度 = hue；touch 取最近角度 + 半径百分比；不是完美 hue ring 也 ok（标记 TODO 但保证可用）。

简化路径：用 RGB Box 三个 slider（R/G/B/Alpha 4 个 slider + 预设 + 文本）。R/G/B slider 即可满足；spec 中"HSV 圆盘"作为可选增强，本期采纳 RGBA 滑条版本，标注"未来可换 HSV 圆盘"。

- [ ] **Step 5: SetCustomThemeScreen**

```kotlin
@Composable
fun SetCustomThemeScreen(
    onBack: () -> Unit,
    viewModel: SetCustomThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeKey by viewModel.activeColorKey.collectAsStateWithLifecycle()

    MusicFreeScreenScaffold(
        title = "自定义主题",
        onBack = onBack,
        actions = { TextButton(onBack) { Text("完成", color = MusicFreeTheme.colors.appBarText) } },
        modifier = Modifier.testTag(FidelityAnchors.Screen.SetCustomThemeRoot),
    ) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
            BackgroundPickerSection(state.background?.url, viewModel::onImagePicked)
            BlurOpacitySliders(state.background, viewModel::onBlurChanged, viewModel::onOpacityChanged)
            ConfigurableColorGrid(
                colors = state.effectiveColors,
                onColorClicked = viewModel::openColorPicker,
            )
        }
    }
    if (activeKey != null) {
        ColorPickerBottomSheet(
            initialColor = state.effectiveColors.byKey(activeKey!!),
            onDismiss = viewModel::dismissColorPicker,
            onConfirm = { hex -> viewModel.onColorConfirmed(activeKey!!, hex) },
        )
    }
}
```

`MusicFreeScreenScaffold` 已有 `actions` 槽位（如无则 fallback：右上加 IconButton(Text)），需检查 actual signature 决定是否手写 actions。如无 actions slot，退而求其次：标题区不放完成按钮，依赖系统返回。

- [ ] **Step 6: SetCustomThemeNavigation**

```kotlin
fun NavGraphBuilder.setCustomThemeScreen(onBack: () -> Unit) {
    composable<SetCustomThemeRoute> {
        SetCustomThemeScreen(onBack = onBack)
    }
}
```

- [ ] **Step 7: AppNavHost 挂载**

加 `setCustomThemeScreen(onBack = { navController.popBackStack() })`。

- [ ] **Step 8: SetCustomThemeContentTest（Robolectric）**

createComposeRule + 注入 fake state，断言：
- 13 个 ColorItem 渲染（13 个 testTag）
- blur / opacity slider 渲染
- 图片占位 / 已选图渲染
- 点击 color 触发 callback

**Verification:**
```bash
./gradlew :feature:settings:testDebugUnitTest :app:assembleDebug
```

---

## Task 8: ThemeSettings 单测 + Anchor 校验

**Files (create / modify):**
- Create: `feature/settings/src/test/java/.../themesetting/ThemeSettingsViewModelTest.kt`
- Create: `feature/settings/src/test/java/.../themesetting/ThemeSettingsContentTest.kt`
- Modify: `core/src/test/java/.../core/ui/PluginSearchPlayAnchorContractTest.kt`（如该测试枚举 anchor，需要补 6 个新 anchor）

- [ ] **Step 1: ThemeSettingsViewModelTest**

`runTest(mainDispatcherRule.dispatcher)` + fake `ThemeRepository`（用 in-memory MutableStateFlow + suspend fun 互改 state）。断言：
- onFollowSystemToggle(true, true) → repo.setFollowSystem(true, true) 被调用
- onSelectTheme(P_LIGHT) → repo.selectTheme(P_LIGHT)

- [ ] **Step 2: ThemeSettingsContentTest (Robolectric)**

createComposeRule、render with fake state：
- 3 张主题卡显示
- 选中卡显示 primary border（可用 onNodeWithTag(testTag).assertExists()）
- followSystem switch 显示 + 状态正确
- 点击 ThemeCardLight → onSelectLight 被调用

- [ ] **Step 3: 更新 anchor contract**

如果 `PluginSearchPlayAnchorContractTest` 列举了所有 Settings anchor，需追加新增 anchor。否则只确认新 anchor 不冲突。

**Verification:**
```bash
./gradlew :feature:settings:testDebugUnitTest :core:testDebugUnitTest
```

---

## Task 9: 联调验收 + 文档 + 日志

**Files (modify):**
- 文档：`docs/dev-harness/ui/incidents.md`（若需登记）
- spec/plan：本计划与 spec
- 添加日志：在 ViewModel / Repository 关键路径插入 `MfLog.detail(SETTINGS, "theme_select", mapOf(...))`

- [ ] **Step 1: 接通日志**

- `DefaultThemeRepository.selectTheme/setFollowSystem/setBackground/replaceCustomColors` 走 `AppPreferences` 的 `writeRuntimeSetting`，已自动打 `settings_write`。
- `SetCustomThemeViewModel.onImagePicked` 完成后额外打 `theme_image_palette_extracted`：`durationMs, primaryHex, grayRate`。
- `SetCustomThemeViewModel.onColorConfirmed` 后打 `theme_custom_color_patch`：`key, hex`。

- [ ] **Step 2: dev-harness check**

```bash
bash scripts/dev-harness/check.sh
```

确保所有模块 testDebugUnitTest 通过。

- [ ] **Step 3: app debug 构建 + 安装到模拟器验收（人工 / 半自动）**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

验收：
1. 设置 → 主题设置 显示
2. 跟随系统切换有效
3. 三张卡可点选；选中边框正确
4. 自定义卡点击进 setCustomTheme 页
5. 选图 → colors / background 同步更新
6. blur / opacity slider 落盘
7. ColorPicker 改 primary → 立即生效
8. 重启后状态保留

- [ ] **Step 4: 合并回 main**

```bash
cd /Users/zili/code/android/MusicFreeAndroid  # 主工作区
git fetch origin && git checkout main && git pull --ff-only
git merge --squash feat/set-custom-theme
git commit -m "feat(theme): 实现 setCustomTheme 与主题设置子页"  # 中文 conventional commit
```

worktree 是否保留由用户决定；本计划默认 `keep`。

**Verification:**
```bash
git log --oneline -5
```

---

## 全局验收

最终 done 标准：

- [ ] `./gradlew :core:testDebugUnitTest :data:testDebugUnitTest :feature:settings:testDebugUnitTest` 全绿
- [ ] `./gradlew :app:assembleDebug` 成功
- [ ] `bash scripts/dev-harness/check.sh` 全绿
- [ ] 模拟器验收 8 项接受标准全部满足（见 spec §13）
- [ ] main 上 squash commit 落地、worktree 状态明确

