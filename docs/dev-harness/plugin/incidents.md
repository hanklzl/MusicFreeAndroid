# 插件系统 Incidents

> 文档状态：当前规范（Dev Harness — Plugin Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [plugin/rules.md](./rules.md)
> 最后校验：2026-05-12

## INC-2026-0018 — 插件加载失败被静默吞掉，UI 无法定位

- id: INC-2026-0018
- area: plugin
- date: 2026-05-12
- status: active
- rule_ref: docs/dev-harness/plugin/rules.md#rule-plugin-failure-must-surface
- guard:
    type: manual
- fix_ref: docs/superpowers/plans/2026-05-11-plugin-engine-alignment-plan.md §Phase C

### 根因

插件安装 / 加载路径 `catch (e) { return null }` 把版本不匹配、解析失败、缺 platform 字段等错误吞掉，UI 只看到"插件没了"无法定位。

### 复发条件

`:plugin/manager/PluginManager.kt` 中新加 catch block 后只 `return null` / 不写 `PluginEntry.state = Failed`。

### 教训

所有失败路径都必须 `recordFailedEntry(filePath, reason, detail)` 写 Failed entry，让 UI 徽章 + 错误面板能展示原因，让用户能重试或卸载。

### 备注

guard 当前 manual：升级触发条件 = 再次出现一次相关 incident 即升级为 contract-test（静态扫 `:plugin/manager` 内的 `catch (e: Exception) { return null }` 形态）。

## INC-2026-0014 — userVariables 写入并发竞态

- id: INC-2026-0014
- area: plugin
- date: 2026-05-10
- status: active
- rule_ref: docs/dev-harness/plugin/rules.md#rule-user-variable-serialization
- guard:
    type: manual
- fix_ref: 5ba9906, 8b94e63, b1cbb08, f0d4727, 1d3583e, 9c9d2ab

### 根因

插件 userVariables（per-plugin 键值对）由多个协程并发写 DataStore，无串行化：dialog 编辑被 refresh 覆盖、refresh 期间在途写入丢失、错误被 swallow 导致状态不一致。需要 6 次连续 fix 才稳定。

### 复发条件

`:plugin/src/main/.../uservariable/...` 中出现新写路径（`setUserVariable` / `updateUserVariables` / refresh）但未串行化、未 await in-flight writes。

### 教训

userVariables 写入路径 MUST 通过 Mutex / 单飞（single-flight）模式串行化；refresh 必须等待 in-flight writes 完成；refresh 错误 MUST 暴露给调用方而不是 swallow。

### 备注

guard 当前 manual：升级触发条件 = 再次出现 userVariable 竞态 fix 即升级为 debug-only runtime invariant（在 JsBridge 写路径加 active write counter，断言 ≤1）。

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
