# 插件系统 Rules

> 文档状态：当前规范（Dev Harness — Plugin）
> 适用范围：QuickJS runtime 线程模型、PluginManager 安装/更新编排、集成测试网络通道与 DataStore 隔离
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[QuickJS 线程修复设计](../../superpowers/specs/2026-04-19-quickjs-threading-fix-design.md)、[Android 测试稳定性设计](../../superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md)
> 最后校验：2026-05-12

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

## userVariables 写入串行化 {#rule-user-variable-serialization}

implemented_by: INC-2026-0014

- 插件 `userVariables` 写入路径 MUST 通过 Mutex / 单飞（single-flight）模式串行化；MUST NOT 让多个协程并发写 DataStore。
- refresh / reload userVariables MUST 等待所有 in-flight 写入完成，再读 DataStore；否则覆盖未持久化的 dialog 编辑。
- userVariable 写错误 MUST 向调用方暴露（不在 catch block swallow），由 ViewModel 决定是否回滚 UI。
- 适用范围：`:plugin/src/main/.../uservariable/...` 与 `JsBridge.setUserVariable(...)` 写路径。

## 默认引导插件 (dev fixture) {#rule-default-bootstrap-plugins-list}

- `app/src/main/java/com/hank/musicfree/bootstrap/DefaultPlugins.kt` 中的 `subscriptionUrls` 与 `pluginUrls` 列表是 dev / test fixture，由 `DefaultPluginsBootstrapper` 在 `MusicFreeApplication.onCreate()` 触发，每次冷启动 reconcile。
- MUST：发布构建前必须人工把两个 list 改成 `emptyList()` 或全行 `//` 注释。任何 release tag 携带未清空的列表都视为违规。
- MUST NOT：不得在该文件中引入按 buildType / BuildConfig 切换的"自动剥离"逻辑——保留"必须手动改 + 人工 review 一次"的语义，避免把策略埋进 gradle。
- 适用范围：`:app/bootstrap/`、`MusicFreeApplication.onCreate()`、任何调用 `DefaultPluginsBootstrapper.start()` 的入口。
- 关联设计：`docs/superpowers/specs/2026-05-11-default-bootstrap-plugins-design.md`。

## 插件失败必须可见 {#rule-plugin-failure-must-surface}

implemented_by: INC-2026-0018

- `:plugin/manager/PluginManager.kt` 中 install / load 路径捕获到失败 MUST 通过 `recordFailedEntry(...)` 写入 `PluginEntry.state = Failed(reason, detail)`；MUST NOT 在 catch 后仅 `return null` 而不更新 `allEntries`。
- `:plugin/runtime/` 中 PluginState 状态变更日志 MUST 通过 `PluginStateKeys.stateKey(...)` / `reasonKey(...)` 写入字段；MUST NOT 直接 `state::class.simpleName` 或 `enum.name`（R8 minify 后不稳定）。
- 复发条件 + 升级触发：再次出现 `catch (e: Exception) { return null }` 形态而未写 `PluginEntry.state = Failed` 的修复 commit，则升级为 contract-test guard。
