# AGENTS.md

面向本仓库 AI 编码助手的项目工作指引。

## 项目概览

MusicFreeAndroid 是 [MusicFree](https://github.com/maotoumao/MusicFree) 的 Android 原生重写版本（原版为 React Native 音乐播放器）。项目核心目标是在 Kotlin + Jetpack Compose + QuickJS 下复刻原版插件化能力与主要交互体验。

原版 RN 侧源码固定参考当前仓库同级目录：`../MusicFree`。

## 文档入口（先读）

在动手实现前，按以下顺序读取文档：

1. `docs/DOCS_STATUS.md`（文档状态索引：当前规范 / 当前参考 / 历史记录）
2. `AGENTS.md`（当前仓库工作约束）
3. 与任务相关的“当前规范”文档

强制规则：

- `docs/superpowers/plans/*.md` 默认视为历史执行快照，不可直接当作当前执行指令。
- 文档之间的引用必须使用相对路径，禁止使用 `/Users/...` 绝对路径。
- 跨仓库引用也必须使用相对路径（例如 `../MusicFree/...`）。

## Git Worktree 开发约束

- 默认使用 `git worktree` 进行功能开发，避免在主工作区直接切换或堆叠功能分支。
- worktree 默认创建在仓库根目录的 `.worktrees/` 下，路径格式为 `.worktrees/<branch-name>`。
- 创建本地 worktree 前必须确认 `.worktrees/` 已被忽略，避免 worktree 内容进入版本控制。
- 若用户未指定分支名，使用与任务语义一致的简短分支名。
- 文档、脚本和说明中引用 worktree 路径时使用相对路径，避免写入 `/Users/...` 绝对路径。

## 构建命令

```bash
./gradlew assembleDebug              # 构建 Debug APK
./gradlew assembleRelease            # 构建 Release APK
./gradlew :app:build                 # 仅构建 app 模块
./gradlew :<module>:assembleDebug    # 构建指定模块
./gradlew test                       # 运行单元测试
./gradlew :app:testDebugUnitTest     # 运行 app 模块单元测试
./gradlew connectedAndroidTest       # 运行仪器测试（需设备/模拟器）
./gradlew lint                       # 运行 lint
```

## 当前构建基线（已校验）

- Min SDK：29（Android 10）
- Target SDK：36
- compileSdk：36.1
- Java compatibility：`VERSION_17`
- JVM toolchain：JDK 25
- Gradle Wrapper：`9.4.1`
- AGP：`9.2.0`
- Kotlin：`2.3.21`
- Compose BOM：`2026.04.01`

## 模块架构

仓库采用多模块结构，依赖方向保持单向：

```text
:app → :feature:* → :data, :player, :plugin → :core
```

| 模块 | 职责 |
|---|---|
| `:core` | 主题、导航路由、基础模型、通用工具 |
| `:data` | Room、DataStore、Repository |
| `:player` | Media3/ExoPlayer、MediaSessionService、PlayerController |
| `:plugin` | QuickJS 引擎、JS 桥接、插件管理 |
| `:feature:home` | 首页、本地音乐、歌单、榜单、详情链路 |
| `:feature:player-ui` | 全屏播放器、迷你播放器 |
| `:feature:search` | 插件驱动搜索 |
| `:feature:settings` | 设置、插件管理、文件选择器 |
| `:app` | 应用入口、NavHost、跨模块编排 |

## 技术栈

- UI：Jetpack Compose + Material3
- 架构：MVVM + 多模块 + 单向数据流
- DI：Hilt
- 播放：Media3（ExoPlayer）+ `MediaSessionService`
- 插件引擎：QuickJS（`quickjs-kt`）
- 数据库：Room
- 偏好存储：DataStore
- 异步：Kotlin Coroutines + Flow
- 导航：Navigation Compose + `@Serializable` 路由对象
- 图片加载：Coil
- 网络：OkHttp

## 核心设计约束

### 主题系统

在 Material3 基础上扩展语义色（`MusicFreeColors` + `CompositionLocal`），并支持亮色/暗色模式。原版主题可参考 `../MusicFree/src/core/theme.ts`。

### 响应式尺寸

使用 `rpx(value)` 统一适配，公式与 RN 保持一致：`(value / 750f) * minWindowEdge`。

### 插件系统

插件运行于 QuickJS 沙箱，`JsBridge` 负责 Kotlin ↔ JS 互通。`require()` shim 支持 `cheerio`、`crypto-js`、`dayjs`、`axios`、`qs`、`he`、`big-integer`。

`PluginApi` 当前包含 14 个核心能力方法（如 `search()`、`getMediaSource()`、`getLyric()`、`getMusicSheetInfo()`、`getRecommendSheetsByTag()` 等）。

### 播放架构

`MediaSessionService` 封装 ExoPlayer，`PlayerController` 作为 `MediaController` 包装层由 Hilt 暴露。UI 侧通过 `StateFlow<PlayerState>` 观察播放状态，队列由 `PlayQueue` 管理。

### 数据层

Room Entity 不直接暴露给上层；通过 mapper 转换为 domain model。Repository 对外统一返回 `Flow<T>`。DataStore 负责标量偏好。

## 当前实现状态（2026-04-11）

- 当前导航与页面路由覆盖约为 `17/19`（对齐 RN `src/pages` 目录口径）。
- 与 RN 对比仍缺页面：`downloading`、`setCustomTheme`。
- `fileSelector` 已落地（旧文档中“缺 fileSelector”的描述已失效）。
- 首页 fidelity 已有专项设计与证据包（见 `docs/home-fidelity/homepage/` 与相关 spec）。

为避免进度信息过期，后续请优先以以下代码事实为准：

- 路由定义：`core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- 实际挂载：`app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

## 当前优先事项（长期有效）

1. 补齐缺失页面：`downloading`、`setCustomTheme`
2. 强化插件详情链路运行态验收：`topListDetail / pluginSheetDetail / musicDetail`
3. 加强端到端链路验证：插件安装 → 搜索 → 播放 → 队列/状态一致性
4. 持续进行文档治理：剔除或标记过时规则，保持规范与仓库状态一致

## 原版 RN 参考路径

实现功能时优先对照本地 RN 侧源码目录 `../MusicFree`：

- 类型定义：`../MusicFree/src/types/`（`music.d.ts`、`plugin.d.ts`、`artist.d.ts`）
- 插件系统：`../MusicFree/src/core/pluginManager/`
- 播放器逻辑：`../MusicFree/src/core/trackPlayer/`
- 主题：`../MusicFree/src/core/theme.ts`
- UI 常量：`../MusicFree/src/constants/uiConst.ts`
- 响应式尺寸：`../MusicFree/src/utils/rpx.ts`

## 迭代工作流

1. 先读 RN 与当前 Android 代码，明确差异和依赖
2. 将任务拆分为可执行、可验证的小步
3. UI 不只看截图，要结合 `uiautomator dump` 与代码结构比对
4. 数据结构、数据库字段、关键请求参数优先对齐 RN
5. 每个功能都补齐单元测试与必要集成测试
6. 先做运行态验收，再给出完成结论
7. 将过程中的错误和修正沉淀到文档

## 分析规则

- 不仅依赖截图，必须同时分析 RN 源码与 Android 源码
- 截图是视觉锚点，不是唯一事实来源
- 要主动识别：缺失抽象、过时文档、伪 backlog、隐藏前置依赖

## 验收闸门

- 编译、单测、集成测试、模拟器/设备验证、最终 review 必须集中执行
- 运行态验收优先于“代码看起来没问题”的乐观判断
- 功能可能通过编译和测试，但仍会在运行态失败；结论必须以运行证据为准

## 文档维护

- 定期复审 `AGENTS.md` 与 `docs/`，及时标记历史文档
- 新增文档时必须写明文档状态与适用范围
- 更新文档引用时必须使用相对路径，避免环境迁移后失效
