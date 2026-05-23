# Startup / 启动耗时 Rules

> 文档状态：当前规范（Dev Harness — Startup）
> 适用范围：Application / Activity 启动流程、SplashScreen、启动耗时日志、冷启动 / Activity 重建启动分类、启动后台任务调度。
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[启动耗时分段与结构化日志设计](../../superpowers/specs/2026-05-19-startup-telemetry-design.md)
> 最后校验：2026-05-19

## 强制入口

新增或修改以下内容前，必须先读取本文件：

- `MusicFreeApplication.attachBaseContext()` / `MusicFreeApplication.onCreate()`。
- `MainActivity.onCreate()` / `onStart()` / `onResume()` 与 AndroidX SplashScreen 调用。
- `StartupBackupRestore`、`LoggingInitializer`、`StartupTelemetry`。
- 启动时调度的 coordinator：`RuntimeRestoreCoordinator`、`PluginAutoUpdateCoordinator`、`PlaybackStartupCoordinator`、`UpdateCheckCoordinator`。
- 启动耗时日志事件、启动类型字段、首帧统计、启动后台流程统计。
- Manifest / theme 中影响启动 Activity、Splash theme、postSplashScreenTheme 的配置。

若改动同时涉及 UI、Runtime State、播放器、插件、网络或测试，还必须读取对应 rules。

## 启动类型分类 {#rule-startup-type-classification}

guard: contract-test

启动日志 MUST 区分三种类型：

- `cold_process_start`：新进程启动，`Application.attachBaseContext()` 和 `Application.onCreate()` 都发生。
- `warm_activity_recreate`：同一进程内新的 `MainActivity` 实例进入 `onCreate()`，用于 Activity 被销毁但 Application 仍存在的场景。
- `activity_resume_existing`：Activity 实例未销毁，仅从后台回到前台。

MUST：

- 每次 `MainActivity.onCreate()` 都生成新的 `activityStartId`，并递增 `activityLaunchIndex`。
- 同一进程内保持同一个 `processStartId`。
- 跳过 Activity create 后的第一次 `onResume()`，避免把正常启动的首个 resume 误记为 `activity_resume_existing`。
- `activity_resume_existing` 不得生成新的首屏启动 total 事件。

## 启动关键顺序 {#rule-startup-order-preserved}

guard: contract-test + manual

启动埋点和流程调整 MUST 保持现有关键顺序：

- `StartupBackupRestore.applyIfPending()` 必须保留在 `Application.attachBaseContext()`，且在 Room / DataStore 打开前执行。
- `MainActivity.installSplashScreen()` 必须保留在 `super.onCreate(savedInstanceState)` 前。
- 日志初始化前产生的 early startup event 只能缓存为有限 pending event；`LoggingInitializer.initialize()` 成功安装 `MfLog` 后再 flush。
- 启动后台任务只能调度，不得在 `Application.onCreate()` 或 `MainActivity.onCreate()` 同步等待完成。
- 不得新增 `setKeepOnScreenCondition` 等让 Splash 等待后台插件、更新检查、Runtime restore 全量完成的逻辑；确需改变 Splash 生命周期时必须先更新设计文档。

## 主线程启动预算 {#rule-main-thread-startup-budget}

guard: manual + startup-log-evidence

启动路径新增主线程同步工作 MUST 保持在可接受范围内。

MUST NOT 在 `Application.attachBaseContext()`、`Application.onCreate()`、`MainActivity.onCreate()` 的主线程路径新增：

- 网络请求、插件加载、QuickJS evaluate。
- Room / DataStore 大 payload 读取、文件大对象反序列化。
- `runBlocking`、`Thread.sleep`、无界 `CountDownLatch.await()`、同步等待 coroutine job。
- 会随插件数量、队列长度、歌单数量、搜索结果数量线性增长的循环。

MUST：

- `StartupTelemetry` 的同步逻辑只做 O(1) 内存操作、ID / 时间戳生成和小 Map 组装。
- 新增同步阶段若可能影响首屏，必须提供 Debug 运行证据：至少对比 `app_startup_activity_content_set` 与 `app_startup_first_frame` 的变更前后耗时，或说明无法采集的原因。
- 单次改动新增的主线程同步耗时目标应控制在 `activity_content_set` 中位数 +5ms 以内，`first_frame` 中位数 +10ms 以内；超出时必须把工作移到后台、懒加载或拆成非阻塞恢复。
- 若本地单次波动明显，采集 3 次冷启动日志，用中位数比较，不用单次极值下结论。

## 后台流程非阻塞 {#rule-startup-background-flows-nonblocking}

guard: contract-test + manual

插件自动更新、Runtime restore、播放恢复、更新检查等后台流程 MUST 非阻塞首屏。

MUST：

- 使用 application scope / IO dispatcher / supervisor 边界运行后台流程。
- 为每个后台启动流程记录 terminal event，包含 `flowName`、`durationMs`、`result`，失败时包含 `reason`。
- 单个后台流程失败不得取消其他独立后台流程。

MUST NOT：

- 在首屏路径同步等待插件自动更新、更新检查、搜索 / 详情 snapshot 大 payload 恢复。
- 把后台流程全部完成时间当作 `app_startup_first_frame` 或首屏启动总耗时。

## 启动结构化日志 {#rule-startup-structured-logs}

guard: contract-test

启动相关日志 MUST 使用 `MfLog` / `StartupTelemetry`，不得在业务代码中新增直接 `android.util.Log.*` 或直接 Logan 调用。

启动 terminal 事件 MUST 包含：

- `operation`
- `startupType`
- `processStartId`
- `activityStartId`（Application 阶段可为空）
- `activityLaunchIndex`
- `phase` 或 `flowName`
- `durationMs`
- `result`
- `reason`（skipped / failed / cancelled 时）

事件名使用稳定小写 snake_case。字段值只使用字符串、数字、布尔值或浅层 Map，不记录 Activity、View、Context、Coroutine、Media3、QuickJS 等运行对象。

## 测试要求

修改启动分类、首帧统计、启动后台流程或主线程启动路径时，至少补齐对应测试：

- `StartupTelemetryTest`：分类、ID、pending event、terminal 字段。
- `SplashScreenResourceContractTest`：Splash 调用顺序仍正确。
- `RuntimeRestoreCoordinatorTest` 或相关 coordinator test：后台 flow terminal 日志存在，失败隔离仍成立。
- `MainActivityStartupTest`：Activity 可启动且首帧回调不崩溃。

收尾默认运行：

```bash
./gradlew :app:testDebugUnitTest :logging:testDebugUnitTest --no-daemon
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```
