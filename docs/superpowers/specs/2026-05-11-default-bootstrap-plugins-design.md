---
status: 当前规范
date: 2026-05-11
topic: 默认引导插件（dev/test fixture）
scope: :app 启动期自动安装预设订阅源与插件
---

# 默认引导插件设计

## 1. 背景与目标

测试阶段需要冷启动后即可获得一组可用的播放插件，避免每次清数据都手动重装。目前 `:plugin` 模块只在 UI 触发时按需安装，缺少"默认引导"通道。

需求要点：

- 冷启动时自动安装下列两个来源：
  - 订阅源：`https://13413.kstore.vip/yuanli/yuanli.json`
  - 单插件：`https://raw.githubusercontent.com/ThomasBy2025/musicfree/refs/heads/main/plugins/wy.js`
- URL 列表必须放在便于"逐行注释 / 清空"的位置；发布前由开发者手动注释掉。
- 目前阶段默认开启（不需要 BuildConfig.DEBUG 或 buildType 门控）。
- 不影响启动性能、无网络时不崩溃。

非目标：

- 不做 plugin 签名校验。
- 不做"用户手动卸载 → 永久不再装回"的持久化记录（见 §5 取舍）。
- 不做 release 构建的静态门控（lint / 自动剥离）。发布前由人工注释列表。

## 2. 架构概览

新增三件事，全部位于 `:app` 模块：

```
app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/
├── DefaultPlugins.kt              ← 唯一需要按发布节奏改动的文件
└── DefaultPluginsBootstrapper.kt  ← @Singleton；reconcile 逻辑
```

并在 `MusicFreeApplication.onCreate()` 末尾调用 `bootstrapper.start()`。

依赖方向保持 `:app → :plugin`，不反向引入。

`:app` 若尚无 `@ApplicationScope CoroutineScope` 的 Hilt 限定符，需要在 `:app` 加一个 `CoroutineModule`（实现阶段先确认是否已存在；若 `:logging` 或其他模块已有，复用）。

## 3. 数据结构

### 3.1 `DefaultPlugins.kt`

```kotlin
package com.zili.android.musicfreeandroid.bootstrap

/**
 * dev / test fixture：冷启动时自动 reconcile 的预设插件来源。
 * 发布前必须把所有条目注释掉或清空列表。
 */
object DefaultPlugins {
    val subscriptionUrls: List<String> = listOf(
        "https://13413.kstore.vip/yuanli/yuanli.json",
    )

    val pluginUrls: List<String> = listOf(
        "https://raw.githubusercontent.com/ThomasBy2025/musicfree/refs/heads/main/plugins/wy.js",
    )
}
```

每个 URL 独占一行，便于 `//` 前缀禁用。

### 3.2 `DefaultPluginsBootstrapper.kt`

```kotlin
@Singleton
class DefaultPluginsBootstrapper @Inject constructor(
    private val pluginManager: PluginManager,
    private val pluginMetaStore: PluginMetaStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun start() {
        if (DefaultPlugins.subscriptionUrls.isEmpty() && DefaultPlugins.pluginUrls.isEmpty()) {
            return
        }
        applicationScope.launch(Dispatchers.IO) { reconcile() }
    }

    @VisibleForTesting
    internal suspend fun reconcile() { /* see §4 */ }
}
```

`start()` 仅触发协程，不阻塞 `Application.onCreate()`。

## 4. Reconcile 语义

调用顺序：先订阅源，后单插件。这样订阅源里若已包含 `wy.js`，单插件那一遍会直接跳过。

### 4.1 订阅源

1. `val existing = pluginMetaStore.subscriptions.first().map { it.url }.toSet()`
2. 对 `DefaultPlugins.subscriptionUrls` 中每个 `url`：
   - 若 `url in existing` → 跳过，记 debug 级 `default_plugin_bootstrap_subscription_skipped { url }`。
   - 否则调用 `pluginManager.installFromSubscriptionUrl(url)`，记 `default_plugin_bootstrap_subscription { url, successCount, failureCount, durationMs }`。
   - 抛异常 → 捕获，记 `default_plugin_bootstrap_failed { stage="subscription", url, errorClass }`，继续下一条。

### 4.2 单插件

1. 取当前已加载插件列表（通过 `PluginMetaStore` / `PluginManager` 暴露的接口），构造"已知来源 URL 集合"。**判定字段在实现阶段确认**——优先使用 `LoadedPlugin.sourceUrl` 或等价字段；若现有 API 不暴露 source URL 列表，bootstrapper 在 `:app` 侧实现一个 helper 方法直接读取 metaStore，**不修改 `:plugin` 公共 API**。
2. 对 `DefaultPlugins.pluginUrls` 中每个 `url`：
   - 若已存在 → 跳过，记 `default_plugin_bootstrap_plugin_skipped { url }`。
   - 否则调用 `pluginManager.installFromNetworkUrl(url)`，记 `default_plugin_bootstrap_plugin { url, success, durationMs }`。
   - 抛异常 → 捕获，记 `default_plugin_bootstrap_failed { stage="plugin", url, errorClass }`，继续。

### 4.3 关键性质

| 性质 | 实现依据 |
|---|---|
| 幂等 | URL 已存在即跳过；第二次冷启动 reconcile 是 no-op |
| 不阻塞 UI | `applicationScope.launch(Dispatchers.IO)`，`onCreate()` 立即返回 |
| 不崩溃 | 单条 URL 异常被 catch；无网时返回错误 result 也只记日志 |
| 串行化 | 依赖 `PluginManager` 内部 `mutex.withLock`，bootstrapper 不需自加锁 |
| 用户卸载会被覆盖 | 见 §5 取舍 |

## 5. 取舍：用户卸载的处理

当前设计：用户在「设置」里手动卸载某条预设插件后，下次冷启动 reconciler 会重新装回。

理由：

- 该功能是测试 fixture，发布前整张表会注释掉，不会困扰真实用户。
- 维持简单：不引入"用户卸载黑名单"持久化与配套测试。

若后续需要"尊重用户卸载"，需要在 `PluginMetaStore` 增加 `userRemovedDefaultPluginUrls: Flow<Set<String>>`，并在 reconcile 时过滤。本 spec 不覆盖此扩展。

## 6. 日志规范

所有事件命名：

- `default_plugin_bootstrap_subscription`（订阅源安装尝试）
- `default_plugin_bootstrap_subscription_skipped`
- `default_plugin_bootstrap_plugin`（单插件安装尝试）
- `default_plugin_bootstrap_plugin_skipped`
- `default_plugin_bootstrap_failed`（任意阶段异常）
- `default_plugin_bootstrap_completed`（reconcile 结束，含 `installedSubscriptionCount`、`installedPluginCount`、`skippedCount`、`failedCount`、`totalDurationMs`）

字段统一使用 snake_case key。使用 `:logging` 模块的 `MfLog`，不引入 `android.util.Log.*`。

## 7. 测试计划

### 7.1 单元测试

`app/src/test/java/.../bootstrap/DefaultPluginsBootstrapperTest.kt`，使用 `runTest` + `MainDispatcherRule`，fake `PluginManager` 与 `PluginMetaStore`。

必须覆盖：

- `reconcile_installsMissingSubscriptions`
- `reconcile_skipsAlreadyInstalledSubscriptions`
- `reconcile_installsMissingPlugins`
- `reconcile_skipsAlreadyInstalledPlugins`
- `reconcile_continuesOnSubscriptionFailure`
- `reconcile_continuesOnPluginFailure`
- `reconcile_emptyListsAreNoOp`（验证发布场景：两个 list 都空时 PluginManager 完全未被调用）

测试不启 QuickJS、不连网络。

### 7.2 手动运行态验收（Debug build）

| # | 步骤 | 期望 |
|---|---|---|
| 1 | 清 app data → 冷启动 | Logcat 含 `default_plugin_bootstrap_subscription` 与 `default_plugin_bootstrap_plugin` 各一条成功记录 |
| 2 | 打开「设置 → 插件」 | 看到 `yuanli` 订阅源 + 它装入的插件 + `wy.js` 单插件 |
| 3 | 杀进程冷启动 | 仅看到 `*_skipped` 与 `default_plugin_bootstrap_completed { installed*Count=0 }` |
| 4 | 手动卸载 `wy.js` → 杀进程冷启动 | `wy.js` 被重新安装 |
| 5 | 飞行模式冷启动 | 看到 `default_plugin_bootstrap_failed`，app 正常进入主页，不崩溃 |
| 6 | 注释掉 `DefaultPlugins.kt` 所有 URL → 清 app data → 冷启动 | 无任何 bootstrap 日志，无自动安装的插件 |

## 8. Dev-Harness 守门

`docs/dev-harness/plugin/rules.md` 新增一条 rule：

> **MUST**：`app/src/main/java/.../bootstrap/DefaultPlugins.kt` 中的 URL 列表是 dev / test fixture。发布构建前必须人工注释或清空所有条目；任何线上版本不得携带未清空的列表。

关联 incident ID 与可选的 lint 守门留待实现阶段补充。Release-time 自动 lint **不在本 spec 范围内**。

## 9. 实现阶段需要确认的开放点

实现 PR 中需明确：

1. `:app` 是否已有 `@ApplicationScope CoroutineScope` Hilt 限定符；若无，在 `:app` 新增 `CoroutineModule`。
2. `PluginManager` / `PluginMetaStore` 暴露的"已安装插件来源 URL"读取方式（确认字段名，必要时用 helper）。
3. 关联 incident ID。

这些都是落地细节，不影响整体架构。
