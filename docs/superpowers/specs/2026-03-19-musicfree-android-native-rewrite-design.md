# MusicFree Android 原生重写设计文档

> 文档状态：当前参考
> 适用范围：项目早期总体设计与分层思路。
> 直接执行：否
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 备注：架构思路可参考，版本与进度类信息以 AGENTS 与代码事实为准。
> 最后校验：2026-04-11


## 概述

将 MusicFree 从 React Native 重写为 Android 原生技术栈（Jetpack Compose），以优化性能（启动速度、内存占用、流畅度）。采用多模块 MVVM 架构，分阶段实施，第一阶段聚焦核心播放功能。

## 原版参考

原版 MusicFree 是一个基于 React Native 的插件化音乐播放器，代码位于 `../MusicFree`。所有数据模型、数据结构和业务逻辑的实现，都应先阅读原版代码作为参考蓝本。

关键参考路径：
- 类型定义：`src/types/`（music.d.ts、plugin.d.ts、artist.d.ts 等）
- 插件系统：`src/core/pluginManager/`
- 播放器：`src/core/trackPlayer/`
- 状态管理：`src/core/` 下各模块

## 技术栈

| 层面 | 技术选型 |
|------|----------|
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + 多模块 |
| DI | Hilt |
| 播放 | Media3 (ExoPlayer) + MediaSessionService |
| 插件 | QuickJS (quickjs-android) |
| 数据库 | Room |
| 偏好 | DataStore |
| 异步 | Kotlin Coroutines + Flow |
| 导航 | Compose Navigation (类型安全 API) |
| 图片 | Coil |
| 网络 | OkHttp |
| Min SDK | 29 (Android 10) |
| Target SDK | 36 |

## 模块划分

### 模块结构

```
:core          → 通用基础（主题、工具类、基础 Compose 组件、通用类型定义）
:data          → Room 数据库 + DataStore + Repository
:player        → Media3 播放引擎 + MediaSessionService
:plugin        → QuickJS 插件引擎（加载/执行/管理 JS 插件）
:feature:home  → 首页（本地音乐列表、播放列表入口）
:feature:player-ui → 播放器全屏 UI（封面、歌词、进度条、控制）
:feature:search    → 搜索页（调用插件搜索）
:feature:settings  → 设置页
:app           → Application 入口、导航图、DI 组装
```

### 依赖方向（单向）

```
:app → :feature:* → :data, :player, :plugin → :core
                                :plugin → :data（插件持久化）
```

- feature 模块之间不互相依赖，通过 `:app` 层的导航连接
- `:player`、`:data` 互相独立，都依赖 `:core`
- `:plugin` 依赖 `:core` 和 `:data`（用于持久化插件信息）
- `:core` 无上层依赖

## 模块详细设计

### Core 模块

```
core/
  theme/       → Material3 主题定义（颜色、字体、形状）
  ui/          → 通用 Compose 组件（AppBar、LoadingIndicator、EmptyState 等）
  model/       → 通用数据模型（MusicItem、Artist、Album、Playlist、LyricLine 等）
  util/        → 工具类（文件操作、格式化、日期等）
  navigation/  → 导航 Route 定义（sealed class/interface）
  di/          → 通用 Hilt 限定符和作用域
```

关键数据模型示例（具体字段实现时参考原版 `src/types/`）：

```kotlin
data class MusicItem(
    val id: String,
    val platform: String,     // 来源标识（local / 插件 ID），与 id 构成复合主键
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,       // 毫秒（注意：原版使用秒，需转换）
    val url: String?,         // 插件返回的默认播放 URL（调用 getMediaSource 前的 fallback）
    val artwork: String?,     // 封面图 URL/路径
    val qualities: Map<PlayQuality, QualityInfo>?, // 可用音质及对应信息
)

data class QualityInfo(
    val url: String?,
    val size: Long?,          // 文件大小（字节）
)

data class MediaSourceResult(
    val url: String,          // 播放地址
    val headers: Map<String, String>?, // 自定义 HTTP 请求头
    val userAgent: String?,   // 自定义 UserAgent
    val quality: PlayQuality?,
)

data class Playlist(
    val id: String,
    val name: String,
    val coverUri: String?,
    val items: List<MusicItem>,
)

enum class PlayQuality { LOW, STANDARD, HIGH, SUPER }
enum class RepeatMode { OFF, ONE, ALL }  // 与 Media3 对齐，shuffle 独立为 shuffleEnabled: Boolean
```

### Data 模块

```
data/
  db/
    AppDatabase.kt          → Room 数据库定义
    entity/                  → 数据库实体（参考原版类型定义）
    dao/                     → DAO 接口
    converter/               → Room 类型转换器
  datastore/
    AppPreferences.kt        → DataStore（播放偏好、主题设置等）
  repository/
    MusicRepository.kt       → 音乐数据仓库
    PlaylistRepository.kt    → 播放列表仓库
    HistoryRepository.kt     → 播放历史仓库
  mapper/                    → Entity ↔ Core Model 映射
```

职责划分：
- **Room** — 歌曲元数据、播放列表、播放历史、插件信息等结构化数据
- **DataStore** — 用户偏好设置（重复模式、音质选择、主题等轻量配置）
- **Room** 同时存储播放队列和当前播放状态（队列可能很大，不适合 DataStore）
- **Repository** — 对上层暴露干净的接口，隐藏 Room/DataStore 细节，返回 `Flow<T>` 实现响应式
- Entity 和 Core Model 分离，通过 mapper 转换，数据库变更不影响上层
- Playlist 与 MusicItem 在 Room 中使用 junction table（多对多关系），避免将大列表嵌入单个实体

### Player 模块

```
player/
  service/
    PlaybackService.kt       → MediaSessionService，后台播放 + 通知栏控制
  controller/
    PlayerController.kt      → 播放控制接口（play/pause/seek/next/prev）
    PlayerState.kt            → 播放状态（当前曲目、进度、播放/暂停、重复模式）
  queue/
    PlayQueue.kt              → 播放队列管理（增删、排序、随机）
  di/
    PlayerModule.kt           → Hilt 模块，提供 ExoPlayer、MediaSession 实例
```

核心设计：
- **PlaybackService** 继承 `MediaSessionService`，管理 ExoPlayer 实例和 MediaSession
- **PlayerController** 封装 Media3 的 `MediaController`，通过 `MediaController` 与 `PlaybackService` 通信（遵循 Media3 的 client-session 架构）。对上层暴露播放状态为 `StateFlow`，feature 模块观察状态变化
- **播放队列** 独立管理，支持插入、移除。Shuffle 模式通过 Media3 的 `shuffleModeEnabled` 实现，退出 shuffle 时恢复原始顺序
- **通知栏** 由 Media3 自动处理（MediaSession 集成），支持锁屏控制、耳机按键
- **音频焦点** 由 ExoPlayer 内置处理
- **PlayerState** 包含 `shuffleEnabled: Boolean`，与 `RepeatMode` 分离，与 Media3 API 对齐

状态流向：
```
Feature UI → PlayerController (MediaController) → PlaybackService (ExoPlayer + MediaSession)
                     ↓ 暴露
            StateFlow<PlayerState>（当前曲目、进度、播放状态、重复模式、shuffle）
```

### Plugin 模块

```
plugin/
  engine/
    JsEngine.kt              → QuickJS 运行时封装（初始化、执行、销毁）
    JsBridge.kt               → Kotlin ↔ JS 桥接（注入原生能力：网络请求、文件访问等）
  manager/
    PluginManager.kt          → 插件生命周期管理（安装、加载、卸载、列表）
    PluginInfo.kt             → 插件元信息（名称、版本、作者、支持的功能）
  api/
    PluginApi.kt              → 插件对外接口（search、getMediaSource、getLyric 等）
  di/
    PluginModule.kt           → Hilt 模块
```

核心设计：
- **JsEngine** 管理 QuickJS 运行时，每个插件运行在独立的 JS 上下文中，互相隔离。选择 QuickJS 而非 J2V8，因 J2V8 已停止维护且对现代 Android NDK 兼容性差；QuickJS 轻量、启动快、有成熟的 Android 绑定（quickjs-android）
- **JsBridge** 为 JS 侧注入原生能力，并提供 `require()` shim 以兼容现有插件生态：
  - 内置 JS 库（打包到 assets）：`cheerio`、`crypto-js`、`axios`（polyfill，底层走 OkHttp）、`dayjs`、`big-integer`、`qs`、`he`、`webdav`
  - 原生桥接：网络请求（OkHttp）、本地存储读写
  - 参考原版 `src/core/pluginManager/plugin.ts` 中的 `require()` 实现
- **PluginManager** 负责：
  - 从本地文件或 URL 安装插件
  - 解析插件元信息
  - 维护已安装插件列表（持久化到 `:data` 模块）
  - 通过 `platform` 字段路由调用到正确的插件（对应原版 `getByMedia()`）
- **PluginApi** 统一调用入口，将 Kotlin 调用转为 JS 函数调用，将 JS 返回值转为 Kotlin 模型。`getMediaSource(musicItem, quality)` 接受目标音质参数，返回 `MediaSourceResult`（包含 url、headers、userAgent）

线程模型：
- QuickJS 实例在专用后台线程运行，所有 JS 调用通过协程调度
- 插件调用结果通过 `suspend fun` 返回，上层无感知线程细节
- 插件调用超时：默认 30 秒，超时后取消并返回错误

错误处理：
- 插件调用失败（超时、异常）时，`PluginApi` 返回 `Result<T>` 类型，上层决定重试或跳过
- `getMediaSource` 返回 null 时，播放器自动跳到下一曲

第一阶段先支持 `search` 和 `getMediaSource` 两个核心接口。

参考原版代码：`src/core/pluginManager/` 和 `src/types/plugin.d.ts`。

### Feature 模块

每个 feature 模块遵循统一的 MVVM 结构：

```
feature:home/
  HomeScreen.kt               → Compose UI
  HomeViewModel.kt            → ViewModel，持有 UI 状态
  HomeUiState.kt              → UI 状态定义（sealed interface）
  navigation/
    HomeNavigation.kt          → 导航注册扩展函数

feature:player-ui/
  PlayerScreen.kt              → 全屏播放器 UI（封面、进度、控制按钮）
  PlayerViewModel.kt
  component/
    MiniPlayer.kt              → 底部迷你播放栏（跨页面常驻）

feature:search/
  SearchScreen.kt              → 搜索页（调用插件搜索）
  SearchViewModel.kt

feature:settings/
  SettingsScreen.kt            → 设置页（主题、音质、插件管理入口）
  SettingsViewModel.kt
```

### 导航设计

在 `:app` 模块中统一组装导航图：

```kotlin
// app/navigation/AppNavHost.kt
NavHost(navController, startDestination = HomeRoute) {
    homeScreen(onNavigateToPlayer = { ... })
    playerScreen(onBack = { ... })
    searchScreen(onBack = { ... })
    settingsScreen(onBack = { ... })
}
```

- 每个 feature 模块暴露 `NavGraphBuilder.xxxScreen()` 扩展函数
- Route 定义为 `@Serializable` data object / data class（Compose Navigation 类型安全 API）
- feature 模块之间不直接引用，通过回调 lambda 传递导航动作

### 迷你播放栏

`MiniPlayer` 组件在 `:app` 层的 `Scaffold` 中作为 `bottomBar` 常驻显示，观察 `PlayerController` 的状态。

## UI 还原策略

目标：与原版 MusicFree 在视觉和交互上完全对齐。

### 1. 设计 Token 提取

从原版代码中提取所有设计 Token，转换为 Compose 对应实现：

| 原版来源 | 内容 | Compose 对应 |
|----------|------|-------------|
| `src/constants/uiConst.ts` | 字体大小（tag/description/subTitle/content/title/appbar）、字重（400-800）、图标尺寸（small~large） | `core/theme/` 中的 Typography 和 Dimension 常量 |
| `src/core/theme.ts` | 17+ 语义色（text/textSecondary/primary/background/appBar/musicBar/divider 等），亮/暗双主题 | Material3 ColorScheme 自定义扩展 |
| `src/utils/rpx.ts` | RPX 响应式尺寸系统（基于 750px 设计稿） | 自定义 `rpx()` 扩展函数，基于 `LocalConfiguration.current.screenWidthDp` |
| `src/constants/commonConst.ts` | 动画时长（fast:150ms/normal:250ms/slow:500ms） | `core/theme/` 中的动画常量 |

**RPX 转换方案：**
```kotlin
// 原版: rpx(value) = (value / 750) * minWindowEdge
// Compose 等效:
@Composable
fun rpx(value: Int): Dp {
    val config = LocalConfiguration.current
    val minEdge = min(config.screenWidthDp, config.screenHeightDp)
    return ((value.toFloat() / 750f) * minEdge).dp
}
```

### 2. 图标资源

原版使用 70+ 个自定义 SVG 图标（`src/assets/icons/`）。处理方式：

- 直接从原版项目拷贝 SVG 文件
- 使用 Android Studio 的 Vector Asset 工具批量转换为 Android Vector Drawable（XML）
- 放入 `:core` 模块的 `res/drawable/` 中
- 封装为 `Icon` composable，对齐原版 `<Icon name="play" size={42} />` 的使用方式

### 3. 主题系统映射

原版的 17+ 语义色超出 Material3 默认 ColorScheme 范围，需要扩展：

```kotlin
// 自定义扩展色，覆盖原版所有语义色
data class MusicFreeColors(
    val text: Color,
    val textSecondary: Color,
    val primary: Color,
    val background: Color,
    val pageBackground: Color,
    val appBar: Color,
    val appBarText: Color,
    val musicBar: Color,
    val musicBarText: Color,
    val tabBar: Color,
    val divider: Color,
    val placeholder: Color,
    val backdrop: Color,
    val card: Color,
    val success: Color,
    val danger: Color,
    val info: Color,
    val notification: Color,
)

// 通过 CompositionLocal 提供
val LocalMusicFreeColors = staticCompositionLocalOf { lightColors }
```

### 4. 基础组件对齐

原版 37+ 基础组件 → Compose 对应实现（放在 `:core/ui/`）：

| 原版组件 | Compose 实现 |
|----------|-------------|
| ThemeText | 主题感知 Text composable，支持 fontSize/fontWeight/color 语义参数 |
| Icon / IconButton | 封装 Vector Drawable，支持 name + size |
| ListItem（含子组件） | 可组合 ListItem（Icon/Text/Image 插槽） |
| AppBar | TopAppBar 自定义，支持菜单和返回按钮 |
| Button (primary/normal) | 主题感知按钮 |
| FastImage | Coil AsyncImage 封装 |
| SortableFlatList | LazyColumn + 拖拽排序（reorderable 库） |
| PageBackground | Box + 背景图/模糊效果 |
| Divider, Chip, Tag, Loading, Empty | 各自的 Compose 实现 |

### 5. 动画与手势对齐

| 原版实现 | Compose 对应 |
|----------|-------------|
| react-native-reanimated `withTiming()` | `animateDpAsState()` / `Animatable` |
| react-native-gesture-handler `Tap/LongPress/Race` | Modifier.combinedClickable / Modifier.pointerInput |
| Easing.out(Easing.exp) | FastOutSlowInEasing 或自定义 Easing |
| Slider（进度条） | Material3 Slider |

### 6. 截图驱动验证

每个里程碑的 UI 验收流程：
1. 编译运行原版 MusicFree（`cd ../MusicFree && npx react-native run-android`）
2. 对相关页面截图作为基准参考
3. 实现 Compose 页面后，截图对比，调整至视觉一致
4. 重点关注：间距、字号、颜色、圆角、阴影、动画时序

### 7. 资源直接复用

以下资源直接从原版项目拷贝：
- SVG 图标文件（`src/assets/icons/`）
- 占位图、默认封面等图片资源
- 亮/暗主题色值（从 `src/core/theme.ts` 提取精确 hex 值）

## 第一阶段：分步目标与验收标准

第一阶段拆分为 6 个里程碑，每个里程碑独立可验证。

---

### 里程碑 1：项目脚手架与多模块搭建

**目标：** 搭建多模块 Gradle 项目结构，所有模块能编译通过，DI 框架就位。

**交付内容：**
- 创建所有 Gradle 模块：`:core`、`:data`、`:player`、`:plugin`、`:feature:home`、`:feature:player-ui`、`:feature:search`、`:feature:settings`、`:app`
- 配置模块间依赖关系（单向依赖）
- 配置 Hilt（`:app` 中 `@HiltAndroidApp`，各模块 `@HiltViewModel` 可注入）
- `:core` 中定义 Material3 主题和基础导航 Route
- `:app` 中搭建基础 `NavHost` 和 `Scaffold`（含空白占位页面）

**验收标准：**
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] App 可安装启动，显示空白首页，可导航到各占位页面
- [ ] 单元测试：Hilt 注入测试（验证 DI 图完整性）
- [ ] 单元测试：导航 Route 序列化/反序列化测试

---

### 里程碑 2：数据层（Core Model + Room + DataStore）

**目标：** 实现完整的数据持久化层，包括数据模型、数据库、偏好存储和 Repository。

**交付内容：**
- `:core/model/` 中定义所有数据模型（参考原版 `src/types/`）：MusicItem、Playlist、MediaSourceResult、QualityInfo 等
- `:data/db/` 中实现 Room 数据库：
  - Entity：MusicItemEntity、PlaylistEntity、PlaylistMusicCrossRef（junction table）、PlayQueueEntity
  - DAO：MusicDao、PlaylistDao、PlayQueueDao
- `:data/datastore/` 中实现 AppPreferences（DataStore）
- `:data/repository/` 中实现 MusicRepository、PlaylistRepository
- `:data/mapper/` 中实现 Entity ↔ Model 映射

**验收标准：**
- [ ] `./gradlew :data:assembleDebug` 编译通过
- [ ] 单元测试：所有 mapper 的双向转换测试
- [ ] 单元测试：DataStore 读写测试
- [ ] 集成测试：Room DAO 的 CRUD 测试（使用 in-memory database）
  - 插入/查询/更新/删除 MusicItem
  - 创建 Playlist 并关联 MusicItem（junction table）
  - 播放队列的保存与恢复
- [ ] 集成测试：Repository 层的 Flow 响应式测试（数据变更能被观察到）

---

### 里程碑 3：播放引擎（Media3 + 后台服务）

**目标：** 实现基于 Media3 的播放引擎，支持后台播放和系统集成。

**交付内容：**
- `:player/service/PlaybackService.kt` — MediaSessionService 实现
- `:player/controller/PlayerController.kt` — 封装 MediaController，暴露 StateFlow<PlayerState>
- `:player/controller/PlayerState.kt` — 播放状态数据类
- `:player/queue/PlayQueue.kt` — 播放队列管理
- `:player/di/PlayerModule.kt` — Hilt 提供 ExoPlayer、MediaSession

**验收标准：**
- [ ] `./gradlew :player:assembleDebug` 编译通过
- [ ] 单元测试：PlayQueue 的增删排序、shuffle 切换与原始顺序恢复
- [ ] 单元测试：PlayerState 状态转换测试
- [ ] 集成测试：PlaybackService 生命周期测试（启动、绑定、解绑）
- [ ] 集成测试：给定音频文件 URI，验证 PlayerController 能驱动播放/暂停/seek/上下曲
- [ ] 集成测试：MediaSession 集成测试（通知栏显示、媒体按钮响应）
- [ ] 集成测试：音频焦点测试（模拟来电中断后恢复）

---

### 里程碑 4：本地音乐扫描与播放 UI

**目标：** 实现端到端的本地音乐播放体验：扫描 → 展示 → 播放。

**交付内容：**
- `:feature:home/` — 首页 UI：本地音乐列表（LazyColumn）、扫描触发、播放列表入口
- `:feature:home/` — HomeViewModel：调用 MusicRepository 获取本地音乐
- `:feature:player-ui/` — 全屏播放器 UI：封面、进度条、播放控制按钮、重复模式/shuffle 切换
- `:feature:player-ui/component/MiniPlayer.kt` — 底部迷你播放栏
- `:app` — 集成 MiniPlayer 到 Scaffold bottomBar
- 本地音乐扫描逻辑（MediaStore 查询）

**验收标准：**
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] App 可在真机上运行：
  - 授权存储权限后，扫描并展示设备上的音乐文件
  - 点击歌曲开始播放，底部 MiniPlayer 显示当前曲目
  - 点击 MiniPlayer 进入全屏播放器
  - 全屏播放器支持播放/暂停/上下曲/进度拖拽/重复模式切换/shuffle
  - 退到后台后继续播放，通知栏显示播放控制
- [ ] 单元测试：HomeViewModel 状态测试（加载中/空列表/有数据）
- [ ] 单元测试：PlayerViewModel 状态测试
- [ ] 集成测试：MediaStore 扫描返回正确数据格式

---

### 里程碑 5：播放列表管理

**目标：** 实现播放列表的完整 CRUD 和播放队列管理。

**交付内容：**
- 首页增加播放列表 tab/区域
- 播放列表创建/重命名/删除对话框
- 播放列表详情页（歌曲列表、移除歌曲）
- 歌曲长按菜单 → 添加到播放列表
- 播放队列页面（查看当前队列、移除、拖拽排序）

**验收标准：**
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] App 功能验证：
  - 创建新播放列表、重命名、删除
  - 将歌曲添加到播放列表
  - 从播放列表中移除歌曲
  - 点击播放列表开始播放其中的歌曲
  - 查看和管理当前播放队列
- [ ] 单元测试：PlaylistViewModel CRUD 状态测试
- [ ] 集成测试：播放列表的完整 CRUD 流程（创建 → 添加歌曲 → 播放 → 删除）

---

### 里程碑 6：插件引擎与搜索

**目标：** 集成 QuickJS 引擎，支持加载现有 MusicFree JS 插件，实现搜索和在线播放。

**交付内容：**
- `:plugin/engine/` — QuickJS 引擎封装、JS Bridge、require() shim
- `:plugin/manager/` — 插件安装（本地文件/URL）、加载、卸载
- `:plugin/api/` — PluginApi 实现 search、getMediaSource
- `:feature:search/` — 搜索页 UI（搜索框、结果列表、插件选择）
- `:feature:settings/` — 设置页（插件管理：安装/查看/卸载）
- 打包 JS 依赖库到 assets（cheerio、crypto-js、dayjs 等）

**验收标准：**
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] App 功能验证：
  - 从 URL 或本地文件安装一个现有 MusicFree 插件
  - 设置页显示已安装插件列表，可卸载
  - 搜索页选择插件 → 输入关键词 → 显示搜索结果
  - 点击搜索结果播放在线音乐（通过 getMediaSource 获取播放地址）
- [ ] 单元测试：JsEngine 基础执行测试（执行 JS 表达式、调用函数）
- [ ] 单元测试：JsBridge require() shim 测试（require('dayjs') 等能正确返回）
- [ ] 单元测试：PluginManager 解析插件元信息测试
- [ ] 集成测试：加载一个测试插件 → 调用 search → 验证返回结果格式
- [ ] 集成测试：加载测试插件 → 调用 getMediaSource → 验证返回 MediaSourceResult
- [ ] 集成测试：插件超时和异常处理测试（验证 30s 超时、JS 异常不 crash）

---

### 后续阶段（不在第一阶段范围内）

- 歌词显示
- 下载管理
- 播放历史
- WebDAV
- 备份恢复
- 推荐/排行榜
- 艺术家/专辑详情页
