# 插件系统 Incidents

> 文档状态：当前规范（Dev Harness — Plugin Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [plugin/rules.md](./rules.md)
> 最后校验：2026-05-09

## INC-2026-0010 — 集成测试默认依赖 kstore.vip 真网络

- id: INC-2026-0010
- area: plugin
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/plugin/rules.md#rule-network-test-gated
- guard:
    type: contract-test
    target: plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/harness/contracts/PluginNetworkTestGateContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#5-3--pintegration-门控机制

### 根因

`:plugin/src/androidTest/.../PluginRuntimeIntegrationTest.kt` 旧版 4 个用例直连 `kstore.vip`，CI 默认通道因网络抖动 flaky；类级 `@Ignore` 同时阻塞 3 个本地用例。

### 复发条件

新 `:plugin/src/androidTest/` 中出现真域名（`kstore.vip` 等显式列表），但文件名不以 `NetworkIntegrationTest.kt` 结尾，或类内未 `Assume.assumeTrue` 引用 `pluginNetworkTests`。

### 教训

按 `PluginRuntimeLocalIntegrationTest`（本地）/ `PluginRuntimeNetworkIntegrationTest`（真网，`Assume` 门控）/ `PluginManagerHttpLifecycleTest`（MockWebServer）三类拆分；`-Pintegration` 启用真网通道。

## INC-2026-0009 — QuickJS 跨线程访问 runtime 崩溃

- id: INC-2026-0009
- area: plugin
- date: 2026-04-19
- status: active
- rule_ref: docs/dev-harness/plugin/rules.md#rule-quickjs-single-thread
- guard:
    type: manual
- fix_ref: docs/superpowers/specs/2026-04-19-quickjs-threading-fix-design.md

### 根因

QuickJS `Context` / `JsBridge` 实例不是线程安全的；多线程调用 `Context.evaluate(...)` 触发 native crash 或不确定结果。

### 复发条件

`:plugin/src/main/` 在非 owning thread / 非 `quickJsDispatcher` 上访问 `Context` / `JsBridge`。静态扫成本高（需要跨函数追踪 dispatcher 切换），暂列 manual。

### 教训

`withContext(quickJsDispatcher)` 切回单线程；JsBridge 内部用 `MutableSharedFlow` 路由跨线程请求。

### 备注

升级触发条件 = 再现一次 QuickJS 跨线程崩溃事件即升级为 contract-test 或 runtime invariant；harness-curator-skill 在巡检时显式列 manual incidents 提醒人工复核。
