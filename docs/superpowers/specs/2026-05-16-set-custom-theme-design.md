# 自定义主题（setCustomTheme）设计

- 状态：草案，直接执行
- 日期：2026-05-16
- 范围：补齐 RN 原版 `setCustomTheme` 页面 + `themeSetting` 选项；让 Android `MusicFreeTheme` 从静态 system dark 切换到响应用户设置的运行时主题；新增 `:feature:settings` 内 `themesetting/` 子包承载主题入口与自定义主题页；新增 `:core/theme/` 内可变 colors 状态源；持久化 selectedThemeId / customColors / background / followSystem 到 DataStore；不动既有播放、插件、数据库 schema。
- 关联文档：`../../../AGENTS.md`（UI Harness / 日志 / 测试规范）、`../../dev-harness/ui/rules.md`、`../../dev-harness/test/rules.md`
- 参考 RN 源码：
  - `../MusicFree/src/core/theme.ts`
  - `../MusicFree/src/pages/setCustomTheme/index.tsx` `.../body.tsx`
  - `../MusicFree/src/pages/setting/settingTypes/themeSetting/index.tsx` `mode.tsx` `background.tsx` `themeCard.tsx`

## 背景

RN 原版有完整三档主题系统：

1. `themeSetting` 子页（在 SettingType=Theme 时展示）：上半 `mode`（跟随系统开关），下半 `background`（亮色卡 / 暗色卡 / 自定义卡）。点击自定义卡跳转 `setCustomTheme`。
2. `setCustomTheme` 子页：选背景图、调整 `blur` 与 `opacity`、12 个可配色（primary / text / appBar / appBarText / musicBar / musicBarText / pageBackground / backdrop / card / placeholder / tabBar / notification）每个走 ColorPicker。
3. 选定后 `theme.selectedTheme = "p-light" | "p-dark" | "custom"`；`custom` 把 dark 配色作为基色，覆盖部分键。

Android 端：

- `core/theme/MusicFreeColors.kt` 中 `LightMusicFreeColors` / `DarkMusicFreeColors` 的 hex 与 RN 完全对齐。
- `MusicFreeTheme(...)` 当前是无参 + `isSystemInDarkTheme()` 静态切换；**没有用户偏好读路径**。
- `feature/settings/SettingsScreen.kt:178` `SettingsType.Theme` 分支只渲染 “待接入” 占位。
- `AppPreferences` 已有 `darkMode: Flow<Boolean?>` 但只在播放器选项里 wired，未真正驱动 Theme。
- `core/navigation/Routes.kt` 没有 `SetCustomThemeRoute`。
- `AppNavHost.kt` 没挂载 setCustomTheme。
- 14 个文件里都用 `MusicFreeTheme.colors`，217 个使用点（grep 计数 214 + 3 个内部 helper）。运行时切换 colors 必须经 `LocalMusicFreeColors`（已存在 `staticCompositionLocalOf`），所以核心改动是把 `MusicFreeTheme()` 改成状态驱动。

注意 RN 的 `custom` 是 "永远基于 dark"（`body.tsx:118` `dark: true` + `darkTheme.colors` 兜底），不是用 `lightTheme` 的一份变体。Android 端遵循同语义。

## 目标

1. 用户在「设置 → 主题设置」可见两组：
   - **显示样式**：跟随系统主题（Switch；开启时根据系统 dark/light 立刻应用 `p-dark` / `p-light`，但不改 `selectedTheme` 在 followSystem=false 时的语义）
   - **主题选择**：三张卡片，亮色（preview `#fff`）/ 暗色（preview `#131313`）/ 自定义（preview = 自定义背景图 url 或占位 "+"），选中卡片有 primary 色 2dp 边框
2. 点击自定义卡跳转 `SetCustomThemeRoute` 全屏自定义页：
   - 上方 460×690 rpx 圆角图片区，点击调起系统图片选择器（`PickVisualMedia.ImageOnly`），选完拷贝到 app private dir、用 Palette 提取 primary/vibrant/average 三个候选色，按 RN `body.tsx:66~108` 的 grayRate 分支生成 5~6 个 colors（`appBar / primary / musicBar / card / tabBar`），整体写入 `customColors` 并切到 `selectedTheme = custom`
   - 两条 slider：`blur 0..30`（默认 20）、`opacity 0.3..1`（默认 0.7），松手才落盘
   - 下方网格 12 个 ColorRow，每行：标签 / 透明棋盘格背景 / 当前色块 / hexa 文本；点击弹 ColorPicker bottomsheet（HSV + alpha + 预设色板 + 文本输入），选完只 patch 该 key
3. App 启动即从 DataStore 恢复 `selectedTheme + customColors + background.{url,blur,opacity} + followSystem`；冷启动期间走默认 dark（与 RN `themeStore = darkTheme` 一致），DataStore first emit 后切换
4. 自定义模式下 app 背景 = `pageBackground` 之下绘制一张背景图（按 `opacity` alpha、按 `blur` dp 模糊）；普通模式下不绘制背景图
5. 主题切换 < 200ms，无重启
6. 所有写操作通过 `AppPreferences` 走 DataStore，与 `BasicSettings` 一致；写入打 `settings_write` log
7. 单元测试覆盖：`AppPreferences` 主题字段读写、自定义 colors 反序列化、`ThemeRepository` 状态切换、`SetCustomThemeViewModel` slider/colors patch 逻辑、`ThemeSettingsContent` 与 `SetCustomThemeContent` Compose 渲染

## 非目标

- 不做 Material You / dynamicColor 联动
- 不做主题导入导出（JSON）；后续可单独 spec
- 不做内置多套预设主题（仅亮 / 暗 / 自定义三档，对齐 RN）
- 不做 R8 keep 规则扩展：colors 是 hex string、不依赖类名反射
- 不动 `MusicFreeColors` 类形状与 21 个字段；只让其中 12 个可被用户覆盖
- 不动数据库（schema 不变；偏好走 DataStore）
- 不重写既有 14 个文件 217 个 `MusicFreeTheme.colors.xxx` 使用点；它们已经走 `LocalMusicFreeColors`，状态化改造完成即生效
- 不做 i18n（与 RN 原版 themeSetting 当前 `t("...")` 一致，本仓库其他设置项都用中文硬编码，保持一致）
- 不动 `core/permissions`（图片选择走 photo picker，免运行时权限）

## §1 关键决策清单

| 决策项 | 取值 | 理由 |
|---|---|---|
| selectedTheme 取值 | `"p-light"` `"p-dark"` `"custom"` | 与 RN `theme.ts:8` `theme.ts:37` `theme.ts:115` 字符串完全一致；后续若做主题 JSON 导入可继续用 |
| custom 基色 | DarkMusicFreeColors 作为兜底 | RN `theme.ts:118-121` `dark: true; colors: { ...darkTheme.colors, ...customColors }` |
| customColors 持久化格式 | 单条 JSON string preference key | 12 个可配色 +1 字典即可；不展开成 12 个 stringPreferencesKey 避免膨胀；用 `kotlinx.serialization` Map<String,String> |
| 背景图存储 | app 内部 files dir `<filesDir>/background<ext>` | 与 RN `dataPath/background.<ext>` 等价；不依赖外部 URI 长期持有 |
| 背景图 URL 失效 | 启动时检查 file exists，否则 url=null 但 colors 保留 | 用户清缓存 / 卸载残留时降级到纯色 |
| Palette 抽色 | androidx.palette `Palette.from(bitmap).generate()` 拿 vibrant / dominant / muted | 替代 RN `react-native-image-colors`；Android 标准库即可 |
| blur 实现 | `Modifier.blur((value).dp, BlurredEdgeTreatment.Unbounded)` | API 31+ 才硬件加速；minSdk=29 上 24/25 fallback 走 fallback 路径；Android `compose-ui` 的 `blur` modifier 在低版本回退为不模糊（不报错），UI 仍可用 |
| ColorPicker | 自实现 HSV + Alpha + 文本 + 预设；不引第三方库 | 项目无现成 ColorPicker；引第三方需评估 R8 / size，自实现 ~150 行 Compose 可控 |
| 默认值 | blur=20, opacity=0.7, followSystem=false | 与 RN `theme.ts:99-101` 一致（RN `body.tsx:152` 默认 0.7，`theme.ts:101` 默认 0.6；以 `body.tsx` 显式 slider 默认为准） |
| 跟随系统行为 | followSystem=true 时：忽略 selectedTheme，系统切换实时切换；followSystem 关闭瞬间不动 selectedTheme | RN `mode.tsx:31-39` 等价：开启时把 system colorScheme 写一次到 selectedTheme |
| AppBar 调色 | 沿用普通 AppBar（`MusicFreeScreenScaffold`） | 主题设置页 / setCustomTheme 都不是特殊 Chrome，符合 ui/rules.md `#rule-no-raw-material3-topappbar` |
| 触发持久化时机 | slider 落盘 = `onValueChangeFinished`；color 落盘 = ColorPicker 确认按钮 | 拖动中只更新内存 state，避免 DataStore 抖动 |

## §2 架构 & 模块归属

### 依赖示意

```
:core                             :data                          :feature:settings
┌──────────────────────┐          ┌──────────────────────┐       ┌──────────────────────────┐
│ theme/                │          │ AppPreferences       │       │ themesetting/            │
│  MusicFreeColors      │          │  + selectedTheme     │◀──────│   ThemeSettingsContent   │
│  MusicFreeTheme(state)│          │  + customColors json │       │   ThemeSettingsViewModel │
│  ThemeState (state    │ ◀────────│  + bgUrl/Blur/Opacity│       │   ThemeCard / ModeRow    │
│   holder + reducer)   │          │  + followSystem      │       │ setcustomtheme/          │
│ navigation/           │          │                       │       │   SetCustomThemeScreen   │
│  SetCustomThemeRoute  │          │                       │       │   SetCustomThemeViewModel│
└──────────────────────┘          │                       │       │   ColorPickerBottomSheet │
                                  └──────────────────────┘       └──────────────────────────┘
        ▲                                                                    ▲
        └────────── :app/MainActivity 用 MusicFreeTheme(state) 包裹整个树 ───┘
```

### 模块归属

| 组件 | 归属模块 | 备注 |
|---|---|---|
| `MusicFreeColors` 不动 | `:core/theme` | data class 形状保持 |
| `MusicFreeTheme(state: ThemeUiState, content)` 改造 | `:core/theme` | 接收外部 state；同时保留无参 overload 用于 preview，但内部使用点都通过新签名 |
| `ThemeUiState` / `ThemeRepository` / `applyOverrides()` | `:core/theme/runtime/` | colors 合并、followSystem 联动；纯 Kotlin 可单测 |
| `SelectedTheme` enum (`P_LIGHT` `P_DARK` `CUSTOM`) | `:core/theme/runtime/` | 与 RN 字符串一一映射 |
| `AppPreferences` 新增 5 个字段 | `:data/datastore` | selectedTheme / customColorsJson / bgUrl / bgBlur / bgOpacity / followSystem |
| `CustomThemeColorsSerializer` | `:data/datastore` 或 `:core/theme/runtime/` | Map<String,String> ↔ JSON；使用 `kotlinx.serialization` |
| `SetCustomThemeRoute` `SettingsType.Theme` 路由（已存在） | `:core/navigation/Routes.kt` | 新加一条 data object |
| `ThemeSettingsContent` `ThemeSettingsViewModel` `ThemeCard` `ModeRow` | `:feature:settings/themesetting/` | 替换 `SettingsScreen.kt:178` Theme 占位 |
| `SetCustomThemeScreen` `SetCustomThemeViewModel` `ColorPickerBottomSheet` `BackgroundPickerSection` `BlurOpacitySliders` `ConfigurableColorGrid` | `:feature:settings/setcustomtheme/` | 全屏页 |
| `SetCustomThemeNavigation.kt` | `:feature:settings/setcustomtheme/navigation/` | NavGraphBuilder ext + Hilt VM |
| MainActivity 注入 `ThemeRepository` 并把 colors 透到 `MusicFreeTheme` | `:app` | 一处改 |
| AppNavHost 加 `setCustomThemeScreen(onBack=...)` | `:app/navigation` | 一处改 |
| FidelityAnchors 新增 4 个 | `:core/ui/FidelityAnchors.kt` | ThemeRoot 已存在；补 5 个子锚 |
| Drawer DrawerSettingsTheme 入口已有 | `:feature:home/drawer` | 不动 |

依赖单向：`:app → :feature:settings → :core/:data`；新增 module 0；新增子包数 2（`themesetting/`、`setcustomtheme/`）。

## §3 数据流与状态

### DataStore 字段（追加到 `AppPreferences.kt`）

```kotlin
val KEY_SELECTED_THEME = stringPreferencesKey("theme_selected")         // "p-light" / "p-dark" / "custom"
val KEY_CUSTOM_COLORS_JSON = stringPreferencesKey("theme_custom_colors_json")
val KEY_THEME_BACKGROUND_URL = stringPreferencesKey("theme_background_url")
val KEY_THEME_BACKGROUND_BLUR = floatPreferencesKey("theme_background_blur")
val KEY_THEME_BACKGROUND_OPACITY = floatPreferencesKey("theme_background_opacity")
val KEY_THEME_FOLLOW_SYSTEM = booleanPreferencesKey("theme_follow_system")
```

Flow + setter 6 对，全部走 `writeRuntimeSetting(...)` 打 `settings_write` 日志（沿用已有 helper）。注意：现有 `darkMode: Flow<Boolean?>` 不删，但被新的主题语义替代；`MainActivity` 不再读它驱动 Theme（保留作为兼容 / debug，但实际使用 0）。

### `ThemeRepository`（新；放 `:core/theme/runtime/ThemeRepository.kt`，避免循环依赖问题：`:core` 不能依赖 `:data`，所以 Repository 在 `:data/repository/`，但其暴露的 `Flow<ThemeUiState>` 类型在 `:core/theme/runtime/`）

实际方案：

- `ThemeUiState` data class 定义在 `:core/theme/runtime/`
- `ThemeRepository` 接口定义在 `:core/theme/runtime/`（只有接口 + 默认值常量，无依赖）
- `DefaultThemeRepository` 实现放 `:data/repository/theme/`，依赖 `AppPreferences`
- Hilt module 在 `:data/di/DataModule.kt` 内 `@Provides` 绑定

```kotlin
// :core/theme/runtime/ThemeUiState.kt
data class ThemeUiState(
    val selected: SelectedTheme,      // P_LIGHT / P_DARK / CUSTOM
    val effectiveColors: MusicFreeColors,  // 已合并 followSystem + customColors，UI 直接消费
    val background: BackgroundInfo?,  // null 表示无背景图
    val followSystem: Boolean,
    val isLoading: Boolean,           // 首帧 DataStore 未到时 true
)

data class BackgroundInfo(
    val url: String?,    // file:// 路径，null 表示无图
    val blur: Float,     // 0..30 dp
    val opacity: Float,  // 0.3..1.0
)

enum class SelectedTheme(val storageKey: String) {
    P_LIGHT("p-light"),
    P_DARK("p-dark"),
    CUSTOM("custom");
    companion object {
        fun fromStorageKey(key: String?): SelectedTheme = entries.firstOrNull { it.storageKey == key } ?: P_DARK
    }
}
```

```kotlin
// :core/theme/runtime/ThemeRepository.kt
interface ThemeRepository {
    val state: Flow<ThemeUiState>
    suspend fun selectTheme(theme: SelectedTheme)
    suspend fun setFollowSystem(enabled: Boolean, currentSystemDark: Boolean)
    suspend fun setBackground(url: String?, blur: Float?, opacity: Float?)
    suspend fun patchCustomColors(patch: Map<String, String>)  // hex string
    suspend fun replaceCustomColors(colors: Map<String, String>)  // 整体替换（图片抽色后调用）
}
```

### `MusicFreeTheme` 改造

```kotlin
@Composable
fun MusicFreeTheme(
    themeState: ThemeUiState,
    content: @Composable () -> Unit,
) {
    val colors = themeState.effectiveColors
    val isDark = themeState.selected != SelectedTheme.P_LIGHT
    val colorScheme = if (isDark) darkColorScheme(...) else lightColorScheme(...)
    CompositionLocalProvider(
        LocalMusicFreeColors provides colors,
        LocalBackgroundInfo provides themeState.background,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

// 保留旧签名给 @Preview 用
@Composable
fun MusicFreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) = MusicFreeTheme(
    themeState = ThemeUiState(
        selected = if (darkTheme) SelectedTheme.P_DARK else SelectedTheme.P_LIGHT,
        effectiveColors = if (darkTheme) DarkMusicFreeColors else LightMusicFreeColors,
        background = null,
        followSystem = false,
        isLoading = false,
    ),
    content = content,
)
```

### MainActivity 集成

```kotlin
@Inject lateinit var themeRepository: ThemeRepository

setContent {
    val systemDark = isSystemInDarkTheme()
    val themeState by themeRepository.state.collectAsStateWithLifecycle(
        initialValue = ThemeUiState(...defaultDark, isLoading = true)
    )
    LaunchedEffect(systemDark, themeState.followSystem) {
        if (themeState.followSystem) {
            themeRepository.selectTheme(if (systemDark) SelectedTheme.P_DARK else SelectedTheme.P_LIGHT)
        }
    }
    MusicFreeTheme(themeState = themeState) {
        Box {
            ThemeBackgroundLayer(themeState.background)  // 仅 selected == CUSTOM 且 url 非空时绘制
            Scaffold(...)  // 既有 Scaffold 不动
        }
    }
}
```

### 背景图层

```kotlin
@Composable
fun ThemeBackgroundLayer(info: BackgroundInfo?) {
    if (info?.url == null) return
    AsyncImage(
        model = info.url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .blur(info.blur.dp)
            .graphicsLayer { alpha = info.opacity },
    )
}
```

只在自定义主题且 url 存在时绘制；与其它 chrome 互不影响。

## §4 自定义主题颜色字典

12 个可配色键（对齐 RN `theme.ts:194-206`，但 RN 额外有 `text`，我们补上：13 个）：

```
primary text appBar appBarText musicBar musicBarText pageBackground
backdrop card placeholder tabBar notification
```

`text` 在 RN 列表中存在；本 Android 端可配色保持 12 个（去掉 `text`）。若取消歧义，沿用 RN 13 个：**采纳 13 个**（含 `text`），便于深色背景下用户调字色。

Map<String, String> 中 key 与 `MusicFreeColors` 字段名一对一。`applyOverrides(base: MusicFreeColors, overrides: Map<String, String>): MusicFreeColors` 使用 reflection-free 手写 when 分支，避免 R8 / 反射风险。

## §5 setCustomTheme 颜色推导算法

照搬 RN `body.tsx:65-108`：

```kotlin
data class PaletteColors(val primary: Color, val average: Color, val vibrant: Color)

fun derivedThemeColorsFor(palette: PaletteColors): Map<String, String> {
    val primary = palette.primary
    val gray = grayRate(primary)  // 与 RN colorUtil.grayRate 等价：(r - g) / 255f + (g - b) / 255f
    val primaryHex = primary.toHexString()
    return when {
        gray < -0.4f -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.darken(gray * 5).toHexString(),
            "musicBar" to primaryHex,
            "card" to "#33000000",
            "tabBar" to primary.copy(alpha = 0.2f).toHexString(),
        )
        gray > 0.4f -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.darken(gray * 5).toHexString(),
            "musicBar" to primaryHex,
            "card" to "#33000000",
        )
        else -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.saturate(abs(gray) * 2 + 2f).toHexString(),
            "musicBar" to primaryHex,
            "card" to "#33000000",
        )
    }
}
```

`darken / saturate / grayRate` 抽到 `:core/theme/runtime/ColorMath.kt`，无外部依赖，纯函数易测。

Palette 抽色：

```kotlin
suspend fun extractPalette(context: Context, uri: Uri): PaletteColors = withContext(Dispatchers.IO) {
    val bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
    val palette = Palette.from(bitmap).generate()
    PaletteColors(
        primary = Color(palette.getDominantColor(0xFF3FA3B5.toInt())),
        average = Color(palette.getMutedColor(palette.getDominantColor(0xFF3FA3B5.toInt()))),
        vibrant = Color(palette.getVibrantColor(palette.getDominantColor(0xFF3FA3B5.toInt()))),
    )
}
```

## §6 UI 结构

### `ThemeSettingsContent`（替换 SettingsScreen.kt:178 Theme 分支）

LazyColumn，两 section：

1. **显示样式** `SettingSectionCard("显示样式", FidelityAnchors.Settings.ThemeSectionMode)`
   - `SettingSwitchRow("跟随系统主题", checked=followSystem, ...)`
2. **主题选择** `SettingSectionCard("主题")`
   - 水平 LazyRow，三张 `ThemeCard`：亮色 / 暗色 / 自定义
   - `ThemeCard` 尺寸 `160 × 160 rpx`（含边框），内部 `136 × 136 rpx` 圆角 `12 rpx`；选中时 2dp `primary` 边框；下方 label 居中 `subTitle` 字号

### `SetCustomThemeScreen`

`MusicFreeScreenScaffold(title = "自定义主题")` + 右上角 “完成” 按钮 = pop。内容 ScrollView：

1. 顶部图片 `Image(460 × 690 rpx)` 圆角 `12 rpx`；空态显示 “+” 中央占位
2. 两条 slider：blur / opacity；左侧标签 + 右侧 slider，sliders 走 Material3 `Slider`，`onValueChangeFinished` 落盘
3. 13 个 ColorRow 2-column grid：每个 ColorItem 内部：标签 + 透明棋盘格背景（10px tile）+ 当前色块 + hexa 文本

点击 ColorItem 弹 `ColorPickerBottomSheet`：

- HSV 圆盘（自绘 Canvas）
- alpha slider
- 6 个预设色按钮（主题常见色）
- 文本输入 `#FFRRGGBB`，校验失败按钮 disabled
- 底部 取消 / 确认 双按钮

## §7 FidelityAnchors 新增

```kotlin
object Settings {
    // ... 既有 ...
    const val ThemeRoot = "settings.theme.root"          // 已有
    const val ThemeSectionMode = "settings.theme.section.mode"
    const val ThemeSectionTheme = "settings.theme.section.theme"
    const val ThemeFollowSystemSwitch = "settings.theme.followSystemSwitch"
    const val ThemeCardLight = "settings.theme.card.light"
    const val ThemeCardDark = "settings.theme.card.dark"
    const val ThemeCardCustom = "settings.theme.card.custom"
}

object Screen {
    // ... 既有 ...
    const val SetCustomThemeRoot = "screen.setCustomTheme.root"
}

object SetCustomTheme {
    const val Image = "setCustomTheme.image"
    const val SliderBlur = "setCustomTheme.slider.blur"
    const val SliderOpacity = "setCustomTheme.slider.opacity"
    const val ColorItemPrefix = "setCustomTheme.colorItem."  // + key name
    const val ColorPickerSheet = "setCustomTheme.colorPicker.sheet"
    const val ColorPickerConfirm = "setCustomTheme.colorPicker.confirm"
}
```

## §8 路由

`:core/navigation/Routes.kt` 追加：

```kotlin
@Serializable
data object SetCustomThemeRoute
```

`:app/navigation/AppNavHost.kt`：

```kotlin
setCustomThemeScreen(onBack = { navController.popBackStack() })
```

`SettingsScreen.kt` 新增参数 `onNavigateToSetCustomTheme: () -> Unit`，并把它穿到 `ThemeSettingsContent.onCustomThemeClick`；`AppNavHost.settingsScreen(...)` 传 `onNavigateToSetCustomTheme = { navController.navigate(SetCustomThemeRoute) }`。

## §9 日志埋点

新增 event：

- `theme_select` fields: `selected, prevSelected, followSystem`
- `theme_custom_color_patch` fields: `key, hex`
- `theme_background_set` fields: `hasUrl, blur, opacity, source` (source=picker/slider)
- `theme_image_palette_extracted` fields: `durationMs, primaryHex, grayRate`
- 既有 `settings_write` 自动覆盖 setter 路径

LogCategory：复用 `SETTINGS`（无需新增）。

## §10 测试覆盖

| 测试位置 | 测试内容 |
|---|---|
| `:data/src/test/.../AppPreferencesThemeTest.kt` | 6 个新字段读写、默认值、JSON parse 失败回退到空 map |
| `:core/src/test/.../theme/runtime/ColorMathTest.kt` | grayRate / darken / saturate 边界 + RN 已知样例 |
| `:core/src/test/.../theme/runtime/ApplyOverridesTest.kt` | 12 个 key + 1 个未知 key + 空 map 行为 |
| `:data/src/test/.../DefaultThemeRepositoryTest.kt` | `runTest(dispatcher)` + Turbine：selectTheme / setFollowSystem / patchCustomColors / setBackground 各驱动正确 state |
| `:feature:settings/src/test/.../themesetting/ThemeSettingsViewModelTest.kt` | followSystem toggle → currentSystemDark 注入 → selectTheme 同步；选中卡 click 行为 |
| `:feature:settings/src/test/.../setcustomtheme/SetCustomThemeViewModelTest.kt` | slider patch 落盘 / colorPatch / 图片选完调 derivedThemeColorsFor + replaceCustomColors，Palette 用 fake |
| `:feature:settings/src/test/.../themesetting/ThemeSettingsContentTest.kt` (Robolectric) | LazyColumn 渲染 / 三卡显示 / 选中边框 |
| `:feature:settings/src/test/.../setcustomtheme/SetCustomThemeContentTest.kt` (Robolectric) | sliders / 13 个 colorItem 显示 / colorPicker show-hide |

测试遵循 `docs/dev-harness/test/rules.md` `#rule-runtest-mandatory`：所有 VM 测试用 `runTest(mainDispatcherRule.dispatcher)` + `advanceUntilIdle()`，禁止 `runBlocking + first(predicate)`。

DataStore 单测继续用 `data/datastore/AppPreferencesTestHelpers`（沿用现有模式），不新增 instrumentation。

## §11 R8 / 序列化

`SelectedTheme` 是非序列化 enum，仅做内部 state。

`Map<String,String>` JSON 用 `kotlinx.serialization.json.Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map)` 显式调用，不依赖 `@Serializable` 类（无反射）；不需要新增 keep 规则。

`SetCustomThemeRoute` 是 `data object`，跟其他既有 route 一致，沿用现有 navigation 序列化路径，无需新增 keep。

## §12 风险与边界

- **图片选择器跨进程权限**：用 PickVisualMedia 返回的 content URI，立刻 `contentResolver.openInputStream()` 拷贝到 app private dir 内 `background.<ext>`，之后只使用 `file://` URI；避免 grant URI 过期问题。
- **blur 在 API 29-30 上的兜底**：`Modifier.blur` 在 Android < 12 上 fallback 为无 blur，不会崩；用户可见上仅"未模糊"。文档化于实现注释。
- **大背景图内存**：拷贝时 inSampleSize 缩到屏幕宽 × 1.5 上限（约 1080 × 1920），Palette 抽色用同一缩放后的 bitmap；不存原图。
- **followSystem + selectedTheme 冲突**：followSystem=true 时，selectedTheme 跟随 system 实时变；followSystem=false 时，selectedTheme 由用户卡片选择驱动。这点与 RN 略有不同（RN followSystem=true 只在切换瞬间应用一次后端 `theme.selectedTheme`），但 Android 端 followSystem=true 让 system 切换实时跟随更符合 Android 用户预期。
- **冷启动闪烁**：DataStore 第一帧延迟可能导致用户看到默认 dark 0.1s。可接受，与 RN 一致；后续可考虑 SharedPreferences 同步加载，本期不做。
- **跨进程**：MediaSessionService 不消费 colors，因此 Theme 状态不影响后台播放。

## §13 接受标准

1. 设置 → 主题设置 显示两 section，可见 followSystem switch + 3 卡。
2. 点亮色 / 暗色卡：500ms 内全 app 颜色切换；选中卡边框正确。
3. 点自定义卡：进 SetCustomTheme 页；首次进入背景图为占位，13 个 colors 显示当前生效色。
4. 选图片后 1s 内：背景图显示、5~6 个 colors 被推导覆盖、回到设置页"自定义"卡 preview 显示新背景图。
5. 拖动 blur / opacity：松手后值持久化，重启后保留。
6. 点单个 color 弹 ColorPicker，确认后该 color 立即生效。
7. 关闭 app 重启：selectedTheme / customColors / bg 全部恢复，followSystem 行为正确。
8. followSystem=true 下，系统切换 dark/light 立刻反映；selectedTheme=custom 时 followSystem=true 被开启会自动切换到对应 light/dark 卡（与 RN `mode.tsx` 等价）。
9. 全部单测通过；`bash scripts/dev-harness/check.sh` 通过；`./gradlew :app:assembleDebug` 通过。

