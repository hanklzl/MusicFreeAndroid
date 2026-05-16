# 搜索页冷启动插件加载实现计划

> **面向自动化执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐步执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪进度。

**目标：** 修复搜索页冷启动首次进入时把“插件尚未加载完成”误显示为“未安装插件”的问题，并保留插件加载完成前提交的待执行搜索。

**架构：** 修复范围限定在 `SearchViewModel`。搜索页改为从 `PluginManager.getSearchablePlugins()` 获取可搜索插件，新增本地插件初始加载完成状态，并把“加载中空列表”和“加载完成后确实为空”分开处理。

**技术栈：** Kotlin、AndroidX ViewModel、Kotlin Coroutines `StateFlow`、Hilt ViewModel、JUnit4、Mockito-Kotlin、Gradle Android 单元测试。

---

## 文件结构

- 修改 `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
  - 负责搜索页状态、插件选择和搜索派发。
  - 新增 `pluginsReady` 与待执行搜索处理。
  - 插件来源从原始 `pluginManager.plugins` 切换为 `pluginManager.getSearchablePlugins()`。
- 修改 `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`
  - 为 `PluginManager.getSearchablePlugins()` 增加独立的 `searchablePluginFlow` 测试桩。
  - 覆盖冷启动空列表、真实无插件、插件稍后到达、插件管理层排序/过滤来源、待执行搜索回放。

## 任务 1：补齐冷启动加载态测试

**文件：**
- 修改：`feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`

- [ ] **步骤 1：增加协程测试辅助与可搜索插件流测试桩**

更新 imports：

```kotlin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.mockito.kotlin.doSuspendableAnswer
```

更新测试字段与 `init`：

```kotlin
private val pluginFlow = MutableStateFlow<List<LoadedPlugin>>(emptyList())
private val searchablePluginFlow = MutableStateFlow<List<LoadedPlugin>>(emptyList())

init {
    whenever(pluginManager.plugins).thenReturn(pluginFlow)
    whenever(pluginManager.getSearchablePlugins()).thenReturn(searchablePluginFlow)
    whenever(pluginManager.getPlugin(any())).thenAnswer { invocation ->
        val platform = invocation.getArgument<String>(0)
        pluginFlow.value.find { it.info.platform == platform }
    }
    whenever(appPreferences.searchHistory).thenReturn(flowOf(emptyList()))
}
```

新增辅助函数：

```kotlin
private fun setLoadedPlugins(vararg plugins: LoadedPlugin) {
    val list = plugins.toList()
    pluginFlow.value = list
    searchablePluginFlow.value = list
}
```

- [ ] **步骤 2：增加“加载未完成时初始空列表保持编辑态”的失败测试**

新增测试：

```kotlin
@Test
fun `initial empty searchable plugin flow stays editing while plugins are loading`() = runTest {
    val loadGate = CompletableDeferred<Unit>()
    whenever(pluginManager.ensurePluginsLoaded()).doSuspendableAnswer {
        loadGate.await()
    }

    val viewModel = createViewModel()
    advanceUntilIdle()

    assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
    assertTrue(viewModel.searchablePlugins.value.isEmpty())
    assertNull(viewModel.selectedPlatform.value)
}
```

- [ ] **步骤 3：运行测试并确认当前实现失败**

运行：

```bash
./gradlew :feature:search:testDebugUnitTest --tests 'com.hank.musicfree.feature.search.SearchViewModelTest.initial empty searchable plugin flow stays editing while plugins are loading'
```

预期：失败。当前实现会把初始空插件列表转换为 `SearchPageStatus.NO_PLUGIN`。

- [ ] **步骤 4：更新真实无插件测试**

将现有 `no plugins transitions to NO_PLUGIN status` 测试替换为：

```kotlin
@Test
fun `empty searchable plugin flow transitions to NO_PLUGIN after plugin load completes`() = runTest {
    whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

    val viewModel = createViewModel()
    advanceUntilIdle()

    assertEquals(SearchPageStatus.NO_PLUGIN, viewModel.pageStatus.value)
    assertTrue(viewModel.searchablePlugins.value.isEmpty())
    assertNull(viewModel.selectedPlatform.value)
}
```

- [ ] **步骤 5：提交加载态回归测试**

运行：

```bash
git add feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt
git commit -m "test(search): cover cold-start plugin loading state"
```

预期：只提交测试文件。

## 任务 2：实现插件加载完成状态

**文件：**
- 修改：`feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- 测试：`feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`

- [ ] **步骤 1：增加加载完成状态与待执行搜索字段**

在 `pageStatus` 下方新增：

```kotlin
private var pluginsReady = false

private data class PendingSearch(
    val query: String,
    val mediaType: SearchMediaType,
)

private var pendingSearch: PendingSearch? = null
```

- [ ] **步骤 2：替换 `init` 中的原始插件收集逻辑**

将当前 `init` block 替换为：

```kotlin
init {
    viewModelScope.launch {
        pluginManager.getSearchablePlugins().collect { plugins ->
            handleSearchablePluginsChanged(plugins.map { it.info })
        }
    }
    viewModelScope.launch {
        runCatching {
            pluginManager.ensurePluginsLoaded()
        }.onFailure { e ->
            runCatching { Log.e(TAG, "Failed to load plugins", e) }
        }
        pluginsReady = true
        updatePageStatusForPluginAvailability()
        runPendingSearchIfPossible()
    }
}
```

- [ ] **步骤 3：增加插件可用性辅助函数**

在 `init` 下方新增：

```kotlin
private fun handleSearchablePluginsChanged(searchable: List<PluginInfo>) {
    _searchablePlugins.value = searchable

    val selected = _selectedPlatform.value
    if (selected == null && searchable.isNotEmpty()) {
        _selectedPlatform.value = searchable.first().platform
    } else if (selected != null && searchable.none { it.platform == selected }) {
        _selectedPlatform.value = searchable.firstOrNull()?.platform
    }

    updatePageStatusForPluginAvailability()
    runPendingSearchIfPossible()
}

private fun updatePageStatusForPluginAvailability() {
    val searchable = _searchablePlugins.value
    if (searchable.isNotEmpty()) {
        if (_pageStatus.value == SearchPageStatus.NO_PLUGIN) {
            _pageStatus.value = SearchPageStatus.EDITING
        }
        return
    }

    if (!pluginsReady) return

    if (_pageStatus.value != SearchPageStatus.RESULT) {
        _pageStatus.value = SearchPageStatus.NO_PLUGIN
    }
}

private fun runPendingSearchIfPossible() {
    val pending = pendingSearch ?: return
    if (_searchablePlugins.value.isEmpty()) return

    pendingSearch = null
    searchForMediaType(pending.query, pending.mediaType)
}
```

- [ ] **步骤 4：更新 `searchForMediaType` 以保留待执行搜索**

将 `searchForMediaType` 开头改为：

```kotlin
private fun searchForMediaType(query: String, mediaType: SearchMediaType) {
    val plugins = _searchablePlugins.value
    if (plugins.isEmpty()) {
        pendingSearch = PendingSearch(query, mediaType)
        updatePageStatusForPluginAvailability()
        return
    }

    pendingSearch = null
```

后续 loading map 初始化与逐插件搜索循环保持原逻辑。

- [ ] **步骤 5：运行 ViewModel 聚焦测试**

运行：

```bash
./gradlew :feature:search:testDebugUnitTest --tests 'com.hank.musicfree.feature.search.SearchViewModelTest'
```

预期：`SearchViewModelTest` 全部通过。

- [ ] **步骤 6：提交实现**

运行：

```bash
git add feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt
git commit -m "fix(search): wait for plugin load before no-plugin state"
```

预期：提交 ViewModel 与测试更新。

## 任务 3：补齐回归覆盖

**文件：**
- 修改：`feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`

- [ ] **步骤 1：更新现有测试的插件设置方式**

将直接赋值：

```kotlin
pluginFlow.value = listOf(plugin)
```

替换为：

```kotlin
setLoadedPlugins(plugin)
```

验证插件管理层过滤/排序的测试需要分别设置原始流与可搜索流：

```kotlin
pluginFlow.value = listOf(rawFirst, searchableSecond)
searchablePluginFlow.value = listOf(searchableSecond)
```

- [ ] **步骤 2：增加使用插件管理层可搜索流的测试**

新增测试：

```kotlin
@Test
fun `uses plugin manager searchable plugin flow for available plugins`() = runTest {
    whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

    val rawFirst = plugin(platform = "raw-first", supportedSearchType = listOf("music"))
    val searchableSecond = plugin(platform = "searchable-second", supportedSearchType = listOf("music"))
    pluginFlow.value = listOf(rawFirst, searchableSecond)
    searchablePluginFlow.value = listOf(searchableSecond)

    val viewModel = createViewModel()
    advanceUntilIdle()

    assertEquals(listOf(searchableSecond.info), viewModel.searchablePlugins.value)
    assertEquals("searchable-second", viewModel.selectedPlatform.value)
    assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
}
```

- [ ] **步骤 3：增加插件稍后到达时恢复编辑态的测试**

新增测试：

```kotlin
@Test
fun `searchable plugin arrival restores editing and selects first plugin`() = runTest {
    whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)

    val viewModel = createViewModel()
    advanceUntilIdle()

    assertEquals(SearchPageStatus.NO_PLUGIN, viewModel.pageStatus.value)

    val plugin = plugin(platform = "searchable", supportedSearchType = listOf("music"))
    setLoadedPlugins(plugin)
    advanceUntilIdle()

    assertEquals(SearchPageStatus.EDITING, viewModel.pageStatus.value)
    assertEquals(listOf(plugin.info), viewModel.searchablePlugins.value)
    assertEquals("searchable", viewModel.selectedPlatform.value)
}
```

- [ ] **步骤 4：增加待执行搜索回放测试**

新增测试：

```kotlin
@Test
fun `pending search runs when searchable plugins arrive after submit`() = runTest {
    whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
    whenever(appPreferences.addSearchQuery(any())).thenReturn(Unit)

    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.searchAll("hello")
    assertEquals(SearchPageStatus.SEARCHING, viewModel.pageStatus.value)

    val plugin = plugin(
        platform = "searchable",
        supportedSearchType = listOf("music"),
        firstPage = searchResult(
            data = listOf(musicItem("1", "Song 1")),
            isEnd = true,
        ),
    )
    setLoadedPlugins(plugin)
    advanceUntilIdle()

    assertEquals(SearchPageStatus.RESULT, viewModel.pageStatus.value)
    val state = viewModel.searchResults.value[SearchMediaType.MUSIC]?.get("searchable")
    assertTrue("Expected Success but was $state", state is PluginSearchState.Success)
    assertEquals(1, (state as PluginSearchState.Success).items.size)
}
```

- [ ] **步骤 5：运行搜索模块单元测试**

运行：

```bash
./gradlew :feature:search:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 6：提交回归覆盖**

运行：

```bash
git add feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt
git commit -m "test(search): cover plugin load replay edge cases"
```

预期：如果任务 2 后测试文件仍有新增改动，则提交成功。

## 任务 4：最终验证

**文件：**
- 验证：`feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- 验证：`feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`

- [ ] **步骤 1：运行聚焦单元测试**

运行：

```bash
./gradlew :feature:search:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 2：运行 app 构建**

运行：

```bash
./gradlew :app:build
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 3：复查 git diff**

运行：

```bash
git status --short
git diff --stat HEAD
git log --oneline --max-count=5
```

预期：`fix-search-cold-start-plugin-load` 分支只包含本任务相关提交；最终提交后工作区干净。

- [ ] **步骤 4：记录手动运行态验收**

设备或模拟器验收步骤：

```text
1. 先安装至少一个插件。
2. 强制停止应用。
3. 冷启动应用。
4. 首次点击首页搜索入口。
5. 确认搜索页显示编辑态/搜索历史，不显示“请先在设置中安装插件”。
6. 提交一个关键词，确认结果可加载。
```

如果本会话没有可用设备或模拟器，最终说明该运行态验收未执行，并以单元测试和构建验证作为已完成证据。

## 自检

- 规格覆盖：本计划覆盖冷启动误判、真实无插件、插件稍后到达、插件管理层排序/过滤来源、待执行搜索回放和最终验证。
- 占位扫描：未发现占位标记或开放式“补测试”步骤。
- 类型一致性：`pluginsReady`、`PendingSearch`、`handleSearchablePluginsChanged`、`updatePageStatusForPluginAvailability`、`runPendingSearchIfPossible` 均先定义后使用，命名一致。
