# 实施计划：去掉插件设置中间页 + 本地插件不入插件列表

> 关联设计：[2026-05-13-plugin-settings-entry-cleanup-design.md](../specs/2026-05-13-plugin-settings-entry-cleanup-design.md)
> 工作分支：`refactor/plugin-settings-entry-cleanup` （worktree：`.worktrees/refactor-plugin-settings-entry-cleanup`）
> 协作模式：subagent-driven-development（Phase 1 与 Phase 2 之间存在测试 fixture 依赖，**不能并行**；按阶段串行 dispatch）

---

## Phase 1 — 抽屉直达 PluginList

### 文件清单与编辑指令

**P1-A. `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`**

- `enum class SettingsType`（line 84-90）删除 `Plugin,`。
- 不动 `SettingsRoute`。

**P1-B. `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`**

- 删除 `Settings.PluginRoot`（line 118）。
- 删除 `Settings.PluginManagementEntry`（line 122）。
- 不要在 `FidelityAnchors.Home` 删除已有 `DrawerSettingsPlugin`（抽屉条目本身仍存在）。

**P1-C. `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`**

- 删除 `when (type) { ... SettingsType.Plugin -> SettingsTypeEntryContent(...) }` 整个 case（line 133-141）。
- 删除 `private fun SettingsType.title()` 中 `SettingsType.Plugin -> "插件管理"` 行（line 263）。

**P1-D. `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt`**

- 在 `@Composable fun HomeScreen(...)` 参数表里新增 `onNavigateToPluginList: () -> Unit`（紧邻 `onNavigateToSettings` 之后）。
- 修改 line 98：
  ```kotlin
  HomeDrawerAction.OpenPluginManagement -> onNavigateToPluginList()
  ```
- grep `HomeScreen(` 其他调用点（Preview、Test），全部补齐新参数。

**P1-E. `feature/home/src/main/java/com/hank/musicfree/feature/home/navigation/HomeNavigation.kt`**

- `fun NavGraphBuilder.homeScreen(...)` 参数表新增 `onNavigateToPluginList: () -> Unit`。
- 在 composable 调用 `HomeScreen(...)` 处透传。

**P1-F. `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`**

- `homeScreen(...)` 调用块内新增：
  ```kotlin
  onNavigateToPluginList = { navController.navigate(PluginListRoute) },
  ```

### 测试改动

**P1-T1. `app/src/androidTest/java/com/hank/musicfree/HomeEntryNavigationTest.kt`**

- 重写 `settingsPluginEntry_exposesSettingsPluginAnchor` → 改名 `settingsPluginEntry_opensPluginListRoot`：
  ```kotlin
  @Test
  fun settingsPluginEntry_opensPluginListRoot() {
      openDrawerDestination(FidelityAnchors.Home.DrawerSettingsPlugin)
      assertTagExists(FidelityAnchors.Screen.PluginListRoot)
  }
  ```
- 不再断言 `Screen.SettingsRoot` 或 `Settings.PluginRoot`、`Settings.PluginManagementEntry`。

**P1-T2. `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsScreenTest.kt`**

- 删除 line 60-70 附近使用 `SettingsType.Plugin` 的整个 `@Test` 函数。

**P1-T3. `core/src/test/java/com/hank/musicfree/core/ui/PluginSearchPlayAnchorContractTest.kt`**

- 从断言列表中移除 `FidelityAnchors.Settings.PluginManagementEntry`。

**P1-T4. `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt`**

- 从断言列表中移除 `FidelityAnchors.Settings.PluginManagementEntry`。

### Phase 1 验收

- `./gradlew :core:testDebugUnitTest :feature:home:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDebug` 全部通过。
- 全工程 `grep -rE "SettingsType\.Plugin|Settings\.PluginRoot|Settings\.PluginManagementEntry"` 应当**零命中**（仅文档可保留历史描述）。

---

## Phase 2 — 本地插件不入 pluginItems

### 文件清单与编辑指令

**P2-A. `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt`**

定位在 `val pluginItems: StateFlow<List<PluginUiItem>> = combine(...)`（line 160-189）。

修改：

```kotlin
val pluginItems: StateFlow<List<PluginUiItem>> = combine(
    pluginManager.plugins.map { list ->
        list.map { it.info }
            .filter { it.platform != LocalFilePluginConstants.PLATFORM }
    },
    metaStore.disabledPlugins,
    metaStore.pluginOrder,
    metaStore.alternativePlugins,
) { allInfos, disabled, order, alternatives ->
    val mediaSourcePlatforms = allInfos
        .filter { it.platform !in disabled && "getMediaSource" in it.supportedMethods }
        .map { it.platform }
        .toSet()
    // ... 其余不变
}
```

`mediaSourcePlatforms` 已经基于过滤后的 `allInfos` 派生，自动不含 "本地"。`alternatives` 是 `Map<String, String>`，键是源平台，值是目标平台；过滤后即便 meta store 仍含历史的 `源 → 本地` 配置，`alternativeInvalid` 也会自然变 true（因为目标平台不在 `mediaSourcePlatforms`），UI 已能正确提示。

### 测试改动

**P2-T1. `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModelTest.kt`**

- 先 `Read` 文件，确认 fixture（fake `PluginManager`）当前 `plugins` 流如何构造。
- 新增测试用例：
  ```kotlin
  @Test
  fun pluginItems_excludesLocalBuiltInPlugin() = runTest {
      // 在 fake plugins flow 里同时放一个 LocalFilePluginConstants.PLATFORM
      // 的 PluginInfo + 一个外部插件 "source"。
      val collectJob = launch { fixture.viewModel.pluginItems.collect() }
      runCurrent()

      val items = fixture.viewModel.pluginItems.value
      assertEquals(1, items.size)
      assertEquals("source", items.single().info.platform)
      collectJob.cancel()
  }
  ```
- 若 fake `PluginManager` 不允许直接注入 "本地" `PluginInfo`，则在 fixture 暴露一个 helper（例如 `emitPlugins(...)`），同步更新所有现有用例的构造路径。

### Phase 2 验收

- `./gradlew :feature:settings:testDebugUnitTest` 通过，包含新增用例。
- `./gradlew :app:assembleDebug` 通过。

---

## 跨阶段验收

- `./gradlew :app:assembleDebug` 通过。
- `./gradlew test`（聚合命令）通过。
- 如果有可用设备 / 模拟器：手动校验抽屉 "插件管理" 直达 PluginList，且 PluginList 不显示 "本地"。
- 全工程 grep 校验列表：
  - `SettingsType\.Plugin` → 0 hits
  - `Settings\.PluginRoot` → 0 hits
  - `Settings\.PluginManagementEntry` → 0 hits
  - `LocalFilePluginConstants\.PLATFORM` 在 `PluginListViewModel.kt` 中应当出现 **2 次以上**（`uiEntries`、`pluginItems` 各一次）。

## Subagent dispatch 策略

- Phase 1 → 单个 subagent，工作目录固定 worktree 根，需要编辑列出的全部文件并跑 Phase 1 验收命令。
- Phase 2 → 等 Phase 1 commit 后再 dispatch，避免 fixture 改写撞车。
- 两个 phase 结束后由主 agent 跑跨阶段验收；若有失败用例，重新 dispatch fix subagent。
