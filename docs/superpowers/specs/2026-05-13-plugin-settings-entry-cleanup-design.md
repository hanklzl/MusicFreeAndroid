# 设计：去掉插件设置中间页 + 本地插件不入插件列表

> 文档状态：当前规范（精简重构）
> 适用范围：抽屉 → 插件管理导航、`SettingsScreen` 的 `Plugin` 分支、`PluginListViewModel.pluginItems` 数据流
> 直接执行：是
> 设计依据：RN 原版 `../MusicFree/src/pages/home/components/drawer/index.tsx`、`../MusicFree/src/pages/setting/settingTypes/pluginSetting/index.tsx`、`../MusicFree/src/core/pluginManager/index.ts`
> 关联 UI rules：[ui rules](../../dev-harness/ui/rules.md)
> 关联 plugin rules：[plugin rules](../../dev-harness/plugin/rules.md)

## 背景

当前 Android 实现存在两处与 RN 原版偏离：

1. **抽屉 → 插件设置走中间页**：抽屉点击"插件管理"会先跳到 `SettingsRoute(SettingsType.Plugin)`，该页面只渲染一个 `SettingsTypeEntryContent`（"插件管理 / 进入"卡片），需要再点一次才能进入真正的 `PluginListScreen`。
   - 现状代码：`feature/home/src/main/java/.../HomeScreen.kt:98` 把 `OpenPluginManagement` 映射到 `onNavigateToSettings(SettingsType.Plugin)`；`feature/settings/src/main/java/.../SettingsScreen.kt:133-141` 渲染入口卡。
   - RN 行为：抽屉 `navigateToSetting("plugin")` → `setting/index.tsx` 直接挂载 `pluginSetting/index.tsx` 的 `Stack.Navigator`，初始路由就是 `pluginList`，**没有中间页**。

2. **本地（built-in）插件出现在插件列表**：`PluginListViewModel.pluginItems`（`feature/settings/.../PluginListViewModel.kt:160-189`）由 `pluginManager.plugins.map { it.info }` 直出，未过滤 `LocalFilePluginConstants.PLATFORM`（即 "本地"）。`uiEntries`（同文件 line 122-135）已经过滤本地条目，`pluginItems` 这条流是漏网。
   - RN 行为：`pluginsAtom` 仅由真实安装插件填充；`localFilePlugin` 仅通过 `getByName("本地")` / `getByHash("local-plugin-hash")` 被业务侧按名取用，从不进入 `pluginsAtom`，因此 `useSortedPlugins()` 渲染的列表里**永远没有本地插件**。

## 目标

- 抽屉点击"插件管理"直达 `PluginListScreen`，与 RN 一致。
- 插件管理列表完全不展示内置"本地"插件（既不在 `pluginItems`，也不在 `uiEntries`；后者已合规）。
- 重构后 `SettingsType` 不再保留 `Plugin` 分支，避免悬空入口。
- 现有自动化测试改写到新预期，避免 CI 回归。

## 非目标

- 不动 `LocalFilePlugin` 自身的运行时行为（仍然在 `PluginManager` 内部用于解析本地媒体源）。
- 不动 "插件管理" 页面内部 UI（排序 / 订阅 / 安装等子页保持不变）。
- 不动 RN parity 之外的其他设置项（`Basic` / `Theme` / `Backup` / `About` 不变）。

## 改动面

### Phase 1 — 抽屉直达 PluginList

**核心改动**

1. `feature/home/src/main/java/.../HomeScreen.kt`
   - 新增构造参数 `onNavigateToPluginList: () -> Unit`。
   - `HomeDrawerAction.OpenPluginManagement` 分发到该新回调，而非 `onNavigateToSettings(SettingsType.Plugin)`。

2. `feature/home/src/main/java/.../navigation/HomeNavigation.kt`
   - `homeScreen(...)` extension 透传新参数 `onNavigateToPluginList`。

3. `app/src/main/java/.../navigation/AppNavHost.kt`
   - `homeScreen { ... }` 调用处增加 `onNavigateToPluginList = { navController.navigate(PluginListRoute) }`。

4. `core/src/main/java/.../core/navigation/Routes.kt`
   - 从 `SettingsType` 枚举中删除 `Plugin`。
   - `SettingsRoute` 的 `default = SettingsType.Basic` 保持不变。

5. `feature/settings/src/main/java/.../SettingsScreen.kt`
   - 删除 `SettingsType.Plugin` 分支（line 133-141）。
   - 删除 `private fun SettingsType.title()` 中的 `SettingsType.Plugin -> "插件管理"` 分支（line 263）。

6. `core/src/main/java/.../core/ui/FidelityAnchors.kt`
   - 删除 `Settings.PluginRoot`、`Settings.PluginManagementEntry` 两个常量（line 118、122）。

**测试改动**

7. `app/src/androidTest/java/.../HomeEntryNavigationTest.kt`
   - 重写 `settingsPluginEntry_*` 用例：抽屉 "插件管理" 点击后直接断言 `FidelityAnchors.Screen.PluginListRoot` 出现（不再走 `Settings.PluginRoot` / `Settings.PluginManagementEntry`）。

8. `feature/settings/src/test/java/.../SettingsScreenTest.kt`
   - 删除使用 `SettingsType.Plugin` 的测试用例。

9. `core/src/test/java/.../PluginSearchPlayAnchorContractTest.kt`
   - 移除对 `FidelityAnchors.Settings.PluginManagementEntry` 的引用。

10. `feature/home/src/test/java/.../HomeAnchorContractTest.kt`
    - 移除对 `FidelityAnchors.Settings.PluginManagementEntry` 的引用。

### Phase 2 — 本地插件不入 pluginItems

**核心改动**

1. `feature/settings/src/main/java/.../pluginlist/PluginListViewModel.kt`
   - 在 `pluginItems` 的 `allInfos` 上加 `.filter { it.platform != LocalFilePluginConstants.PLATFORM }`，与 `uiEntries`（line 133）一致。
   - 同时审查 `mediaSourcePlatforms`、`alternatives` 等派生集合是否需要把 "本地" 也排除（应当排除，否则下拉里仍可能选中 "本地"）。

**测试改动**

2. `feature/settings/src/test/java/.../pluginlist/PluginListViewModelTest.kt`
   - 新增 `pluginItems_excludesLocalBuiltInPlugin()`：当 `pluginManager.plugins` 同时包含一个 `本地` `LoadedPlugin` 和一个外部 JS 插件时，`pluginItems.value` 仅含外部插件，`uiEntries.value` 同步过滤。
   - 若现有 fixture 没有挂载本地条目，改造 fixture 让 `PluginManager` 启动后 `plugins` 流确实带本地条目（与 `PluginManager.buildLocalEntry` 实际运行行为一致）。

## 数据流影响

- `pluginManager.plugins` 与 `pluginManager.allEntries` **保持不变** — `PluginManager` 内部仍然需要 "本地" 用于解析本地媒体源（参见 `PluginManager.kt:562-584`）。
- 只在 ViewModel 层做投影过滤，保持 `:plugin` 模块与 `:feature:settings` 模块的关注分离。

## 验收

### 必须通过

- `./gradlew :app:assembleDebug` 通过。
- `./gradlew :feature:settings:testDebugUnitTest` 通过，包含新增 `pluginItems_excludesLocalBuiltInPlugin`。
- `./gradlew :feature:home:testDebugUnitTest` `:core:testDebugUnitTest` 通过。
- `./gradlew :app:lintDebug` 不引入新 warning（与基线一致）。
- 重写后的 `HomeEntryNavigationTest.settingsPluginEntry_*` 用例若有可用模拟器执行 connectedAndroidTest，必须通过；否则在 PR 描述中标注未执行原因。

### 行为校验（运行态）

- Debug APK 安装后：打开抽屉 → 点击"插件管理" → **直接进入 `PluginListScreen`**（与 RN 一致），系统返回键回到首页（而非回到一个空的 Settings 中间页）。
- 在 `PluginListScreen` 顶部列表中：**不可见"本地"卡片**；若开启 `lazyLoad` 也不影响。
- 进入 "插件替代源" 下拉：候选不包含 "本地"。
- 验证内部行为不退化：本地媒体源播放、本地音频导入、`getByName("本地")` 调用栈无 NPE / 空结果。

## 风险与回滚

| 风险 | 缓解 |
|------|------|
| 抽屉新回调遗漏在 PreviewParameter / 测试 fake 中 | 全工程 grep `HomeScreen(` / `homeScreen(` 调用点，逐个补齐 `onNavigateToPluginList` |
| 删除 `SettingsType.Plugin` 时还有未发现的引用 | 改动前 grep `SettingsType\.Plugin` 全仓库，确认仅剩本 spec 列出的 7 处 |
| `pluginItems` 过滤后某些 alt-platform 下拉显示 "本地" 为悬空选项 | 同步过滤 `alternatives` / `mediaSourcePlatforms`；测试覆盖一条 alt-source 用例 |

回滚策略：直接 revert squash commit；`PluginManager` 内部未改动，本地媒体源能力不受影响。

## 与 dev-harness 的关系

- 不新增也不修改任何 `docs/dev-harness/<area>/rules.md` 条款。
- `FidelityAnchors.Settings.PluginRoot / PluginManagementEntry` 是历史过渡期的"占位入口"锚点，本次随中间页一并退役；不影响 UI rules 中已登记的特殊 Chrome 页面。
