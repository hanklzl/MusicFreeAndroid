# 启动耗时分段与结构化日志设计

> 文档状态：当前规范
> 适用范围：应用启动分段耗时、冷启动 / Activity 重建启动分类、启动后台流程耗时日志。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 相关规范：[日志系统设计](./2026-05-05-logging-system-design.md)、[日志诊断覆盖设计](./2026-05-10-logging-diagnostics-coverage-design.md)、[Startup Rules](../../dev-harness/startup/rules.md)、[Runtime State Rules](../../dev-harness/runtime/rules.md)、[Test Rules](../../dev-harness/test/rules.md)
> 最后校验：2026-05-19

## 1. 背景

当前 Android 启动链路已有 Logan / `MfLog` 底座和部分启动事件：

- `LoggingInitializer.initialize()` 初始化 Logan 并记录 `app_start`。
- `MainActivity.onCreate()` 记录 `main_activity_create_start`、`edge_to_edge_enabled`。
- `RuntimeRestoreCoordinator` 对每个 `RuntimeStore` 记录 `runtime_restore_*` 耗时。
- 插件自动更新、播放恢复、更新检查等后台流程各自有零散日志。

缺口在于：日志包里无法直接回答“本次启动总共花了多久、慢在哪个阶段、这是冷启动还是 Activity 重建”。特别是 Activity 被销毁但 Application 仍在同一进程时，现有事件会再次出现 `MainActivity.onCreate()`，但不能和真正进程冷启动区分。

## 2. 目标

1. 将启动拆成稳定的可诊断阶段，并为每个阶段记录结构化耗时。
2. 同时记录到 `MainActivity.setContent` 注册完成和首帧绘制完成两个首屏口径。
3. 区分进程冷启动、同进程 Activity 重建启动、Activity resume 复前台。
4. 记录后台启动流程的独立耗时，但不把后台网络 / 插件任务算进首屏总耗时。
5. 保持现有启动行为不变，不因为埋点阻塞首屏或改变恢复顺序。

## 3. 非目标

1. 不引入新的性能分析 SDK 或远程上报平台。
2. 不重排启动任务执行顺序。
3. 不把所有后台任务完成时间定义为“首屏启动耗时”。
4. 不新增 UI 页面展示启动耗时。
5. 不记录每次 Compose recomposition、列表加载或高频 frame 事件。

## 4. 方案选择

### 4.1 推荐方案：轻量 `StartupTelemetry` 聚合

在 `:app` 增加一个进程级 `StartupTelemetry`，统一生成启动 ID、启动类型、阶段耗时和字段结构。`Application` 与 `MainActivity` 调用它记录关键节点；后台 coordinator 保留各自业务日志，只补齐统一的启动 span start / terminal 事件。

优点：

- 改动集中，事件字段一致。
- 可以覆盖 `attachBaseContext()` 这种日志初始化前的阶段。
- 不改变 `RuntimeRestoreCoordinator`、插件、播放、更新等现有执行语义。
- 后续 Logan 反馈包能按 `processStartId` / `activityStartId` 串联一次启动。

### 4.2 备选方案：各文件直接手写计时

直接在 `MusicFreeApplication`、`MainActivity`、各 coordinator 中手写 `System.nanoTime()` 和 `MfLog`。

优点是接入快；缺点是字段容易散，冷 / 暖分类容易重复实现，后续新增阶段时难以保持一致。

### 4.3 备选方案：引入重型启动性能框架

引入系统级 tracing 或性能 SDK 可以获得更完整的底层视角，但当前目标是用户反馈日志包诊断启动耗时。重型方案会扩大依赖和验收面，不适合作为本次落地。

## 5. 启动类型定义

启动类型固定为三类：

| startupType | 定义 | 是否参与首屏启动耗时 |
|---|---|---|
| `cold_process_start` | 新进程创建，`Application.attachBaseContext()` 和 `Application.onCreate()` 都发生 | 是 |
| `warm_activity_recreate` | `Application` 仍在同一进程，新的 `MainActivity` 实例进入 `onCreate()` | 是 |
| `activity_resume_existing` | Activity 实例未销毁，只是从后台回到前台 | 不参与启动总耗时，仅记录 resume |

`StartupTelemetry` 在进程内维护：

- `processStartId`：进程启动 ID，Application 创建时生成一次。
- `applicationStartNanoTime`：以 `attachBaseContext()` 为起点。
- `activityLaunchIndex`：同一进程内每次 `MainActivity.onCreate()` 递增。
- `activityStartId`：每次 `MainActivity.onCreate()` 生成一个新的 ID。

判断规则：

- `activityLaunchIndex == 1` 且本进程走过 `Application.onCreate()`：`cold_process_start`。
- `activityLaunchIndex > 1` 且 `processStartId` 不变：`warm_activity_recreate`。
- 仅触发 `onStart()` / `onResume()`，没有新的 `onCreate()`：`activity_resume_existing`。

## 6. 阶段与事件

### 6.1 应用进程阶段

| 事件 | category | 触发点 | durationMs 口径 |
|---|---|---|---|
| `app_startup_process_start` | `app` | `Application.attachBaseContext()` 后 | 无 |
| `app_startup_pending_restore_complete` | `app` | `StartupBackupRestore.applyIfPending()` 返回 | 从 `attachBaseContext()` 内该阶段开始 |
| `app_startup_logging_initialized` | `app` | `LoggingInitializer.initialize()` 返回 | 日志初始化自身耗时 |
| `app_startup_application_complete` | `app` | `Application.onCreate()` 调度完启动后台任务 | 从 `attachBaseContext()` 到 `Application.onCreate()` 尾部 |

`app_startup_process_start` 发生在日志初始化前，不能立即写 Logan。实现上由 `StartupTelemetry` 缓存为 pending event，在 `LoggingInitializer.initialize()` 成功安装 `MfLog` 后 flush。若日志初始化失败，后续保持 no-op，不阻塞启动。

### 6.2 Activity 首屏阶段

| 事件 | category | 触发点 | durationMs 口径 |
|---|---|---|---|
| `app_startup_activity_create_start` | `app` | `MainActivity.onCreate()` 开始 | 无 |
| `app_startup_splash_installed` | `app` | `installSplashScreen()` 返回 | 从 Activity onCreate 开始 |
| `app_startup_edge_to_edge_enabled` | `app` | `enableEdgeToEdge()` 返回 | 从 Activity onCreate 开始 |
| `app_startup_activity_content_set` | `app` | `setContent { ... }` 调用返回 | 从 Activity onCreate 开始 |
| `app_startup_first_frame` | `app` | 首个绘制回调触发 | 从 Activity onCreate 开始 |

`app_startup_first_frame` 是用户可见首屏口径。实现可以在 `MainActivity` root decor view 上使用一次性 pre-draw / draw 回调，触发后立即移除，避免持续监听。

`app_startup_activity_content_set` 与 `app_startup_first_frame` 都记录是必要的：前者定位 Kotlin / Compose 注册阶段，后者定位真实可见首屏。如果两者差距大，说明首帧渲染或首屏依赖收集更可疑。

### 6.3 后台启动流程

后台流程不计入首屏总耗时，但必须独立记录 terminal event：

| flowName | 现有落点 | 设计动作 |
|---|---|---|
| `runtime_restore` | `RuntimeRestoreCoordinator.start()` / `restoreAll()` | 增加聚合 `startup_flow_start` / `startup_flow_complete`，保留每个 store 的 `runtime_restore_*` |
| `plugin_auto_update` | `PluginAutoUpdateCoordinator.start()` / `runIfDue()` | 复用现有 start / skipped / completed / failed，补齐 `flowName`、`startupType`、ID 字段 |
| `playback_runtime_restore` | `RuntimeRestoreCoordinator` 外层 + `PlaybackRuntimeStore.restore()` | `:player` 不直接依赖 `:app` telemetry；外层 `runtime_restore` 聚合事件提供启动 ID，`playback_runtime_restore_*` 保留业务字段 |
| `update_check_on_launch` | `UpdateCheckCoordinator.start()` / `UpdateChecker.checkOnLaunch()` | 由 `MusicFreeApplication` 传入普通 Map 形式的启动上下文字段；`:updater` 不依赖 `:app` 类型 |
| `logging_preference_bridge` | `MusicFreeApplication.startLoggingPreferenceBridge()` | 记录 bridge 调度完成，不记录每次偏好变化 |

统一后台事件名优先使用：

- `startup_flow_start`
- `startup_flow_complete`
- `startup_flow_skipped`
- `startup_flow_failed`
- `startup_flow_cancelled`

已有业务事件继续保留，用于业务诊断。统一事件用于启动耗时总览，业务事件用于细节下钻。

## 7. 统一字段

所有启动相关事件至少包含：

| 字段 | 说明 |
|---|---|
| `operation` | 固定为 `startup` 或具体后台操作名 |
| `startupType` | `cold_process_start` / `warm_activity_recreate` / `activity_resume_existing` |
| `processStartId` | 进程启动 ID |
| `activityStartId` | Activity 启动 ID；Application 阶段为空 |
| `activityLaunchIndex` | 同一进程内 Activity create 序号 |
| `isFirstActivityInProcess` | 是否为本进程第一个 Activity create |
| `phase` | 例如 `application`、`activity_content_set`、`first_frame` |
| `flowName` | 后台流程名；非后台流程为空 |
| `durationMs` | terminal 事件必填 |
| `result` | `success` / `failure` / `cancelled` / `skipped` |
| `reason` | skipped / failure / cancelled 时填写 |

字段值只使用字符串、数字、布尔值，不记录复杂对象。URL 类字段沿用现有日志规范；启动 ID 使用随机 UUID，不持久化到磁盘。

## 8. 组件与代码落点

### 8.1 `StartupTelemetry`

建议文件：`app/src/main/java/com/hank/musicfree/startup/StartupTelemetry.kt`

职责：

- 生成并持有 `processStartId`。
- 记录 `Application.attachBaseContext()` 和 `Application.onCreate()` 起点。
- 为每次 Activity create 生成 `StartupActivitySession`。
- 提供 `markPhase()`、`startFlow()`、`completeFlow()` 等小 API，统一字段。
- 缓存日志初始化前的 pending events，并在 Logan 初始化后 flush。

`StartupTelemetry` 不依赖 Activity、View 或 Compose 状态，不持久化任何运行对象。

模块边界：

- `StartupTelemetry` 只放在 `:app`，不让 `:player`、`:plugin`、`:data`、`:updater` 反向依赖 `:app`。
- `:app` 内的 `RuntimeRestoreCoordinator`、`PluginAutoUpdateCoordinator` 可以直接使用 `StartupTelemetry`。
- 跨模块 coordinator 如 `UpdateCheckCoordinator` 只接收 `Map<String, Any?>` 形式的启动上下文字段，避免新增模块耦合。
- `PlaybackRuntimeStore` 等运行态 Store 保留自身业务事件；启动上下文由 `RuntimeRestoreCoordinator` 的聚合 flow 事件承载。

### 8.2 `MusicFreeApplication`

改动点：

1. 在 `attachBaseContext()` 中记录进程启动和 pending restore 耗时。
2. 在 `onCreate()` 开始记录 application 阶段。
3. 包裹 `AxiosShim.setBaseClient()`、`LoggingInitializer.initialize()`、启动后台 coordinator 调度。
4. `LoggingInitializer.initialize()` 后 flush pending events。
5. `onCreate()` 尾部记录 `app_startup_application_complete`。

`StartupBackupRestore.applyIfPending()` 必须保持在 `attachBaseContext()` 中执行，不能因为埋点移到 `onCreate()`。

### 8.3 `MainActivity`

改动点：

1. `onCreate()` 最前创建 `StartupActivitySession`。
2. 保持 `installSplashScreen()` 仍在 `super.onCreate()` 前。
3. `installSplashScreen()`、`enableEdgeToEdge()`、`setContent` 调用返回后记录阶段。
4. root decor view 注册一次性首帧回调，记录 `app_startup_first_frame`。
5. `onResume()` 中跳过当前 Activity create 后的第一次 resume；同一 Activity 实例后续从后台回到前台时记录 `activity_resume_existing`。

不改变现有 `Scaffold`、MiniPlayer、导航和主题结构。

### 8.4 后台 coordinator

改动点：

- `RuntimeRestoreCoordinator` 增加整体 restore flow 的聚合日志；每个 store 日志保留。
- `PluginAutoUpdateCoordinator`、`UpdateCheckCoordinator` 使用统一字段补齐 terminal duration。
- `PlaybackRuntimeStore` 不直接补 `:app` 启动 ID；播放恢复属于 `RuntimeRestoreCoordinator` 聚合 flow 的一部分，由外层事件关联启动上下文。

## 9. 错误处理

1. 埋点失败不能影响启动。`StartupTelemetry` 内部日志调用使用 `runCatching` 兜底。
2. 日志初始化失败时保持当前降级策略：`MfLog` 回到 no-op，应用继续启动。
3. 后台流程异常继续由原 coordinator 捕获并记录业务失败；新增启动 flow 只补 terminal failure / cancelled。
4. `CancellationException` 仍按协程规则重新抛出，不吞取消。
5. pending event 数量固定且很小，只缓存启动早期几条，不做无界列表。

## 10. 测试方案

### 10.0 Dev Harness

新增 Startup Harness 入口：[docs/dev-harness/startup/rules.md](../../dev-harness/startup/rules.md)。

后续任何启动流程修改必须先读该 rules，并重点检查：

- 启动类型分类是否仍能区分 `cold_process_start`、`warm_activity_recreate`、`activity_resume_existing`。
- `StartupBackupRestore.applyIfPending()`、`installSplashScreen()` 等关键顺序是否被保留。
- 启动路径新增主线程同步工作是否 O(1)，并通过 `app_startup_activity_content_set` / `app_startup_first_frame` 提供前后耗时证据。
- 后台启动流程是否仍非阻塞首屏，并具备 terminal duration 日志。

### 10.1 JVM 单测

- `StartupTelemetryTest`
  - 第一次 Activity create 分类为 `cold_process_start`。
  - 同进程第二次 Activity create 分类为 `warm_activity_recreate`。
  - resume 事件分类为 `activity_resume_existing`，不产生新的 `activityStartId`。
  - pending events 在日志初始化后只 flush 一次。
  - terminal 事件包含 `durationMs`、`result`、`processStartId`、`activityStartId`、`activityLaunchIndex`。

- `RuntimeRestoreCoordinatorTest`
  - restore 聚合 flow start / complete 存在。
  - 单个 store 失败不阻止聚合 terminal 日志。

- `PluginAutoUpdateCoordinatorTest` / `UpdateCheckCoordinatorTest`
  - skipped / success / failure 事件包含 `flowName`、`durationMs`、`result`。

### 10.2 Contract / instrumentation

- 保留 `SplashScreenResourceContractTest`：确保 `installSplashScreen()` 仍在 `super.onCreate()` 前。
- 扩展 `MainActivityStartupTest`：应用可启动且不因首帧回调崩溃。
- 如本地设备可用，Debug 包冷启动后拉取 Logan，确认出现 `app_startup_first_frame` 与 `app_startup_application_complete`。

### 10.3 验证命令

默认收尾验证：

```bash
./gradlew :app:testDebugUnitTest :logging:testDebugUnitTest --no-daemon
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

若修改只落在 `:app` 与日志字段 helper，可根据实际触达模块缩小第一条命令；最终仍以 Debug 构建为普通功能闸门，不要求 Release 构建。

## 11. 验收标准

1. 冷启动日志中能按 `processStartId` 串起：
   - `app_startup_process_start`
   - `app_startup_application_complete`
   - `app_startup_activity_content_set`
   - `app_startup_first_frame`
2. Activity 被销毁但 Application 仍在时，新的 `MainActivity.onCreate()` 记录 `startupType=warm_activity_recreate`，且 `processStartId` 不变、`activityStartId` 变化、`activityLaunchIndex` 递增。
3. Activity 仅从后台 resume 时记录 `startupType=activity_resume_existing`，不生成首屏启动 total 事件。
4. 后台启动流程能看到各自 terminal event 和 `durationMs`，但首屏耗时不等待插件自动更新或更新检查完成。
5. `StartupBackupRestore.applyIfPending()` 仍在 `attachBaseContext()` 期间执行。
6. 启动路径新增主线程同步耗时满足 Startup Harness 预算：`activity_content_set` 中位数新增不超过 5ms，`first_frame` 中位数新增不超过 10ms；若无法采集，必须在验收说明里明确原因。
7. 日志事件命名稳定小写 snake_case，字段满足现有日志规范。

## 12. 实施顺序建议

1. 新增 `StartupTelemetry` 与单测，先锁定分类和字段协议。
2. 接入 `MusicFreeApplication`，覆盖 `attachBaseContext()`、日志初始化和 Application complete。
3. 接入 `MainActivity`，覆盖 Activity create、content set、first frame、resume。
4. 补齐后台 coordinator 聚合 flow 日志。
5. 运行 JVM 测试、dev-harness check、Debug 构建。
