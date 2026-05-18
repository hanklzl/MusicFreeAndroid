# RuntimeStore 运行时架构设计

> 文档状态：当前规范（运行时状态架构）
> 适用范围：前后台切换、Activity 重建、进程冷启动后的状态恢复；播放、插件、搜索、详情、Route seed 与跨页面运行态。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [Dev Harness Runtime Rules](../../dev-harness/runtime/rules.md)
> 最后校验：2026-05-19

## 背景

当前 App 已经有一批进程级对象，例如 `PlayerController`、`PluginManager`、`Downloader`、Repository、ThemeRepository 和启动 coordinator。它们能覆盖部分前后台恢复，但缺少一套统一的“运行时状态边界”：

- Compose 的普通 `remember` 只适合当前 composition，Activity 重建后会丢失。
- NavBackStackEntry 级 ViewModel 可以扛住常见 Activity 重建，但进程死亡后只剩 route / `SavedStateHandle` / 落盘数据。
- `@Singleton` 对象能在进程内常驻，但目前每个对象自行决定缓存、加载、恢复和清理策略。
- 一次性 SeedStore 用 `ConcurrentHashMap + take()` 传递复杂路由对象，不能作为可靠恢复协议。

目标不是把所有对象都持久化，而是把“用户当前上下文”抽成可恢复快照。不可序列化的运行对象只根据快照重建。

## 结论

采用分层方案：

```text
ViewModel / Compose
        ↓
RuntimeStore（进程内状态门面，StateFlow 可观测）
        ↓
SnapshotStore（可选落盘快照，支持冷启动恢复）
        ↓
Room / DataStore / files / plugin files
```

核心规则：

- RuntimeStore 保存“可以描述用户当前上下文的状态”，不保存正在运行的系统对象。
- SnapshotStore 保存可序列化、可校验、可失效的快照，不保存 QuickJS、Media3、Coroutine 等实例。
- ViewModel 负责页面适配和事件转发，不再独占高价值运行态。
- Compose `remember` 只保存短生命周期 UI transient；跨 Activity 重建有价值的状态必须进入 RuntimeStore 或 `rememberSaveable`。

## 必修范围

### 1. RuntimeStore 基座

新增统一基座，承载进程内常驻状态和落盘快照协议。它不是一个无类型全局 Map，而是由明确子 Store 组成的门面。

必要性：

- 避免每个页面各自实现缓存、恢复、失效和日志。
- 避免业务后续继续把高价值状态塞进 ViewModel 或 Compose `remember`。
- 统一 ApplicationScope、初始化顺序、清理策略、测试方式。

### 2. PluginRuntimeStore

保存内容：

- 插件条目、加载状态、失败状态、能力索引。
- 安装、更新、订阅同步等操作态。
- 插件元数据快照、失败详情、更新时间、来源信息。

不保存内容：

- QuickJS `Context`、JS engine 实例、协程 job。

必要性：

- 插件能力是全 App 基础能力，不应跟页面初始化节奏绑定。
- 多个 ViewModel 重复调用 `ensurePluginsLoaded()` 会让“插件暂时为空 / 重复加载 / 页面误判无插件”问题复发。

### 3. PlaybackRuntimeStore

保存内容：

- 当前播放项、队列、当前索引、进度、duration、播放模式、随机、速度、当前音质。
- 恢复状态，例如 cold restore 是否已完成、是否存在 pending restore position。

不保存内容：

- `MediaController`、`ExoPlayer`、`MediaSession`、通知对象、音频焦点对象。

必要性：

- 播放是核心状态，前后台、Activity 重建、冷启动都必须有连续体验。
- 当前队列和进度已有落盘基础，后续需要把 UI 派生状态和恢复语义收敛到同一运行态边界。

### 4. SearchSessionStore

保存内容：

- query、媒体类型 tab、选中平台。
- 每个平台搜索结果、分页页码、isEnd、loading/error 状态。
- 会话更新时间、插件版本/能力签名，用于失效。

必要性：

- 搜索结果是用户刚构建出来的上下文，恢复后不应回空白编辑态。
- 插件搜索成本高且可能失败，重复请求会放大网络和插件不稳定。

持久化策略：

- 进程内常驻。
- SnapshotStore 落盘最近若干会话，带 TTL 和容量限制。
- 快照命中但插件签名不匹配时必须丢弃或降级为“可刷新旧结果”。

### 5. DetailSessionStore

保存内容：

- 详情 route key：类型、platform、id、route 参数。
- header seed：title、artwork、description、raw 等。
- 已加载 musicList、page、isEnd、loading/error 状态。
- 详情类型覆盖歌单、榜单、专辑、歌手。

必要性：

- 插件详情页分页成本高，重建后回到第一页会明显打断体验。
- seed raw 字段会影响插件请求；只保留轻量 route fallback 不够可靠。

持久化策略：

- 按 route key 缓存。
- 设置 TTL 和容量上限。
- 插件不存在、插件版本变化或 schema 版本变化时失效。

### 6. RouteSeedStore

保存内容：

- 用于跨页面导航的完整 seed，包括 raw、cover、description、worksNum 等插件原始字段。
- seed 与目标 route key 的绑定关系。
- 失效时间和消费状态。

必要性：

- 当前一次性 `take()` 不是恢复协议，只适合进程内短跳转。
- Activity 重建或进程恢复后丢 seed 会导致详情页只能用降级 fallback，插件调用可能缺关键字段。

策略：

- `take()` 语义必须改为“可重复 resolve + 显式 prune”。
- 对会影响插件请求的 seed，必须能从 SnapshotStore 或详情快照恢复。

### 相邻必修：DownloadRuntimeStore

保存内容：

- 下载任务列表、下载中/失败状态、进度、已下载 key。
- 下载配置快照，例如并发数、默认下载音质、移动网络下载开关。
- foreground service 通知只根据任务状态重建，不作为持久化对象。

不保存内容：

- Service 实例、Notification 实例、下载协程 job、OkHttp call。

必要性：

- 下载任务是用户明确发起的长任务，必须跨 Activity 重建和进程冷启动恢复。
- 当前下载任务已有 Room 表与进程级 engine，后续改动必须继续遵守 RuntimeStore / SnapshotStore 边界，避免把下载状态重新散落到页面 ViewModel。

## UiRuntimeStore 边界

UiRuntimeStore 只保存少量跨重建有价值的 UI 状态，例如：

- 播放器当前显示封面页还是歌词页。
- 首页当前 tab。
- 搜索页当前 tab。

以下状态默认不进入 RuntimeStore：

- 弹窗是否展开、菜单是否展开、拖动中状态、临时输入草稿。
- 这些状态如确实需要 Activity 重建恢复，优先用 `rememberSaveable` 或 ViewModel 局部状态。

## 不可持久化清单

以下对象禁止直接进入 SnapshotStore：

- QuickJS `Context`、`JsEngine`、JS bridge runtime。
- `MediaController`、`ExoPlayer`、`MediaSession`、Notification 实例。
- Coroutine `Job`、`Scope`、Dispatcher、Mutex 等调度对象。
- Android `Context`、Activity、View、Composable lambda。
- Repository、DAO、OkHttpClient、Room Database 实例。

## SnapshotStore 协议

每个落盘快照必须定义：

- `snapshotVersion`：结构版本，变更时有迁移或失效策略。
- `key`：可稳定定位会话，例如 `search:<queryHash>` 或 `detail:<type>:<platform>:<id>`。
- `createdAt` / `updatedAt`：用于 TTL 和 LRU 清理。
- `sourceSignature`：插件版本、能力签名、appVersion 或数据版本。
- `payload`：仅包含可序列化状态。
- `restorePolicy`：命中、失效、部分恢复、强制刷新时的行为。

默认限制：

- 搜索和详情快照必须设置 TTL。
- 搜索和详情快照必须设置容量上限。
- 大 payload 不进 DataStore；优先 Room 或文件。
- Room schema 变更必须遵守数据库迁移规范。

## 初始化顺序

冷启动建议顺序：

1. Application 初始化日志、DataStore、Room。
2. RuntimeStore 基座启动，加载轻量 snapshot 索引。
3. PluginRuntimeStore 恢复插件元数据并按需重建 JS runtime。
4. PlaybackRuntimeStore 恢复队列、索引、进度。
5. SearchSessionStore / DetailSessionStore 懒恢复，页面订阅时再加载 payload。
6. RouteSeedStore 恢复当前 back stack 可能需要的 seed。

## 启动性能与并行恢复

冷启动恢复必须避免阻塞首屏。RuntimeStore 的启动分为三类：

- **必须早期完成的轻量恢复**：日志配置、轻量 snapshot 索引、播放队列索引/当前项/进度。这类任务可以在 application scope 中启动，但不得在主线程做文件或数据库重 IO。
- **后台并行恢复**：插件元数据、插件失败状态、下载任务、播放派生状态。使用 `SupervisorJob` 隔离失败，互不阻塞。
- **懒恢复**：搜索结果大 payload、详情页分页结果、Route seed payload。页面订阅对应 Store 时再加载；命中 snapshot 前 UI 可以显示轻量 header 或恢复中状态。

允许并推荐使用协程并行：

```kotlin
applicationScope.launch {
    supervisorScope {
        launch { pluginRuntimeStore.restoreMetadata() }
        launch { playbackRuntimeStore.restoreQueueSnapshot() }
        launch { downloadRuntimeStore.restoreTaskIndex() }
        launch { routeSeedStore.restoreActiveSeedsIndex() }
    }
}
```

约束：

- 启动链路不得使用 `runBlocking` 等方式等待大 payload 恢复。
- 不得在 `Application.onCreate()` 同步执行 QuickJS 插件完整评估、搜索结果反序列化、详情列表大 payload 读取。
- 插件 JS runtime 重建必须按需或后台执行；插件能力索引可先由元数据快照提供，再由真实加载结果修正。
- 大 payload restore 必须可取消、可超时、可降级刷新。
- 并行恢复任务必须记录 `durationMs` 和失败降级原因。

验收时需要关注：

- 首屏 Activity 创建不因搜索/详情 snapshot 变慢。
- 插件/搜索/详情恢复失败不会阻塞播放队列恢复。
- 恢复任务失败只影响对应 Store，不取消整个 RuntimeStore。

## ViewModel 接入原则

- ViewModel 不再把高价值运行态作为唯一事实源。
- ViewModel 可以保留页面 transient，例如当前 dialog target。
- ViewModel 通过 Store action 修改状态，通过 `StateFlow` 订阅状态。
- ViewModel 发起插件、网络、播放、下载操作时，必须让 Store 记录 loading/error/stale 状态。
- 异步加载仍必须遵守 generation / stale result 丢弃规则。

## 日志与诊断

新增或迁移 Store 时必须记录结构化日志：

- snapshot restore start / success / failed / skipped / stale。
- snapshot persist start / success / failed。
- runtime state mutation 中关键用户操作。
- restore 失败后降级路径和原因。

日志事件使用小写 snake_case，字段使用稳定 key。

## 测试策略

每个子 Store 至少补齐：

- JVM 单测：状态变更、snapshot 序列化、TTL、容量、失效策略。
- ViewModel 单测：页面只转发 action / 订阅 Store，不重复实现业务恢复。
- Activity recreate 验收：`ActivityScenario.recreate()` 后关键 UI 状态不回空白。
- 冷启动恢复验收：能从持久快照恢复关键状态，失效时能降级刷新。

若改动涉及播放器、插件、测试基建，还必须同时读取对应 Dev Harness rules。

## 分期建议

第一期：

- 建 RuntimeStore / SnapshotStore 基础接口和规则。
- 接入 PlaybackRuntimeStore、PluginRuntimeStore、DownloadRuntimeStore、RouteSeedStore。
- 为搜索和详情定义 snapshot schema，但先选一个页面试点。

第二期：

- 迁移 SearchSessionStore。
- 迁移 PluginSheet / TopList / Album / Artist 详情会话。
- 补 Activity recreate 与冷启动恢复验收。

第三期：

- 收敛 UI transient，补 `rememberSaveable` 或移除不必要恢复。
- 增加 SnapshotStore 清理任务和诊断界面。
