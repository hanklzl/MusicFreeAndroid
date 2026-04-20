---
status: current
last_updated: 2026-04-21
topic: 修复全局网络封面图无法加载
---

# 修复全局网络封面图无法加载

## 背景

用户反馈搜索结果中歌曲封面不显示。进一步确认后，现象扩展到全局：首页推荐歌单、榜单、迷你播放器、播放详情页等所有依赖网络封面的位置，全部回退到 `CoverImage` 里 `MusicNote` 的默认占位图。

## 根因

Coil 从 3.0 起把网络图片加载能力从核心包中移出，必须显式添加 `coil-network-okhttp` 或 `coil-network-ktor` 才能加载 `http(s)://` URL。本仓库 `gradle/libs.versions.toml:76` 只声明了 `coil-compose:3.2.0`，运行时因此没有任何 `NetworkFetcher.Factory` 通过 ServiceLoader 被注册。所有传给 `SubcomposeAsyncImage(model = uri)` 的网络 URL 会直接进入 error 分支，触发 `CoverImage.kt:53-60` 的 `error = { CoverPlaceholder(...) }`，显示音乐符号占位图。

关键事实：

- `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/CoverImage.kt:38-62` 是唯一的封面渲染入口，17 处业务代码都走它。
- `JsBridge.firstImageUrl` 对字段名兼容已足够（`artwork/coverimg/cover_img/pic/...`），下载的 wy 插件里也确实写的是 `artwork`，因此封面字段从 JS → Kotlin 数据类的传递没问题。
- 项目没有自定义 `SingletonImageLoader`，全局走 Coil 默认 `ImageLoader`。

## 修复

在 Gradle 版本目录新增 `coil-network-okhttp` 条目，并在 `core` 模块引入它。无需改任何 Kotlin 代码；Coil 通过 `META-INF/services` 自动把 `OkHttpNetworkFetcherFactory` 注册进默认 `ImageLoader`。

### 改动清单

1. `gradle/libs.versions.toml`
   - 在 `[libraries]` 段新增：
     ```
     coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
     ```
   - 复用已有 `coil = "3.2.0"` 版本引用。

2. `core/build.gradle.kts`
   - 在现有 `implementation(libs.coil.compose)` 下一行新增：
     ```
     implementation(libs.coil.network.okhttp)
     ```
   - 作为 `implementation` 依赖即可：Coil 通过 `META-INF/services` 在运行时加载 `NetworkFetcher.Factory`，下游 feature / app 不需要在编译期可见该类。`core` 是所有 UI 模块的依赖，因此运行时 classpath 会自然包含它。

### 为什么不放在 app 或各 feature 模块

`core` 是所有 UI 模块的直接依赖，且 `CoverImage` 定义在 `core`。把网络依赖放在 `core` 保持「封面加载能力」与「封面组件」同源，避免未来新 feature 忘记加依赖。

### 为什么不用自定义 `ImageLoader`

目前没有任何已知 CDN 拒绝加载项目封面（RN 原版也未注入 Referer/UA）。在无具体故障驱动下自建 `SingletonImageLoader` 属于预防性工程，违反 YAGNI。等实际出现某平台封面被 403 时再单独加。

## 不做的事

- 不新增 Hilt `@Provides ImageLoader`。
- 不改 `MusicFreeApplication`。
- 不改 `CoverImage` 的 API。
- 不改任何插件层 / JsBridge / 搜索链路代码 —— 链路本身没问题。

## 验收

1. 构建：`./gradlew :app:assembleDebug` 成功。
2. 运行态：安装一个带封面字段的插件（如 wy / 元力系），在以下位置均能看到封面图而非音乐符号占位：
   - 搜索结果（歌曲/歌单/专辑/歌手）
   - 首页推荐歌单
   - 榜单详情
   - 歌单详情
   - 专辑详情
   - 艺人详情
   - 迷你播放器
   - 全屏播放详情
3. 网络不可用或 URL 404 时仍应优雅回退到占位图（不崩溃）。

## 风险

极低。只是添加一个由 Coil 官方维护、与 `coil-compose` 同版本号的姊妹依赖。不涉及任何业务逻辑或组件 API。
