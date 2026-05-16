# 搜索页冷启动插件加载状态修复设计

> 文档状态：当前参考
> 适用范围：搜索页冷启动首次进入时，插件列表尚未加载完成导致误显示无插件提示的问题。
> 直接执行：否
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 备注：本文档记录修复设计，具体执行以随后生成的实现计划为准。
> 最后校验：2026-05-03

## 背景

用户反馈：冷启动后首次点击首页搜索入口，搜索页展示“请先在设置中安装插件”；点击返回后再次进入搜索页，页面正常。

该现象说明应用本地已经存在插件，问题不是插件缺失，而是搜索页把“插件尚未从磁盘加载完成”的中间态误判为“没有可搜索插件”。

## 代码事实

Android 当前相关实现：

- `../../../plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- `../../../feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- `../../../feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
- `../../../feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`

RN 参考：

- `../../../../MusicFree/src/entry/bootstrap/bootstrap.ts`
- `../../../../MusicFree/src/pages/searchPage/hooks/useSearch.ts`
- `../../../../MusicFree/src/pages/searchPage/store/atoms.ts`

`PluginManager.plugins` 是 `StateFlow<List<LoadedPlugin>>`，初始值为空列表。`SearchViewModel` 初始化时先 collect `pluginManager.plugins`，收到初始空列表后会把 `_pageStatus` 设置为 `SearchPageStatus.NO_PLUGIN`。同一个 `init` 中另一个协程随后调用 `pluginManager.ensurePluginsLoaded()`，磁盘插件加载完成后会更新插件列表，但当前代码不会把已经进入 `NO_PLUGIN` 的搜索页恢复到编辑态。

RN 版启动流程会先 `await PluginManager.setup()` 再进入正常页面生命周期，因此搜索页不需要处理“插件管理器尚未完成初始加载”的空列表。

## 目标

1. 冷启动首次进入搜索页时，不再把插件初始空列表误判为没有安装插件。
2. 页面默认保持搜索编辑态和搜索历史，后台完成插件加载。
3. 只有插件加载完成后仍没有可搜索插件时，才展示无插件提示。
4. 插件加载完成后如果存在可搜索插件，应自动选择第一个可用插件，并保持或恢复搜索编辑态。
5. 搜索页插件列表应尊重插件启用状态和用户排序。

## 非目标

- 不改首页搜索入口的导航行为。
- 不把插件加载整体前置到 `MainActivity` 冷启动流程。
- 不调整搜索结果 UI、播放解析或插件安装流程。
- 不引入新的全局 loading 页面。

## 方案选择

采用搜索页局部状态修复。

`SearchViewModel` 增加“插件初始加载是否完成”的本地状态。插件列表 collect 到空列表时，如果初始加载尚未完成，页面保持 `EDITING`。`ensurePluginsLoaded()` 返回后，再根据当前可搜索插件列表决定是否进入 `NO_PLUGIN`。

同时将搜索页可搜索插件来源从直接 collect `pluginManager.plugins` 调整为 `pluginManager.getSearchablePlugins()`。这样搜索页天然复用插件管理层的启用/禁用和排序逻辑，避免搜索页自行过滤原始插件列表时绕过用户设置。

备选方案评估：

- 在 `MainActivity` 启动阶段预加载插件：更接近 RN，但会增加冷启动路径负担，且各业务页仍需要防御初始空列表。
- 搜索入口点击前等待插件加载完成：只能覆盖首页入口，无法覆盖深链或后续其他搜索入口，且会把插件生命周期逻辑散到导航层。

## 状态流设计

搜索页初始化流程：

1. `pageStatus` 初始为 `EDITING`。
2. `SearchViewModel` 收集 `pluginManager.getSearchablePlugins()`。
3. `pluginsReady=false` 且插件列表为空时，不切换到 `NO_PLUGIN`。
4. `ensurePluginsLoaded()` 完成后设置 `pluginsReady=true`。
5. 若可搜索插件为空，且当前没有正在搜索，页面进入 `NO_PLUGIN`。
6. 若可搜索插件非空：
   - `searchablePlugins` 更新为 `PluginInfo` 列表。
   - `selectedPlatform` 为空时选择第一个插件。
   - 若当前页面处于 `NO_PLUGIN`，恢复到 `EDITING`。

搜索行为：

1. 用户提交非空 query 后，`searchAll()` 将页面置为 `SEARCHING`。
2. 如果此时可搜索插件仍为空，等待加载态不应立刻覆盖为 `NO_PLUGIN`。
3. 初始加载完成后仍为空，才切换到 `NO_PLUGIN`。
4. 初始加载完成后存在插件，则按当前 query 对已选择媒体类型发起搜索。

第 4 点是为了避免用户在插件加载完成前立即按搜索时，query 被记录但没有实际搜索。本轮实现必须在 `searchAll()` 中保留待执行 query，并在插件列表变为非空后补发搜索，避免同一类加载竞态在“快速输入并搜索”路径残留。

## 错误处理

- `ensurePluginsLoaded()` 正常完成但无可搜索插件：展示现有无插件提示。
- `ensurePluginsLoaded()` 抛异常：页面进入 `NO_PLUGIN` 或错误提示之间，本轮选择复用 `NO_PLUGIN`，同时记录日志；当前 UI 没有搜索页级错误态，避免扩大范围。
- 单个插件加载失败：由 `PluginManager.loadAllPlugins()` 继续跳过失败插件；只要仍有可搜索插件，搜索页可继续使用。

## 测试设计

更新 `SearchViewModelTest`：

1. 初始插件流为空且 `ensurePluginsLoaded()` 尚未完成时，`pageStatus` 保持 `EDITING`。
2. `ensurePluginsLoaded()` 完成后仍无插件时，`pageStatus` 进入 `NO_PLUGIN`。
3. 初始为空，加载完成后出现可搜索插件时，`pageStatus` 保持或恢复 `EDITING`，并自动选中第一个插件。
4. 可搜索插件列表使用插件管理层排序/启用过滤后的结果。
5. 快速提交搜索但插件稍后才加载完成时，加载完成后会发起搜索并进入 `RESULT`。

运行验证：

- `./gradlew :feature:search:testDebugUnitTest`
- 必要时补跑 `./gradlew :app:build`

运行态验收：

1. 设备或模拟器中先安装至少一个插件。
2. 强制停止应用后冷启动。
3. 首次点击首页搜索入口。
4. 预期：搜索页直接显示编辑态/搜索历史，不显示“请先在设置中安装插件”。
5. 输入关键词并搜索，预期可进入结果页。
