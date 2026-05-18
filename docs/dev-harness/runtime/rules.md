# Runtime State Rules

> 文档状态：当前规范（Dev Harness — Runtime State）
> 适用范围：RuntimeStore、SnapshotStore、Activity 重建恢复、进程冷启动恢复、Route seed、跨页面高价值运行态。
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[RuntimeStore 运行时架构设计](../../superpowers/specs/2026-05-19-runtime-store-architecture-design.md)
> 最后校验：2026-05-19

## 强制入口

新增或修改以下内容前，必须先读取本文件：

- 跨页面状态、搜索结果、插件详情分页、播放会话、下载会话、插件加载状态。
- `@HiltViewModel` 中保存的高价值页面状态。
- Compose Screen 中通过 `remember` / `rememberSaveable` 保存的恢复相关状态。
- Route seed、导航参数、`SavedStateHandle` 恢复协议。
- Room / DataStore / file snapshot 形式的运行态缓存。

若同时涉及 UI、插件、播放器或测试，还必须读取对应 rules：

- [UI rules](../ui/rules.md)
- [Plugin rules](../plugin/rules.md)
- [Player rules](../player/rules.md)
- [Test rules](../test/rules.md)

## 状态分层 {#rule-runtime-state-classification}

每个新增或修改的状态 MUST 明确归入以下四类之一：

- **UI transient**：只影响当前 composition 的短暂 UI，例如弹窗、菜单、拖动中状态。
- **ViewModel local**：只属于当前 back stack entry，重建后可丢或可由 Store 重新派生。
- **RuntimeStore**：进程内常驻、跨 Activity 重建保留、通过 `StateFlow` 暴露。
- **SnapshotStore**：可序列化落盘快照，用于进程死亡后的冷启动恢复。

MUST NOT 把高价值运行态只放在 Compose 普通 `remember` 中。高价值运行态包括用户刚构建出来且恢复成本高的状态，例如搜索结果、详情页分页结果、播放会话、插件加载状态、下载队列、Route seed。

## RuntimeStore 边界 {#rule-runtime-store-boundary}

RuntimeStore MUST 是明确子 Store 组成的进程级门面，不得实现为无类型全局 Map。

当前标准子 Store：

- `PluginRuntimeStore`
- `PlaybackRuntimeStore`
- `DownloadRuntimeStore`
- `SearchSessionStore`
- `DetailSessionStore`
- `RouteSeedStore`
- `UiRuntimeStore`

RuntimeStore MUST：

- 由 Hilt 以进程级作用域提供，使用统一的 application scope。
- 通过 `StateFlow` / suspend action 暴露状态和修改入口。
- 记录关键状态恢复、持久化、失效、降级日志。
- 为异步加载提供 generation / stale result 丢弃策略，或取消上一轮任务。
- 冷启动恢复使用结构化并行，单个 Store 恢复失败不得取消其他 Store。

RuntimeStore MUST NOT：

- 持有 Activity、View、Composable lambda。
- 直接持久化 QuickJS、Media3、Coroutine、Repository、DAO 等运行对象。
- 把 unrelated feature 的状态塞进同一个 payload。
- 在 `Application.onCreate()` 或主线程同步等待大 payload 恢复。

## SnapshotStore 边界 {#rule-snapshot-store-boundary}

需要进程冷启动恢复的 RuntimeStore MUST 设计 SnapshotStore 协议。

每类 snapshot MUST 定义：

- `snapshotVersion`
- 稳定 `key`
- `createdAt` / `updatedAt`
- `sourceSignature` 或失效依据
- `payload`
- TTL / 容量 / 清理策略
- restore 失败后的降级行为

MUST NOT 用 DataStore 保存大 payload 或高频写入 payload。大 payload 应使用 Room 或文件，并遵守数据库迁移规范。

## 不可持久化对象 {#rule-runtime-objects-not-persisted}

以下对象 MUST NOT 直接进入 SnapshotStore：

- QuickJS `Context`、`JsEngine`、JS bridge runtime。
- `MediaController`、`ExoPlayer`、`MediaSession`、Notification 实例。
- Coroutine `Job`、`Scope`、Dispatcher、Mutex 等调度对象。
- Android `Context`、Activity、View。
- Repository、DAO、OkHttpClient、Room Database 实例。

必须通过可序列化 descriptor / snapshot 重建这些对象。

## 冷启动性能 {#rule-runtime-restore-nonblocking}

RuntimeStore / SnapshotStore 的冷启动恢复 MUST 非阻塞首屏。

MUST：

- 把恢复拆为轻量索引恢复、后台并行恢复、页面懒恢复三类。
- 使用 application scope + `SupervisorJob` / `supervisorScope` 并行恢复相互独立的 Store。
- 对大 payload restore 设计 timeout / cancellation / fallback。
- 记录 restore `durationMs`、`store`、`key`、`result`、失败 `reason`。

MUST NOT：

- 在 `Application.onCreate()` 使用 `runBlocking` 或同步等待搜索/详情大 payload。
- 在主线程读取大 snapshot 文件或反序列化大列表。
- 冷启动同步执行 QuickJS 插件完整评估来阻塞首页展示。

插件能力索引 MAY 先由元数据 snapshot 提供，再由后台真实加载结果修正。搜索和详情结果 SHOULD 在对应页面订阅时懒加载。

## Route Seed 恢复 {#rule-route-seed-recoverable}

Route seed 如果会影响插件请求、详情 header、分页或 raw 字段，MUST 可恢复。

MUST NOT 只依赖一次性 `ConcurrentHashMap + take()` 作为恢复协议。允许保留进程内快速路径，但必须满足：

- 支持重复 resolve 当前 route 需要的 seed。
- seed 可由 SnapshotStore、详情 snapshot 或完整 route 参数恢复。
- 有 TTL / prune 策略，避免无限增长。

## ViewModel 职责 {#rule-viewmodel-runtime-store-adapter}

高价值运行态迁移后，ViewModel MUST 作为 RuntimeStore 的页面适配层：

- 订阅 Store `StateFlow`。
- 转发用户 action。
- 保留页面 transient 状态。
- 不重复实现 snapshot restore / persist / invalidation。

新增 ViewModel 状态时，PR 描述或设计文档 MUST 说明为什么该状态不需要 RuntimeStore / SnapshotStore。

## Compose 状态恢复 {#rule-compose-state-restore}

Compose 普通 `remember` 只适合 UI transient。

- Activity 重建后仍应恢复的小状态 SHOULD 使用 `rememberSaveable`。
- 跨页面或跨冷启动有价值的状态 MUST 进入 RuntimeStore / SnapshotStore。
- MUST NOT 为了“恢复方便”把弹窗、菜单、拖动中状态全局化；这类状态默认丢弃。

## 测试要求 {#rule-runtime-state-tests}

新增或迁移 RuntimeStore / SnapshotStore 时 MUST 补测试：

- Store JVM 单测：mutation、restore、persist、TTL、容量、失效。
- ViewModel 单测：ViewModel 不重复实现恢复逻辑，只订阅 Store / 转发 action。
- Activity recreate 验收：关键页面经过 `ActivityScenario.recreate()` 不回空白。
- 冷启动恢复验收：能从 snapshot 恢复；snapshot 失效时能降级刷新。

若修改 `*Test.kt` 或测试基建，必须遵守 [Test rules](../test/rules.md)。

## 日志要求 {#rule-runtime-state-logging}

RuntimeStore / SnapshotStore 的恢复和持久化 MUST 记录结构化日志：

- `runtime_restore_start`
- `runtime_restore_success`
- `runtime_restore_failed`
- `runtime_restore_skipped`
- `runtime_snapshot_persist_start`
- `runtime_snapshot_persist_success`
- `runtime_snapshot_persist_failed`

具体 feature 可以使用更具体事件名，但字段必须包含 `store`、`operation`、`key`、`result`，失败时包含 `reason`。
