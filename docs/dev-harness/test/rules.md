# 测试 / 测试基建 Rules

> 文档状态：当前规范（Dev Harness — Test）
> 适用范围：JVM 单测异步范式、instrumentation 主线程模型、DataStore 隔离、feature 模块 androidTest runner 基线、Gradle JVM 内存基线、`@Ignore` 政策
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[Android 测试稳定性设计](../../superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md)
> 最后校验：2026-05-09

## 强制入口

新增或修改任何 `*Test.kt` / `*build.gradle.kts` 测试 wiring / `gradle.properties` JVM 参数前，必须先读取本文件。

## ViewModel 单测 runTest 范式 {#rule-runtest-mandatory}

implemented_by: INC-2026-0001

- ViewModel 单测 MUST 用 `runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`。
- MUST NOT 在 `*ViewModelTest.kt` 中使用 `runBlocking { ... .first { predicate } ... }` 自旋谓词模式。
- 若需要等待 viewModelScope.launch 副作用，MUST 在 `runTest` block 内显式 `advanceUntilIdle()`，再读 `Flow.first()`（无谓词）。
- `MainDispatcherRule` 当前在 `:feature:settings`、`:feature:search` 各保留一份；不强制本期 dedup（详见非目标）。

## instrumentation test 主线程禁阻塞 {#rule-no-runblocking-mainthread-in-instrumentation}

implemented_by: INC-2026-0002

- `*Test.kt` 在 `androidTest/` 中 MUST NOT 使用 `context.mainExecutor.execute { runBlocking { ... } }` 或同义模式。
- `CountDownLatch.await()` MUST 提供有界 timeout，例如 `latch.await(5, TimeUnit.SECONDS)`。
- 异常 MUST 通过 `AtomicReference<Throwable?>` 等机制从主线程回传到测试线程，再 `throw` 触发 fail。

## Gradle JVM 内存基线 {#rule-gradle-jvmargs-baseline}

implemented_by: INC-2026-0003

- `gradle.properties` MUST 含 `org.gradle.jvmargs` 且 `-Xmx` 数值 ≥ 4096m。
- 全量 androidTest dex 合并需要至少 4 GiB 堆，否则 `:plugin:mergeExtDexDebugAndroidTest` 会 D8 OOM。
- 提升 heap 是仓库级基线，不要让开发者手动传 `-Dorg.gradle.jvmargs`。

## DataStore 隔离 {#rule-datastore-per-instance-isolation}

implemented_by: INC-2026-0004

- instrumentation test 中手工构造 `PreferenceDataStoreFactory.create(...)` 时，`produceFile` MUST 为每个 test 实例生成唯一文件名（如 `File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb")`）。
- MUST NOT 在 instrumentation test 复用固定 `*.preferences_pb` 路径；AndroidJUnit4 每个 `@Test` 创建新 test class 实例，DataStore 对同一文件路径的 active scope 有单例约束，固定文件名会触发 `There are multiple DataStores active for the same file`。
- `@After` MUST 调用 `pluginManager.uninstallAllPlugins()` 关闭 QuickJS 后再 `dataStoreScope.cancel()` 关闭 DataStore scope。

## feature 模块 androidTest runner 基线 {#rule-feature-androidtest-baseline}

implemented_by: INC-2026-0005

- 任何在 `feature/*/build.gradle.kts` 声明 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` 的模块 MUST 同时声明 `androidTestImplementation(libs.androidx.test.runner)`。
- 推荐同时引入 `androidTestImplementation(libs.androidx.junit)` 与 `androidTestImplementation(libs.androidx.espresso.core)` 作为基础组合，避免后续新增 androidTest 时再补依赖。
- 全量 `connectedAndroidTest` MUST 通过——空 androidTest 源的 feature 模块也必须能实例化 runner。

## `@Ignore` 政策

- `@Ignore` 在 `:plugin/src/androidTest/` 与 `:feature:settings/src/test/` 等位置当前应为 0。
- 新增 `@Ignore` MUST 同步登记 incident 并写明触发条件、升级方案；否则在下次 `harness-curator-skill` 巡检中报告。

## 网络通道门控

参见 [plugin/rules.md#rule-network-test-gated](../plugin/rules.md#rule-network-test-gated)。
