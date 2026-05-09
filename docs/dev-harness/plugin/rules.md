# 插件系统 Rules

> 文档状态：当前规范（Dev Harness — Plugin）
> 适用范围：QuickJS runtime 线程模型、PluginManager 安装/更新编排、集成测试网络通道与 DataStore 隔离
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[QuickJS 线程修复设计](../../superpowers/specs/2026-04-19-quickjs-threading-fix-design.md)、[Android 测试稳定性设计](../../superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md)
> 最后校验：2026-05-09

## 强制入口

新增或修改 `:plugin` 模块代码 / 插件集成测试前，必须先读取本文件。

## QuickJS 线程模型 {#rule-quickjs-single-thread}

implemented_by: INC-2026-0009

- QuickJS `Context` / `JsBridge` MUST 仅在专用单线程 `CoroutineDispatcher` 上访问。
- 跨线程调用必须通过 `withContext(quickJsDispatcher)` 切回 owning thread。
- MUST NOT 在多线程 / `Dispatchers.IO` / `Dispatchers.Default` 上直接调用 `Context.evaluate(...)` 或 `JsBridge.invoke(...)`。
- 复发条件 + 升级触发：`harness-curator-skill` 巡检若发现新的 QuickJS 跨线程崩溃 commit，应将本条 rule 的 guard 升级为 contract-test。

## 集成测试网络通道 {#rule-network-test-gated}

implemented_by: INC-2026-0010

- `:plugin/src/androidTest/` 中依赖真实远端的测试 MUST 集中在 `*NetworkIntegrationTest.kt`。
- 此类测试类 `@Before` 必须执行 `Assume.assumeTrue("...", arg == "true")`，其中 `arg` 取自 instrumentation runner argument `pluginNetworkTests`。
- `:plugin/build.gradle.kts` 必须保留 `testInstrumentationRunnerArguments["pluginNetworkTests"]` 与 `-Pintegration` 的映射。
- 默认通道 (`./gradlew :plugin:connectedAndroidTest`) MUST 跳过真网测试；`-Pintegration` 才执行。
- 真域名（如 `kstore.vip`）出现在测试源中时，文件名必须以 `NetworkIntegrationTest.kt` 结尾且类内必须出现 `Assume.assumeTrue` 引用 `pluginNetworkTests`。

## DataStore 隔离 {#rule-datastore-per-instance-isolation}

implemented_by: INC-2026-0004

参见 [test/rules.md#rule-datastore-per-instance-isolation](../test/rules.md#rule-datastore-per-instance-isolation)。该规则同时影响 plugin 与 test 域，规范以 test/rules.md 为主，本文件保留交叉引用。

## PluginManager 编排

- `installFromUrl` 与 `updatePlugin` 编排路径 MUST 通过 `MockWebServer` 单测验证（`PluginManagerHttpLifecycleTest`），断言 request path 与磁盘内容、`plugins` StateFlow 单例。
- 真插件 JS 解析能力由 `:plugin/src/test/` 单测层守护，instrumentation 仅做编排验证。
