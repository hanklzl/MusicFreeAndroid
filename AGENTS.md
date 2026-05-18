# AGENTS.md

面向本仓库 AI 编码助手的项目工作指引。

## 项目概览

MusicFreeAndroid 是 [MusicFree](https://github.com/maotoumao/MusicFree) 的 Android 原生重写版本（原版为 React Native 音乐播放器）。项目核心目标是在 Kotlin + Jetpack Compose + QuickJS 下复刻原版插件化能力与主要交互体验。

原版 RN 侧源码固定参考当前仓库同级目录：`../MusicFree`。

## 文档入口（先读）

在动手实现前，按以下顺序读取文档：

1. `docs/DOCS_STATUS.md`（文档状态索引：当前规范 / 当前参考 / 历史记录）
2. `AGENTS.md`（当前仓库工作约束）
3. `docs/dev-harness/INDEX.md`（开发守门总入口；按域跳到对应 rules.md / incidents.md）
4. 与任务相关的“当前规范”文档

强制规则：

- `docs/superpowers/plans/*.md` 默认视为历史执行快照，不可直接当作当前执行指令。
- 文档之间的引用必须使用相对路径，禁止使用 `/Users/...` 绝对路径。
- 跨仓库引用也必须使用相对路径（例如 `../MusicFree/...`）。

## Dev Harness 强制入口

任何涉及下述域的改动，动手前必须读取对应 rules.md：

- UI / Compose Screen：`docs/dev-harness/ui/rules.md`
- 插件系统：`docs/dev-harness/plugin/rules.md`
- 播放器 / Media3：`docs/dev-harness/player/rules.md`
- 测试代码 / 测试基建：`docs/dev-harness/test/rules.md`
- Runtime State / 持久化恢复：`docs/dev-harness/runtime/rules.md`

每条 rule 都关联一条或多条 incident（`docs/dev-harness/incidents/index.md`）和 / 或一条 contract test。
违反 rules.md 中标记 MUST / MUST NOT 的条款由人工 review 拦截；本地可跑 `bash scripts/dev-harness/check.sh` 自查。

历史决策快照在 `docs/superpowers/specs/` 与 `plans/`（仅参考），不是当前规则源。

## 项目记忆与守门约束

- 强约束：`docs/dev-harness/<area>/rules.md`
- 历史踩坑：`docs/dev-harness/incidents/index.md`（按 ID 反查到 area + rule + guard）
- AI 工作流：见 `.agents/skills/<area>-skill/`，软链到 `.claude/skills/`、`.codex/skills/`
- 历史决策快照：`docs/superpowers/specs/` 与 `plans/`（仅参考，不是当前规则源）
- 个人会话偏好（Claude Code only）：`~/.claude/projects/.../memory/MEMORY.md`，不进仓库

## Parity Audit Skill 入口

跨工具调用的 RN/Android 对齐扫描 sub-agent，触发短语见 `.agents/skills/parity-audit-skill/SKILL.md`。状态文件 `docs/parity-audit/state.json` + `queue.md` 为唯一可信源。`mode=dry-run` 不会创建 Issue；`mode=audit` 走 `gh issue create` + 指纹查重 + release 截图上传。设计 spec：`docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`。

## Git Worktree 开发约束

- 默认使用 `git worktree` 进行功能开发，避免在主工作区直接切换或堆叠功能分支。
- worktree 默认创建在仓库根目录的 `.worktrees/` 下，路径格式为 `.worktrees/<branch-name>`。
- 创建本地 worktree 前必须确认 `.worktrees/` 已被忽略，避免 worktree 内容进入版本控制。
- 若用户未指定分支名，使用与任务语义一致的简短分支名。
- 文档、脚本和说明中引用 worktree 路径时使用相对路径，避免写入 `/Users/...` 绝对路径。
- worktree 分支合并回 `main` 时必须使用 `git merge --squash`，把分支上所有 commit 压成单个 commit。Commit message 使用 conventional commits 格式（`feat(scope): ...`、`fix(scope): ...`、`docs(scope): ...` 等）简要说明变更类型与范围；正文可补一两句"做了什么、为什么"，不要把每一步过程写进 message, 提交使用中文。

## 构建命令

```bash
./gradlew assembleDebug              # 构建 Debug APK
./gradlew assembleRelease            # 构建 Release APK
./gradlew :app:assembleDebug         # 仅构建 app Debug 变体
./gradlew :<module>:assembleDebug    # 构建指定模块
./gradlew test                       # 运行单元测试
./gradlew :app:testDebugUnitTest     # 运行 app 模块单元测试
./gradlew connectedAndroidTest       # 运行仪器测试（需设备/模拟器）
./gradlew lint                       # 运行 lint
```

本地功能收尾默认验证 Debug 构建，不要求验证 Release 构建。Release 构建只在签名环境变量齐备或任务明确涉及发布/签名时验证。

## 当前构建基线（已校验）

- Min SDK：29（Android 10）
- Target SDK：36
- compileSdk：36.1
- Java compatibility：`VERSION_17`
- JVM toolchain：JDK 21
- Gradle Wrapper：`9.4.1`
- AGP：`9.2.0`
- Kotlin：`2.3.21`
- Compose BOM：`2026.04.01`

## 模块架构

仓库采用多模块结构，依赖方向保持单向：

```text
:app → :feature:* → :data, :player, :plugin → :core
```

| 模块                 | 职责                                                    |
| -------------------- | ------------------------------------------------------- |
| `:core`              | 主题、导航路由、基础模型、通用工具                      |
| `:data`              | Room、DataStore、Repository                             |
| `:player`            | Media3/ExoPlayer、MediaSessionService、PlayerController |
| `:plugin`            | QuickJS 引擎、JS 桥接、插件管理                         |
| `:feature:home`      | 首页、本地音乐、歌单、榜单、详情链路                    |
| `:feature:player-ui` | 全屏播放器、迷你播放器                                  |
| `:feature:search`    | 插件驱动搜索                                            |
| `:feature:settings`  | 设置、插件管理、文件选择器                              |
| `:app`               | 应用入口、NavHost、跨模块编排                           |

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

### UI Harness Rules

新增或修改 Compose Screen 前，必须读取并遵守 [docs/dev-harness/ui/rules.md](docs/dev-harness/ui/rules.md)。

- Screen 切换动画、普通 AppBar、沉浸式状态栏处理必须走公共 harness 入口。
- 普通 AppBar 页面不得直接手写分散的 `TopAppBar` + `TopAppBarDefaults.topAppBarColors(...)`。
- 特殊 Chrome 页面必须在规则文档中登记，并自行负责状态栏背景和顶部 inset。
- `docs/superpowers/plans/*.md` 中旧动画或 AppBar 写法不作为当前 UI Harness 规范来源。
- 旧入口 `docs/ui-harness/screen-chrome-rules.md` 已迁移；保留只读 redirect stub 以兼容历史引用。

### Runtime State Harness Rules

新增或修改跨页面运行态、Activity 重建恢复、进程冷启动恢复、Route seed、搜索结果缓存、插件详情分页、播放/下载/插件运行状态前，必须读取并遵守 [docs/dev-harness/runtime/rules.md](docs/dev-harness/runtime/rules.md)。

- 高价值运行态必须明确归类为 UI transient、ViewModel local、RuntimeStore 或 SnapshotStore。
- RuntimeStore 保存可描述用户上下文的进程级状态，SnapshotStore 保存可序列化落盘快照；不得持久化 QuickJS、Media3、Coroutine、Android Context、Repository/DAO 等运行对象。
- ViewModel 作为 RuntimeStore 的页面适配层，负责订阅状态与转发 action，不应独占搜索结果、详情分页、播放会话、插件加载状态等高价值运行态。
- Route seed 若影响插件请求、详情 header、分页或 raw 字段，必须可恢复；不得只依赖一次性 `ConcurrentHashMap + take()`。
- 下载、播放、插件等长生命周期运行对象只能根据可序列化快照重建，不得直接持久化 Service、Media3、QuickJS、Coroutine job 等实例。
- Compose 普通 `remember` 只适合短暂 UI 状态；跨 Activity 重建或冷启动有价值的状态必须使用 RuntimeStore / SnapshotStore，或在小型 UI 状态场景下使用 `rememberSaveable`。

### R8 与反射保留规则

Release 构建启用 R8 和资源压缩；新增或修改会被运行时按类名、成员名或序列化类型名解析的类型时，必须审查是否需要 `@Keep` 或等价 ProGuard 规则，并补充 release 运行态验收。

需要保留 class 名称的典型场景：

- 类型的全限定类名会在运行时作为字符串被解析，例如 `Class.forName(...)`、框架反射、外部协议或持久化数据引用。
- Navigation Compose typed route 的 enum 参数、custom `NavType` 参数，或其他依赖默认全限定 `serialName` 查找类型的导航参数。
- `kotlinx.serialization` 多态、默认 `serialName`、跨版本持久化 payload 或外部接口把类名当作稳定协议的一部分。
- Android framework、第三方 SDK、JS bridge 或插件桥按约定反射调用的类、构造函数、方法或字段；若只需要保留成员名，优先使用精确 `-keepclassmembers`，不要扩大到整包。

不应为了省事 blanket keep 整个模块。普通 `@Serializable` route/data class 如果只通过生成 serializer 读写字段，且类名不是运行时协议的一部分，通常不需要 `@Keep`。一旦新增此类保留规则，必须至少验证 `:app:assembleRelease`，并在可用设备/模拟器上安装 release 包冷启动检查 `AndroidRuntime` 崩溃日志。

### 日志记录规范

项目使用 `:logging` 模块和 `MfLogger` / `MfLog` 记录结构化日志，底层由美团 Logan 持久化。

- 新功能或 bugfix 涉及启动、插件、网络、播放、数据写入、导入导出、跨模块状态变化时，必须补结构化日志。
- 业务代码使用 `MfLogger` / `MfLog`，禁止新增直接 `android.util.Log.*` 和直接 Logan 调用；日志底层模块内部兜底除外。
- 日志事件命名使用稳定小写 snake_case，例如 `plugin_install_failed`。
- catch 后如果吞掉异常、降级返回或转成用户 toast，必须记录 `error`。
- 耗时操作必须记录 `durationMs`，可使用 `:logging` 模块提供的 timing helper。
- 日志字段使用稳定 key，避免把临时 UI 文案作为机器可读字段。
- 日志默认开启，第一阶段暂不做脱敏，也不提供详细日志手动开关；导出前必须提示用户日志可能包含搜索词、请求地址、插件返回内容和设备信息。
- 默认保留最近 7 天日志，并通过日志包分享能力提供用户反馈材料。

### 数据库迁移规范

项目已正式发布，Room 数据库 schema 变更必须考虑老版本升级路径。

- 禁止使用 `fallbackToDestructiveMigration()` 静默清空老用户数据。
- 新增 / 修改 / 删除 `@Entity` 字段、新增表、新增索引时，必须：
  - 提升 `@Database(version = N)`
  - 提供对应的 `Migration(N-1, N)`，覆盖每一项 schema diff
  - 在 `:data` 模块加 `MigrationTestHelper` 单测验证 N-1 → N 在真实 sqlite 上成功
- 新增表如仅 N 之后使用，迁移里写 `CREATE TABLE IF NOT EXISTS ...`，老用户升级时表为空、由功能首次写入填充。
- 大量行级 backfill 迁移必须在后台线程执行并打 timing 日志，避免阻塞 main thread。
- DataStore key 同样适用：默认值要保证老用户首次读到时不崩、不丢配置。

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

- 路由定义：`core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`
- 实际挂载：`app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`

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
- 默认构建闸门使用 Debug 构建（如 `:app:assembleDebug`）；不要因缺少 Release 签名环境变量而阻塞普通功能收尾。
- 运行态验收优先于“代码看起来没问题”的乐观判断
- 功能可能通过编译和测试，但仍会在运行态失败；结论必须以运行证据为准

## 文档维护

- 定期复审 `AGENTS.md` 与 `docs/`，及时标记历史文档
- 新增文档时必须写明文档状态与适用范围
- 更新文档引用时必须使用相对路径，避免环境迁移后失效
- 发布流程详见根目录 `RELEASE.md`；设计 spec 见 `docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md`。
