# 测试 / 测试基建 Incidents

> 文档状态：当前规范（Dev Harness — Test Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [test/rules.md](./rules.md)
> 最后校验：2026-05-09

## INC-2026-0005 — feature 模块缺 androidTest runner 基线

- id: INC-2026-0005
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-feature-androidtest-baseline
- guard:
    type: contract-test
    target: app/src/test/java/com/zili/android/musicfreeandroid/harness/contracts/FeatureAndroidTestRunnerBaselineContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#2-pr-1--group-d-feature-androidtest-runner-基线

### 根因

`feature/{home,player-ui,search,settings}/build.gradle.kts` 声明 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`，但模块依赖未包含 `androidTestImplementation(libs.androidx.test.runner)`。AGP 仍创建 connected task；test APK 实例化 runner 时 `ClassNotFoundException`，instrumentation 进程 crash，全量 `connectedAndroidTest` 卡死在第一个 feature 模块。

### 复发条件

任何 feature `build.gradle.kts` 声明 runner 但未声明 runner 依赖。

### 教训

声明 runner 必带 runner 依赖；推荐同时带 `androidx.junit` 与 `espresso-core` 作为基础组合。

## INC-2026-0004 — DataStore multiple active 同文件

- id: INC-2026-0004
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-datastore-per-instance-isolation
- guard:
    type: contract-test
    target: plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/harness/contracts/PluginDataStoreIsolationContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#5-3-1-instrumentation-datastore-隔离

### 根因

`:plugin/src/androidTest/.../*Test.kt` 中手工 `PreferenceDataStoreFactory.create(...)` 复用固定 `*.preferences_pb` 文件名。AndroidJUnit4 每个 `@Test` 方法创建新 test class 实例，DataStore 对同一文件路径的 active scope 有单例约束；旧 scope 未关闭时新实例访问同一路径抛 `There are multiple DataStores active for the same file`，表现为 `installFromFile` / `installFromUrl` 返回 `null`。

### 复发条件

instrumentation test 中静态文件名 + 多 `@Test` 方法 + 未关闭 dataStoreScope。

### 教训

`produceFile = { File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb") }`；`@After` 调 `uninstallAllPlugins()` + `dataStoreScope.cancel()`。

## INC-2026-0003 — mergeExtDexDebugAndroidTest D8 OOM

- id: INC-2026-0003
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-gradle-jvmargs-baseline
- guard:
    type: grep
- signature: |
    grep -E '^org\.gradle\.jvmargs' gradle.properties \
      | awk -F'-Xmx' 'NF>1 { split($2,a,"m"); if (a[1]+0 < 4096) print "gradle.properties -Xmx=" a[1] "m < 4096m" }'
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#4-6-1-d8-oom-根因与修复

### 根因

`gradle.properties` 旧基线 `-Xmx2048m` 不足以支撑全量 androidTest dex 合并；`:plugin:mergeExtDexDebugAndroidTest` 在 D8 阶段 `OutOfMemoryError: Java heap space`，全量 `connectedAndroidTest` 失败。

### 复发条件

`gradle.properties` `-Xmx` 被回退到 < 4096m。

### 教训

仓库级基线必须写入 `gradle.properties`，不要靠开发者手动 `-Dorg.gradle.jvmargs`。

## INC-2026-0002 — PlayerController.connect 主线程 runBlocking 死锁

- id: INC-2026-0002
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-no-runblocking-mainthread-in-instrumentation
- guard:
    type: contract-test
    target: app/src/test/java/com/zili/android/musicfreeandroid/harness/contracts/PlayerControllerSetupContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#4-6-2-playercontrollertest-setup-死锁根因与修复

### 根因

`PlayerControllerTest.@Before` 在 `context.mainExecutor.execute { runBlocking { controller.connect() } }` 中阻塞主线程，`PlayerController.connect()` 内部又通过 `Dispatchers.Main.immediate` 等待 `MediaController.buildAsync()`；连接回调无法回到主线程，整个 instrumentation 永久挂起在 `Tests 0/N completed`。`runOnAppThread` helper 的无界 `latch.await()` 把失败放大为永久 hang。

### 复发条件

instrumentation test 中嵌套 `mainExecutor.execute { runBlocking { ... } }` 或使用无界 `latch.await()`。

### 教训

`controller.connect()` 在测试线程执行 + `withTimeout(5_000L)` 兜底；helper 改 bounded await + AtomicReference 异常回传。

## INC-2026-0001 — runBlocking + Flow.first { predicate } 死锁

- id: INC-2026-0001
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-runtest-mandatory
- guard:
    type: contract-test
    target: app/src/test/java/com/zili/android/musicfreeandroid/harness/contracts/TestRunTestIdiomContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#3-pr-1--group-c-runtest-迁移

### 根因

`*ViewModelTest.kt` 同时使用 `runBlocking + UnconfinedTestDispatcher + viewModelScope.launch + Flow.first { predicate }`：predicate 在 hot dispatcher 上自旋；viewModelScope 协程未被 advance，Flow 无新发射；用例 hang。仅在 Robolectric/ByteBuddy 已被预热的 JVM 复现，因此初次运行可能"看起来通过"。

### 复发条件

ViewModel 单测里同时出现 `runBlocking` 与 `Flow.first { ... }` 自旋谓词。

### 教训

全部走 `runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`；`Flow.first { predicate }` 替换为 `advanceUntilIdle()` + `Flow.first()`。
