---
状态：当前规范（Android 测试稳定性专项）
适用范围：本仓库 unit / integration / instrumented 测试卡死、runner 崩溃与 `@Ignore` 反应化。
直接执行：是（作为 writing-plans 的输入）
入口：[AGENTS.md](../../../AGENTS.md)
最后校验：2026-05-04
---

# Android 测试稳定性与反应化设计

## 0. 背景与目标

2026-05-04 本地复现结论：

- `./gradlew :feature:settings:testDebugUnitTest --no-daemon`：通过，用时约 39s，但 `feature/settings` 仍有 3 个被 `@Ignore` 掩盖的 DataStore/ViewModel 流测试 hang TODO。
- `./gradlew :player:testDebugUnitTest --no-daemon`：通过，用时约 15s。
- `./gradlew :player:connectedDebugAndroidTest --no-daemon`：通过，19 个 instrumentation 用例完成，说明近期 Media3 controller/service deadlock 修复对 `:player` 聚焦测试有效。
- `./gradlew test --no-daemon`：通过，用时约 1m53s；当前未复现全量 JVM 测试永久卡死。
- `./gradlew connectedAndroidTest --no-daemon`：初始失败点在 `:feature:home:connectedDebugAndroidTest`。测试进程崩溃原因是 test APK 找不到 `androidx.test.runner.AndroidJUnitRunner`，即 feature 模块声明了 `testInstrumentationRunner`，但没有打包 runner 依赖。后续 `:feature:player-ui`、`:feature:search`、`:feature:settings` 也存在同类配置风险。
- 在修复 feature runner baseline 后，full `connectedAndroidTest` 继续暴露第二层失败：`D8: java.lang.OutOfMemoryError: Java heap space`，失败 task 为 `:plugin:mergeExtDexDebugAndroidTest`。单变量验证显示临时 `-Dorg.gradle.jvmargs=-Xmx4096m` 能越过该失败点，当前 `-Xmx2048m` 不足以支撑全量 androidTest dex 合并。
- 在 4 GiB heap 下继续执行 full `connectedAndroidTest` 后，`:player:connectedDebugAndroidTest` 会卡在 `Starting 15 tests ... Tests 0/15 completed`。单类复现 `PlayerControllerTest` 卡在 `Starting 9 tests ... Tests 0/9 completed`，根因是 `@Before` 在 `context.mainExecutor` 主线程块中执行 `runBlocking { controller.connect() }`，阻塞了 `MediaController.buildAsync()` 需要的主线程/回调路径。

仓库当前共有 5 个方法级 `@Ignore` 与 1 个类级 `@Ignore`，分布在 4 个文件中。这些用例的失败均**非生产代码缺陷**，而是测试侧的根因。叠加全量 `connectedAndroidTest` 的 runner 崩溃、D8 OOM 与 player instrumentation setUp 死锁，按根因归为 6 组：

| 组 | 文件 | 案例 | 根因 |
|---|---|---|---|
| A | `app/src/androidTest/.../HomeFidelityHomeStructureTest.kt` | 2 个 `@Test` | 旧 mock MiniPlayer 已被真实 Hilt 版替换；后者在无媒体时 early-return，旧断言永远失败 |
| B | `plugin/src/androidTest/.../PluginRuntimeIntegrationTest.kt` | 1 个类级（覆盖 7 个 `@Test`） | 4 个用例依赖 `kstore.vip` 真实网络，3 个是本地 runtime shim |
| C | `feature/settings/src/test/.../SettingsViewModelTest.kt` + `.../FileSelectorLiteViewModelTest.kt` | 3 个 `@Test` | `runBlocking + UnconfinedTestDispatcher + viewModelScope.launch + Flow.first { predicate }` 死锁，仅在 Robolectric/ByteBuddy 已预热的 JVM 中复现 |
| D | `feature/home`、`feature/player-ui`、`feature/search`、`feature/settings` 的 `build.gradle.kts` | 全量 `connectedAndroidTest` | feature 模块声明 `androidx.test.runner.AndroidJUnitRunner`，但未声明 androidTest runner 基础依赖；空/无测试模块仍会被全量 connected task 执行，导致 test APK 无法实例化 runner |
| E | `gradle.properties` | 全量 `connectedAndroidTest` | Gradle daemon heap 固定 `-Xmx2048m`；full androidTest 构建到 `:plugin:mergeExtDexDebugAndroidTest` 时 D8 合并 dex OOM |
| F | `player/src/androidTest/.../PlayerControllerTest.kt` | `PlayerControllerTest` 单类 + full `:player:connectedDebugAndroidTest` | `@Before` 在主线程 executor 内 `runBlocking { controller.connect() }`，主线程被阻塞后 MediaController async 连接无法完成；helper 的无界 `latch.await()` 放大为永久挂起 |

目标：

1. 全部 `@Ignore` 归零（方法级与类级），通过修复或诚实化测试断言实现。
2. 为网络依赖的 plugin 集成测试建立 `-Pintegration` 门控，使 CI 默认通道仍能跑测试套件、按需可启用真网络回归。
3. 修复全量 `connectedAndroidTest` 的 feature-module runner 基线，使无 androidTest 源的 feature 模块也不会因缺 runner 依赖崩溃。
4. 提升 Gradle daemon heap 到能稳定完成全量 androidTest dex 合并的水平，消除 `:plugin:mergeExtDexDebugAndroidTest` D8 OOM。
5. 修复 `PlayerControllerTest` 的 setUp 主线程死锁，并让测试 helper 使用 bounded await，避免 instrumentation 无期限卡住。
6. 不动生产代码（仅允许测试代码、测试依赖与 Gradle 测试 wiring）。
7. 顺手补 1 条 `MockWebServer` lifecycle 用例，覆盖 `PluginManager.installFromUrl + updatePlugin` 编排路径——即"我们的代码"，与"真插件 JS 解析"双层正交。

非目标（详见 §7 Out-of-scope）：

- 不抽 `PlayerController` 接口
- 不建 `:core:testing` 共享模块
- 不全仓库扫 `runBlocking` 单测
- 不迁移 Compose UI test v2 API；当前 v1 deprecation 只是警告，不是本轮卡死/崩溃根因

## 1. 编排（2 PR，git worktree 隔离）

```
.worktrees/
  test/runtest-and-home-fidelity   (PR 1, branch: test/runtest-and-home-fidelity)
  test/plugin-it-split             (PR 2, branch: test/plugin-it-split)
```

- **PR 1**：Group D feature androidTest runner 基线 + Group E Gradle heap 基线 + Group F PlayerController instrumentation 死锁修复 + Group C `runTest` 迁移 + Group A HomeFidelity 断言改写。改动只触及 Gradle 测试依赖、Gradle 内存配置与测试代码。
- **PR 2**：Group B `:plugin` 集成测试拆分 + MockWebServer + `-Pintegration` Gradle 门控 + 1 行 wiring 改 `:plugin/build.gradle.kts`。

执行顺序：PR 1 先行（先修全量 connected runner 崩溃，再 unblock 5 个方法级 ignore），PR 2 紧随。两 PR 文件路径基本不交集，可并行 review；若并行开发，PR 2 的最终 `./gradlew connectedAndroidTest` 需基于 PR 1 合入后的主线重跑。

worktree 创建必须遵循 `AGENTS.md` 的约束：路径格式 `.worktrees/<branch-name>`、`.worktrees/` 必须已在 `.gitignore` 中、引用使用相对路径。

## 2. PR 1 — Group D：feature androidTest runner 基线

### 2.1 现象

全量 `connectedAndroidTest` 在 `:feature:home:connectedDebugAndroidTest` 崩溃：

```text
Test run failed to complete. Instrumentation run failed due to Process crashed.
Caused by: java.lang.ClassNotFoundException:
Didn't find class "androidx.test.runner.AndroidJUnitRunner" on path: DexPathList...
```

`feature/home/build.gradle.kts`、`feature/player-ui/build.gradle.kts`、`feature/search/build.gradle.kts`、`feature/settings/build.gradle.kts` 都声明：

```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

但这些 feature 模块当前没有对应 `androidTestImplementation(libs.androidx.test.runner)` 基础依赖。AGP 仍会为这些模块创建 connected task；即使模块没有真实 androidTest 源，test APK 也需要能实例化 runner。

### 2.2 改动清单

| 文件 | 操作 |
|---|---|
| `feature/home/build.gradle.kts` | 在 dependencies 中新增 `androidTestImplementation(libs.androidx.test.runner)`、`androidTestImplementation(libs.androidx.junit)`、`androidTestImplementation(libs.androidx.espresso.core)` |
| `feature/player-ui/build.gradle.kts` | 同上 |
| `feature/search/build.gradle.kts` | 同上 |
| `feature/settings/build.gradle.kts` | 同上 |

### 2.3 设计权衡

- **为何补依赖而不是禁用空模块 connected task**：禁用 task 会隐藏后续新增 androidTest 的入口，容易形成"测试源已加但 CI 没跑"的反向风险。补齐 runner 基线是最小、可持续的修复。
- **为何加 runner + junit + espresso 三件套**：`runner` 解决当前崩溃；`androidx.junit` 与 `espresso-core` 是本仓库其它 androidTest 模块的基础组合，后续 feature 模块新增 `AndroidJUnit4` 或基础 UI 断言时无需再次补依赖。
- **为何不在本轮加 Compose UI test androidTest 依赖**：当前 feature 模块没有 Compose androidTest 源，本轮目标是修全量 runner 崩溃，不提前扩依赖面。

## 3. PR 1 — Group C：`runTest` 迁移

### 3.1 改动清单

| 文件 | 操作 |
|---|---|
| `feature/settings/src/test/.../SettingsViewModelTest.kt` | 删 `@Ignore` 与上方 TODO；用例迁到 `runTest(mainDispatcherRule.dispatcher) { ... ; advanceUntilIdle() ; ... }` |
| `feature/settings/src/test/.../fileselector/FileSelectorLiteViewModelTest.kt` | 删 2 处 `@Ignore` 与上方 TODO；同样 `runTest` 化 2 个用例 |
| `feature/settings/src/test/.../MainDispatcherRule.kt` | 保留 |
| `feature/search/src/test/.../MainDispatcherRule.kt` | 保留 |

### 3.2 Idiom 转换

**之前（hang 模式）**：

```kotlin
@Test
fun `set storage directory persists selected tree uri`() = runBlocking {
    val appPreferences = createAppPreferences()
    val viewModel = createViewModel(appPreferences)
    val treeUri = "content://..."

    viewModel.setStorageDirectory(treeUri)

    assertEquals(treeUri, appPreferences.storageDirectoryUri.first { it == treeUri })
}
```

**之后（确定性）**：

```kotlin
@Test
fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
    val appPreferences = createAppPreferences()
    val viewModel = createViewModel(appPreferences)
    val treeUri = "content://..."

    viewModel.setStorageDirectory(treeUri)
    advanceUntilIdle()

    assertEquals(treeUri, appPreferences.storageDirectoryUri.first())
}
```

关键变化：

- `runBlocking` → `runTest(mainDispatcherRule.dispatcher)`：让 test scheduler 接管时间、协程调度。
- `advanceUntilIdle()`：显式排空 viewModelScope.launch 的副作用。
- `Flow.first { predicate }` → `Flow.first()`：去掉自旋 predicate；持久化已经完成，下一次发射就是目标值。

### 3.3 设计权衡

- **为何复用 `mainDispatcherRule.dispatcher` 而不让 `runTest` 创建新 dispatcher**：rule 已经把 `Dispatchers.Main` 设为某个 `TestDispatcher`，`viewModelScope.launch` 走的就是这个 dispatcher。如果 `runTest` 创建新 dispatcher，两者不同步、`advanceUntilIdle` 无法推进 viewModelScope 的协程。复用同一个 dispatcher 是 kotlinx.coroutines.test 的规范做法。
- **为何不顺手扫全仓库 `runBlocking` 单测**：YAGNI。其它 `runBlocking` 用例当前未暴露 hang 症状，盲目迁移=制造 diff 噪声。follow-up 触发条件见 §7。
- **为何不 dedup `MainDispatcherRule`**：跨模块复用 test 源集需要 `java-test-fixtures` 插件或抽共享 `:core:testing` 模块——两者均属 §7 out-of-scope。当前仅 1 处 duplicate，留待第 3+ 个共享 utility 出现时一并处理。

## 4. PR 1 — Group A：HomeFidelity 断言改写（β 路径）

### 4.1 设计前置

调研发现：

- `PlayerController` 是 `@Singleton class @Inject constructor(@ApplicationContext context: Context)`，**没有接口、没有 `@Binds`**；`PlayerModule` 是空 placeholder；`_playerState` 是 `private MutableStateFlow`，外部无法驱动。
- 全仓库 ~12 处 ViewModel 直接注入 `PlayerController` 具体类。
- `feature/player-ui/src/test/.../MiniPlayerContentTest.kt` 已用合成 `MiniPlayerUiModel` 覆盖 MiniPlayer composable 的 loaded-state 渲染、`PlayerState → MiniPlayerUiModel` 映射。

结论：originally-ignored 的 2 个用例**断言一个不存在的 UI 状态**（mock MiniPlayer 永远显示 + 硬编码 "In the End"/"Linkin Park"）；MiniPlayer-with-media 渲染信号已在单测层覆盖。改写断言至 cold launch 真实状态比建立 instrumented Hilt 注入基建更诚实、更便宜。

### 4.2 改动清单

| 文件 | 操作 |
|---|---|
| `app/src/androidTest/.../HomeFidelityHomeStructureTest.kt` | 删 2 处 `@Ignore` 与上方 TODO；改写第 1 个用例；删除第 2 个用例 |

### 4.3 用例 1：`home_exposes_root_nav_operations_sheets_and_drawer_opens_from_menu`

测试名建议改为 `home_cold_launch_exposes_navbar_operations_sheets_and_drawer`，反映其实际语义。

**保留**断言：

- `FidelityAnchors.Screen.HomeRoot`、`Home.NavBarRoot`、`Home.NavBarMenu`
- `Home.OperationsRoot`、`Home.SheetsRoot`
- 点击菜单后 `Home.DrawerRoot` 出现

**删除**断言：

- `Player.MiniRoot`、`Player.MiniPlayPause`、`Player.MiniQueue`（cold launch 时 MiniPlayer 因 `state.hasMedia == false` 早 return，节点不存在）

### 4.4 用例 2：`home_content_remains_visible_above_existingMiniPlayer`

**整体删除**。理由：

- 前提"cold launch 时 MiniPlayer 总是可见"已不成立。
- "Sheets root remains visible above mini player" 在无 mini player 时退化为"Sheets root visible"，与用例 1 重复。
- "In the End"/"Linkin Park" 文本断言依赖被移除的 mock，无可替代真值。
- MiniPlayer-with-media 渲染信号已由单测覆盖（见 §4.1）。

### 4.5 影响评估

- 用例**净减少 1 个**，删的是"测试无状态可达性"，真实信号未损失。
- `@Ignore` 在 `HomeFidelityHomeStructureTest.kt` 中归零。
- TestPlayerControllerModule、`@BindValue` Hilt 测试基建**不引入**——本次设计承认"我们暂时不需要它"，简化即收益。

## 4.6 PR 1 — Group E/F：全量 androidTest 资源与 player setUp 死锁

### 4.6.1 D8 OOM 根因与修复

在 feature runner baseline 修复后，full `connectedAndroidTest` 不再停在 `:feature:home`，而是继续执行到 `:plugin:mergeExtDexDebugAndroidTest` 并失败：

```text
> Task :plugin:mergeExtDexDebugAndroidTest FAILED
ERROR: D8: java.lang.OutOfMemoryError: Java heap space
```

同一命令临时加入：

```bash
./gradlew connectedAndroidTest --no-daemon -Dorg.gradle.jvmargs=-Xmx4096m
```

可越过该失败点，说明根因是当前 `gradle.properties` 中 `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8` 对本仓库 full androidTest dex 合并不足。修复应把项目基线改为：

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

不采用 task-level workaround 或要求开发者手动传 `-Dorg.gradle.jvmargs`：full test suite 是仓库级验收门，内存基线应写入仓库配置。

### 4.6.2 PlayerControllerTest setUp 死锁根因与修复

在 4 GiB heap 下继续 full `connectedAndroidTest` 后，`:player:connectedDebugAndroidTest` 卡在：

```text
Starting 15 tests on Pixel_10_Pro(AVD) - 17
Pixel_10_Pro(AVD) - 17 Tests 0/15 completed. (0 skipped) (0 failed)
```

单类复现：

```bash
./gradlew :player:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.player.controller.PlayerControllerTest
```

卡在 `Starting 9 tests ... Tests 0/9 completed`。对应测试代码：

```kotlin
@Before
fun setUp() {
    controller = PlayerController(context)
    runOnAppThread {
        kotlinx.coroutines.runBlocking {
            controller.connect()
        }
    }
}
```

`PlayerController.connect()` 内部会切到 `Dispatchers.Main.immediate` 并等待 `MediaController.Builder(...).buildAsync()`。测试把 `runBlocking` 放在主线程 executor 里，导致主线程被同步阻塞，MediaController async 连接无法完成；同时 `runOnAppThread` helper 使用无界 `latch.await()`，所以失败表现为永久挂起而不是可诊断失败。

修复原则：

- `controller.connect()` 在 instrumentation test 线程调用，并用 `runBlocking { withTimeout(5_000L) { ... } }` 约束等待时间。
- `runOnAppThread` helper 改为 bounded await，例如 5s；若主线程 block 抛异常，必须回传到当前测试线程并 fail。
- 仅修改测试代码；不改 production `PlayerController`，除非测试修复无法消除死锁。

建议形态：

```kotlin
@Before
fun setUp() = runBlocking {
    controller = PlayerController(context)
    withTimeout(5_000L) {
        controller.connect()
    }
}
```

并将 helper 改成：

```kotlin
private fun runOnAppThread(description: String = "main thread action", block: () -> Unit) {
    val latch = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    context.mainExecutor.execute {
        try {
            block()
        } catch (t: Throwable) {
            failure.set(t)
        } finally {
            latch.countDown()
        }
    }
    assertTrue("Timed out waiting for $description", latch.await(5, TimeUnit.SECONDS))
    failure.get()?.let { throw it }
}
```

## 5. PR 2 — Group B：`:plugin` 集成测试拆分

### 5.1 7 个用例分类

| # | 测试方法 | 网络? | 处置 |
|---|---|---|---|
| 1 | `localRuntimeShimPlugin_search_executesWithoutNotFunctionErrors` | ❌ | 进 `PluginRuntimeLocalIntegrationTest` |
| 2 | `yuanliWy_searchAndMediaSource_returnsPlayableUrl` | ✅ kstore.vip | 进 `PluginRuntimeNetworkIntegrationTest`，`@Assume` 门控 |
| 3 | `defaultSubscription_installAndWyPlaybackChain_succeeds` | ✅ kstore.vip | 同上 |
| 4 | `updatePlugin_thenSearchStillWorks_returnsPlayableResults` | ✅ kstore.vip | 同上 |
| 5 | `updatePlugin_afterSearchRegression_keepsSearchablePluginUsable` | ✅ kstore.vip | 同上 |
| 6 | `updatePlugin_withoutSource_returnsMissingSource_andKeepsPluginUsable` | ❌ | 进 `PluginRuntimeLocalIntegrationTest` |
| 7 | `updateAllPlugins_withoutSources_returnsFailureSummary` | ❌ | 进 `PluginRuntimeLocalIntegrationTest` |

### 5.2 改动清单

| 文件 | 操作 |
|---|---|
| `plugin/src/androidTest/.../PluginRuntimeIntegrationTest.kt` | **删除**（拆为下面 3 个文件） |
| `plugin/src/androidTest/.../PluginRuntimeLocalIntegrationTest.kt` | **新增**：3 个本地用例 + 共享的 `runtimeShimScript` + `clearPluginStorage()` 工具；类**不带** `@Ignore` |
| `plugin/src/androidTest/.../PluginRuntimeNetworkIntegrationTest.kt` | **新增**：4 个网络用例；类无 `@Ignore`，但 `@Before` 通过 `Assume.assumeTrue(...)` 在缺少 `-Pintegration` 时跳过 |
| `plugin/src/androidTest/.../PluginManagerHttpLifecycleTest.kt` | **新增**：使用 MockWebServer 覆盖 `installFromUrl + updatePlugin` 编排路径（详见 §5.4） |
| `plugin/build.gradle.kts` | 新增：`testInstrumentationRunnerArguments` 把 `-Pintegration` 转成 `pluginNetworkTests=true`；`androidTestImplementation` 加 `mockwebserver` |
| `gradle/libs.versions.toml` | 新增：`okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }` |

### 5.3 `-Pintegration` 门控机制

**`plugin/build.gradle.kts`**（增量片段）：

```kotlin
android {
    defaultConfig {
        testInstrumentationRunnerArguments["pluginNetworkTests"] =
            if (project.hasProperty("integration")) "true" else "false"
    }
}

dependencies {
    androidTestImplementation(libs.okhttp.mockwebserver)
}
```

**`PluginRuntimeNetworkIntegrationTest.kt`**（关键片段）：

```kotlin
@RunWith(AndroidJUnit4::class)
class PluginRuntimeNetworkIntegrationTest {
    @Before
    fun gateNetwork() {
        val arg = InstrumentationRegistry.getArguments().getString("pluginNetworkTests")
        Assume.assumeTrue(
            "Skipping plugin network integration tests; pass -Pintegration to enable.",
            arg == "true",
        )
    }
    // ... 4 个用例（从原 IntegrationTest 复制，去掉类级 @Ignore）
}
```

执行：

```bash
./gradlew :plugin:connectedAndroidTest                  # 默认：4 个网络用例 SKIPPED；本地 3 + MockWebServer 2 PASSED
./gradlew :plugin:connectedAndroidTest -Pintegration    # 全部 9 个用例 PASSED
```

`Assume.assumeTrue` 失败 = "ignored/skipped"，**不污染 `@Ignore` 计数**——与真正"坏掉的 ignore"区分开。

### 5.4 MockWebServer Lifecycle 用例（`PluginManagerHttpLifecycleTest`）

**目标**：覆盖"我们的代码"——`PluginManager.installFromUrl` 与 `updatePlugin` 的编排路径，无需触网、无需真实插件 JS 解析能力，CI 默认运行。

**用例（建议 2 条）**：

1. `installFromUrl_writesPluginAndLoadsMeta`：MockWebServer 返回 `runtimeShimScript`，`installFromUrl(url)` 应：插件文件落盘、`pluginMetaStore` 记录 sourceUrl、`plugins` StateFlow 包含新插件、`getPlugin(platform)` 可定位。
2. `updatePlugin_refetchesAndReplaces`：先 `installFromUrl` 拿到 v1 脚本；MockWebServer 切换到 v2（修改 platform 字段以外的内容如版本号 `1.0.0 → 1.0.1`）；`updatePlugin(platform)` 后磁盘内容更新、`plugins` StateFlow 单例不重复、版本号更新。

**不覆盖**：search/getMediaSource 的 JS 执行路径——本地 shim 用例已覆盖；JS 引擎本身的测试由 `:plugin/src/test/...` 单测层守护。

## 6. 验收闸门

### 6.1 PR 1 验收

```bash
./gradlew :feature:home:connectedDebugAndroidTest        # PASS（空/无测试模块不再 runner crash）
./gradlew :feature:player-ui:connectedDebugAndroidTest   # PASS（同上）
./gradlew :feature:search:connectedDebugAndroidTest      # PASS（同上）
./gradlew :feature:settings:connectedDebugAndroidTest    # PASS（同上）
./gradlew :player:connectedDebugAndroidTest              # PASS（不再停在 Tests 0/N completed）
./gradlew :feature:settings:testDebugUnitTest    # PASS（含 3 个新加用例）
./gradlew :feature:search:testDebugUnitTest      # PASS（回归不破）
./gradlew :app:testDebugUnitTest                 # PASS
./gradlew connectedAndroidTest --no-daemon       # PASS（HomeFidelity 用例 1 跑通；无 D8 OOM；无 player 0/N hang）
./gradlew :app:assembleDebug                     # 编译保护
```

PR 描述需明确："新增 feature androidTest runner 基线覆盖 4 个 feature 模块；Gradle daemon heap 提升到 4 GiB，full androidTest 不再在 `:plugin:mergeExtDexDebugAndroidTest` D8 OOM；`PlayerControllerTest` 不再在 setUp 停在 `Tests 0/N completed`；新启用测试用例 = 4 个（C: 3, A: 1）；删除的失效测试 = 1 个（HomeFidelity 用例 2）；`@Ignore` 净减少 = 5（5 个方法级，0 个类级——类级在 PR 2 处理）"。

要求至少跑两轮 `:feature:settings:testDebugUnitTest`（Robolectric/ByteBuddy 已被预热的复现条件），确保 hang 不再回归。

### 6.2 PR 2 验收

```bash
./gradlew :plugin:testDebugUnitTest                       # PASS
./gradlew :plugin:connectedAndroidTest                    # PASS（5 跑通，4 SKIPPED）
./gradlew :plugin:connectedAndroidTest -Pintegration      # PASS（9 全跑，需稳定网络 + kstore.vip 可达）
./gradlew connectedAndroidTest                            # 全仓库 PASS
./gradlew assembleDebug && ./gradlew lint                 # 整体编译/lint 保护
```

PR 描述需明确："拆分后 `@Ignore` 在 `:plugin/src/androidTest` 归零；新增 MockWebServer 用例 2 条；网络用例通过 `Assume` 门控（非 `@Ignore`），CI 默认跳过"。

### 6.3 终态指标

| 指标 | 现状 | 目标 |
|---|---|---|
| `@Ignore` 数（方法级） | 5 | 0 |
| `@Ignore` 数（类级） | 1 | 0 |
| MockWebServer 用例 | 0 | 2 |
| feature 模块 connected runner 崩溃 | `:feature:home` 复现 | 0 |
| full androidTest D8 OOM | `:plugin:mergeExtDexDebugAndroidTest` 复现（2 GiB heap） | 0 |
| Player instrumentation setUp hang | `PlayerControllerTest` 卡在 `Tests 0/9 completed` | 0 |
| Settings 模块 hang 复现率 | 不确定（与 JVM 预热顺序相关） | 0 |
| 网络依赖测试在 CI 默认通道的执行 | 类级 `@Ignore` 阻塞 7 个用例 | `Assume` 门控；CI 跳过 4 个真网络用例，跑 5 个 |

`@Ignore` 用 grep 验证：

```bash
grep -rn "@Ignore" --include="*.kt" 2>/dev/null | grep -v build/ | grep -v .worktrees/
# 期望：空输出
```

### 6.4 失败模式预案

| 风险 | 触发条件 | 处置 |
|---|---|---|
| `runTest` 迁移后仍 hang | 某个被忽略的协程依赖未被 `advanceUntilIdle()` 推进 | 改用 `runTest(mainDispatcherRule.dispatcher) { ... ; advanceUntilIdle() ; ... }` 显式包裹；如仍失败则在该用例转 `runBlocking(Dispatchers.Default)` 兜底（追加 TODO 注明 root cause 待查） |
| feature 模块补 runner 后仍崩溃 | 只补了部分 feature 模块，后续模块继续缺 runner | 按 §2.2 四个 feature 模块一次性补齐；逐个跑 focused `:feature:*:connectedDebugAndroidTest` 后再跑全量 |
| `:plugin:mergeExtDexDebugAndroidTest` D8 OOM | Gradle daemon heap 仍为 2 GiB 或 CI 覆盖了 `org.gradle.jvmargs` | 确认 `gradle.properties` 为 `-Xmx4096m`，并检查 CI 是否用命令行/环境覆盖；不要把 full suite 依赖隐藏在本地手动 `-Dorg.gradle.jvmargs` |
| `PlayerControllerTest` 停在 `Tests 0/N completed` | `@Before` 或 helper 仍在主线程执行无界 blocking wait | 禁止主线程 `runBlocking`；所有 `CountDownLatch.await()` 必须 bounded；连接用 `withTimeout` 让失败可诊断 |
| HomeFidelity 用例 1 仍失败 | 真实 cold launch 时某个 anchor（如 `OperationsRoot`）也是延迟挂载 | 调整 `waitUntil` timeout 或换更稳定的 anchor；不应回到 `@Ignore` |
| MockWebServer 用例不稳 | 端口冲突 / 服务未就绪 | 用 `mockWebServer.url("/wy.js")` 拿到带端口的 URL；`@Before` 中 `start()`，`@After` 中 `shutdown()` |
| `-Pintegration` 通道在真机失败 | kstore.vip 临时不可达或 wy.js 行为变更 | 不阻塞 PR——这是设计上"按需手动通道"，单独排查；可在 PR 描述里贴一次成功的 log 作为 baseline |
| Hilt singleton 状态在 androidTest 之间泄漏 | `@HiltAndroidTest` + 复用 application | β 方案不主动改 PlayerController 状态，无此风险 |

## 7. Out-of-scope / 观察记录（不在本 spec 执行）

避免 CLAUDE.md 警告的"伪 backlog"，下列项**仅作记录**，本 spec 不执行：

1. **`PlayerController` 接口化（γ 路径）**：当前 12+ 处 ViewModel 直接注入具体类，单测只能用 Mockito inline mock。如要为更多 androidTest 提供"deterministic player state"能力，应走接口 + `@Binds` + `@TestInstallIn(replaces = ...)`。建议作为独立 spec：标题 `player-controller-testability-design`，触发条件是"出现第 2 个需要在 androidTest 注入特定 player state 的需求"。
2. **共享 `:core:testing` 模块**：当前 `MainDispatcherRule` 在 `:feature:settings`、`:feature:search` 各一份；本 spec 故意只 dedup 不抽模块。若未来出现第 3+ 个共享 utility（如 fake AppPreferences、fake PluginManager），重新评估抽模块。
3. **`runTest` 全仓库扫描**：本 spec 只迁移 3 个明确 hang 的用例。仓库内其它 `runBlocking` 单测当前未暴露问题，盲扫一遍是制造 diff 噪声。建议触发条件是"再次出现一例 hang"。
4. **Compose UI test v2 迁移**：`connectedAndroidTest` 输出提示 `createAndroidComposeRule` / `createComposeRule` v1 已 deprecated，v2 使用 `StandardTestDispatcher`，可能暴露额外同步问题。本轮只记录警告，不迁移，避免把 runner 修复与调度语义迁移混在一起。
5. **`:core` 覆盖薄、`:feature:player-ui` 单测仅 2 个文件**：覆盖薄但非缺陷暴露面；不在本 spec 兜底。
6. **`PluginRuntimeNetworkIntegrationTest` 的 nightly CI 接入**：本 spec 不要求改 CI 配置——仅提供本地 `-Pintegration` 入口。后续若想 nightly 跑，由 CI 专项处理。
7. **Paparazzi snapshot pilot**（`feat/paparazzi-home-ui-snapshot` worktree）：与本 spec 完全正交、独立推进，本 spec 不动。

## 8. 文档治理

合并 PR 1 / PR 2 之前，需同步更新 [`docs/DOCS_STATUS.md`](../../DOCS_STATUS.md)：

- 在文档清单中新增本 spec 行，状态标记 `当前规范（Android 测试稳定性专项）`，可直接执行。
- spec 完成后（即 PR 1 + PR 2 全部合入），将本 spec 状态降为 `当前参考`，并在 `最后校验` 字段记录验收日期。
