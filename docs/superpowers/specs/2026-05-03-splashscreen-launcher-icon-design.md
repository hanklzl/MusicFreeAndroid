# SplashScreen 与启动图标对齐设计

> 文档状态：当前规范
> 适用范围：仅适用于 Android 原生版启动 SplashScreen 与 launcher icon 对齐原版 RN Android 资源。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 最后校验：2026-05-03

## 背景

MusicFreeAndroid 当前仍使用 Android 模板默认启动图标资源，并且未接入系统 SplashScreen。原版 RN Android 侧已经具备完整的原生启动视觉资源与主题配置，集中在 `../MusicFree/android/app/src/main/`：

- `res/values/styles.xml` 中的 `Theme.App.SplashScreen`
- `res/values/colors.xml` 中的 `splashscreen_background`
- `res/drawable/splashscreen_image.png`
- `res/drawable/spashscreen_branding_image.png`
- `res/drawable/splashscreen.xml`
- `res/mipmap-*` 与 `res/mipmap-anydpi-v26/` 下的 launcher icon 资源
- `ic_launcher-playstore.png`

本次设计目标是在 Android 原生版中复刻 RN 侧启动视觉，并使用 AndroidX Jetpack SplashScreen 支持库承接系统启动页能力。SplashScreen 不使用 Compose 实现。

## 目标

1. 使用 `androidx.core:core-splashscreen` 接入系统 SplashScreen。
2. 逐文件复刻 RN Android 侧启动页与 launcher icon 资源结构。
3. 启动页视觉对齐 RN：背景色为 `#27282C`，使用 RN 同款启动图与 branding 图。
4. 应用启动后进入现有 Compose 内容，不新增 Compose Splash 页面。
5. 不改变现有导航、播放器、插件、首页或业务初始化链路。

## 非目标

- 不迁移 RN Android 的 deep link、音频文件 intent-filter、React Native Activity 配置或 Expo 启动逻辑。
- 不新增启动业务初始化门控，不等待插件、播放器或数据初始化完成后再隐藏 SplashScreen。
- 不加入固定最短展示时长，避免人为拖慢冷启动。
- 不调整应用主题系统、首页 UI 或运行时导航结构。

## 推荐方案

采用“RN 原生资源逐项复刻 + AndroidX SplashScreen”的方案。

Android 原生版在 `:app` 模块中引入 `androidx.core:core-splashscreen`。`MainActivity` 在 `super.onCreate(savedInstanceState)` 之前调用 `installSplashScreen()`。`AndroidManifest.xml` 中 `MainActivity` 使用新的 splash theme，正常应用 theme 保持为 `Theme.MusicFreeAndroid`，并通过 `postSplashScreenTheme` 在 splash 结束后切回正常主题。

SplashScreen 由 AndroidX 与系统控制显示时机。实现不调用 `setKeepOnScreenCondition`，因此 splash 默认保持到首帧内容可绘制为止，然后进入现有 Compose `setContent` 主流程。

## 组件与文件设计

### Gradle

- `gradle/libs.versions.toml`
  - 新增 `coreSplashscreen` 版本。
  - 新增 `androidx-core-splashscreen` library alias。
- `app/build.gradle.kts`
  - 新增 `implementation(libs.androidx.core.splashscreen)`。

### Activity

- `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
  - 引入 `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`。
  - 在 `onCreate()` 中、`super.onCreate(savedInstanceState)` 前调用 `installSplashScreen()`。
  - 保留现有 `enableEdgeToEdge()` 与 Compose `setContent` 流程。
  - 不新增 Compose Splash composable。

### Manifest 与主题

- `app/src/main/AndroidManifest.xml`
  - `application` 继续使用 `@style/Theme.MusicFreeAndroid`。
  - `MainActivity` 使用 `@style/Theme.MusicFreeAndroid.Splash`。
  - `android:icon` 与 `android:roundIcon` 继续引用 `@mipmap/ic_launcher` 与 `@mipmap/ic_launcher_round`。

- `app/src/main/res/values/themes.xml`
  - 保留 `Theme.MusicFreeAndroid`。
  - 新增 `Theme.MusicFreeAndroid.Splash`，继承 `Theme.SplashScreen`。
  - 配置：
    - `windowSplashScreenBackground` 指向 `@color/splashscreen_background`
    - `windowSplashScreenAnimatedIcon` 指向 `@drawable/splashscreen_image`
    - `postSplashScreenTheme` 指向 `@style/Theme.MusicFreeAndroid`
    - `android:windowSplashScreenBrandingImage` 指向 `@drawable/spashscreen_branding_image`

- `app/src/main/res/values/colors.xml`
  - 新增或对齐 `splashscreen_background = #27282C`。
  - 新增或对齐 launcher background 色值 `#27282C`，命名以 RN 资源契约为准。

如 `android:windowSplashScreenBrandingImage` 在当前构建环境中需要 API 分流，则实施时将平台属性移动到 `values-v31` 或使用可编译的资源限定方式；设计目标保持与 RN Android 视觉一致。

### 资源

从 `../MusicFree/android/app/src/main/` 复制以下资源到 `app/src/main/` 的对应位置：

- `res/drawable/splashscreen_image.png`
- `res/drawable/spashscreen_branding_image.png`
- `res/drawable/splashscreen.xml`
- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `res/mipmap-mdpi/ic_launcher.webp`
- `res/mipmap-mdpi/ic_launcher_round.webp`
- `res/mipmap-mdpi/ic_launcher_foreground.webp`
- `res/mipmap-hdpi/ic_launcher.webp`
- `res/mipmap-hdpi/ic_launcher_round.webp`
- `res/mipmap-hdpi/ic_launcher_foreground.webp`
- `res/mipmap-xhdpi/ic_launcher.webp`
- `res/mipmap-xhdpi/ic_launcher_round.webp`
- `res/mipmap-xhdpi/ic_launcher_foreground.webp`
- `res/mipmap-xxhdpi/ic_launcher.webp`
- `res/mipmap-xxhdpi/ic_launcher_round.webp`
- `res/mipmap-xxhdpi/ic_launcher_foreground.webp`
- `res/mipmap-xxxhdpi/ic_launcher.webp`
- `res/mipmap-xxxhdpi/ic_launcher_round.webp`
- `res/mipmap-xxxhdpi/ic_launcher_foreground.webp`
- `ic_launcher-playstore.png`

当前 Android 原生版已有 `res/mipmap-anydpi/ic_launcher.xml`、`res/mipmap-anydpi/ic_launcher_round.xml`、`res/drawable/ic_launcher_background.xml`、`res/drawable/ic_launcher_foreground.xml` 等模板资源。实施时需要替换或移除这些与 RN 资源结构冲突的模板资源，确保最终 APK 不再打包默认 Android launcher 图标。

## 启动行为

1. 用户从 launcher 启动应用。
2. Android 系统读取 `MainActivity` 的 splash theme，显示 RN 同款深色背景、启动图与 branding 图。
3. `MainActivity.onCreate()` 调用 `installSplashScreen()`，AndroidX 处理 Android 12+ 平台 SplashScreen 与 Android 10/11 兼容行为。
4. SplashScreen 保持到首帧内容可绘制，不额外延时。
5. 系统切换到 `Theme.MusicFreeAndroid`，现有 Compose 主界面继续渲染。

## 兼容性

- 项目 minSdk 为 29，AndroidX SplashScreen 支持 Android 10+。
- Android 12+ 使用平台 SplashScreen 语义；Android 10/11 使用 AndroidX compat 路径。
- 资源复制采用 RN Android 侧本地文件，避免重新绘制导致视觉偏差。
- 主题与资源引用保持在 `:app` 模块，不影响其他 feature 模块依赖方向。

## 测试与验收

### 静态验收

- `AndroidManifest.xml` 中 `MainActivity` theme 指向 `Theme.MusicFreeAndroid.Splash`。
- `Theme.MusicFreeAndroid.Splash` 继承 `Theme.SplashScreen`。
- `postSplashScreenTheme` 指回 `Theme.MusicFreeAndroid`。
- `windowSplashScreenBackground`、`windowSplashScreenAnimatedIcon`、branding image 均引用存在的资源。
- `@mipmap/ic_launcher` 与 `@mipmap/ic_launcher_round` 解析到 RN 同款资源，不再使用默认 Android 模板图标。

### 构建验收

- 至少运行 `./gradlew :app:assembleDebug`。
- 若资源限定或 splash 属性导致构建失败，优先采用 Android 官方兼容方式修正资源拆分，不改变视觉目标。

### 运行态验收

- 在设备或模拟器冷启动应用，确认启动页背景色、启动图、branding 图与 RN Android 资源一致。
- 进入首页后无 Compose Splash 页面、无额外路由跳转、无明显白屏闪烁。
- 安装后 launcher 图标与 round icon 视觉对齐 RN 侧图标。
- 如环境允许，使用截图或 APK 资源检查确认 `splashscreen_image.png`、`spashscreen_branding_image.png` 与 launcher 资源已进入 APK。

## 风险与处理

- **平台属性兼容风险**：`android:windowSplashScreenBrandingImage` 是 Android 12+ 平台属性。若直接放在基础 `values/themes.xml` 中构建失败，则拆到 `values-v31` 或使用当前 AGP 可接受的限定写法。
- **资源冲突风险**：当前 Android 原生版已有模板 adaptive icon 资源。实施时需清理冲突资源，避免 manifest 引用解析到旧图标。
- **视觉差异风险**：不同 Android 版本对 splash icon 裁剪和 branding image 支持不同。验收以 RN 资源本身、AndroidX 官方行为和实际设备截图共同判断。
- **启动时长误解风险**：本方案不复刻 RN 的 `preventAutoHideAsync()` 初始化门控，只复刻启动视觉。Splash 隐藏时间由系统首帧机制决定。

## 决策记录

- 选择完全复刻 RN Android 原生启动页资源，而不是只做视觉近似。
- 使用 AndroidX Jetpack SplashScreen 支持库，而不是 Compose Splash 页面。
- 不新增固定展示时长或初始化门控。
- launcher icon 采用 RN Android 资源结构逐项对齐，包括 `mipmap-anydpi-v26/`、foreground webp、background 色值与 Play Store 图标。
