# 歌单导入功能实施计划

> **面向 agent worker：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务逐步执行本计划。步骤使用复选框（`- [ ]`）跟踪。

**目标：** 在首页“我的歌单”区域实现对齐 RN 原版的“导入歌单”链路：选择支持插件、输入链接、解析确认、批量添加到目标歌单并跳过重复歌曲。

**架构：** 插件模块负责公开 `importMusicSheet` 能力和解析兜底平台；`feature:home` 新增独立 `PlaylistImportViewModel` 管理导入 flow；`core.ui` 的添加到歌单状态扩展为批量歌曲；`data` 的批量写入改为 Room 事务。首页不新增路由，仍通过底部面板和对话框完成交互。

**技术栈：** Kotlin、Coroutines / Flow、Hilt ViewModel、Jetpack Compose Material3、Room、QuickJS 插件桥、JUnit / Mockito / Robolectric / Compose UI 测试。

---

## 工作区

- 分支：`feat/playlist-import`
- 工作树：`.worktrees/playlist-import`
- 当前规范：[歌单导入功能设计](../specs/2026-05-04-playlist-import-design.md)
- 相关规范：[用户歌单功能设计](../specs/2026-05-04-playlist-feature-design.md)、[UI Harness Rules](../../ui-harness/screen-chrome-rules.md)

## 文件结构

- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginInfo.kt`
  - 为插件元信息增加 `supportedMethods`。
- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
  - 加载插件时检测核心方法集合。
- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
  - `importMusicSheet` 调用 `JsBridge` 时传入当前插件平台。
- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
  - `parseImportMusicSheetResult` 支持 fallback platform。
- 修改： `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt`
  - 覆盖导入歌单解析的平台兜底。
- 修改： `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistSheetState.kt`
  - 单曲 pending 扩展为批量 pending。
- 修改： `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
  - 使用新的批量状态 helper，保持单曲行为。
- 修改： `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
  - 使用新的批量状态 helper，保持单曲行为。
- 修改： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt`
  - 使用新的批量状态 helper，保持单曲行为。
- 修改： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
  - 使用新的批量状态 helper，保持单曲行为。
- 修改： `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelTest.kt`
  - 覆盖 `pendingItems`。
- 修改： `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`
  - 注入 `AppDatabase`，批量写入使用 `withTransaction`。
- 修改： `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt`
  - 覆盖批量导入新增数、重复跳过、顺序保持。
- 创建： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportModels.kt`
  - 导入 UI 状态、插件 UI 模型、事件模型。
- 创建： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportViewModel.kt`
  - 首页导入 flow 状态机。
- 创建： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportHost.kt`
  - Compose Host：插件列表底部面板、输入框、解析中、确认、添加到歌单面板。
- 修改： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
  - 接入 `PlaylistImportViewModel` 和 `PlaylistImportHost`。
- 创建： `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportViewModelTest.kt`
  - 覆盖导入状态机。
- 创建： `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportHostTest.kt`
  - 覆盖导入 UI 关键节点。
- 修改： `docs/DOCS_STATUS.md`
  - plan 完成后确认 spec 已登记；本 plan 不需要登记为当前规范。

---

### Task 1: 插件导入解析和能力识别

**文件：**
- 修改： `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt`
- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginInfo.kt`
- 修改： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`

- [ ] **Step 1: 写失败测试，覆盖导入歌单平台兜底**

在 `JsBridgeTest` 末尾追加：

```kotlin
@Test
fun `parseImportMusicSheetResult backfills blank platforms from loaded plugin`() {
    val payload = listOf(
        mapOf("id" to "m1", "platform" to "", "title" to "Song A", "artist" to "A"),
        mapOf("id" to "m2", "title" to "Song B", "artist" to "B"),
    )

    val result = JsBridge.parseImportMusicSheetResult(payload, fallbackPlatform = "demo")

    assertEquals(listOf("demo", "demo"), result.map { it.platform })
}

@Test
fun `parseImportMusicSheetResult keeps explicit non blank platform`() {
    val payload = listOf(
        mapOf("id" to "m1", "platform" to "explicit", "title" to "Song A", "artist" to "A"),
    )

    val result = JsBridge.parseImportMusicSheetResult(payload, fallbackPlatform = "demo")

    assertEquals("explicit", result.single().platform)
}
```

- [ ] **Step 2: 运行测试，确认失败**

运行：

```bash
./gradlew :plugin:testDebugUnitTest --tests com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest
```

预期：失败，错误为 `parseImportMusicSheetResult` 没有 `fallbackPlatform` 参数。

- [ ] **Step 3: 实现 `JsBridge` 平台兜底**

修改 `JsBridge.parseImportMusicSheetResult`：

```kotlin
fun parseImportMusicSheetResult(
    payload: Any?,
    fallbackPlatform: String? = null,
): List<MusicItem> {
    val list = payload as? List<*> ?: return emptyList()
    return list.mapNotNull { entry ->
        (entry as? Map<String, Any?>)?.let { toMusicItem(it, fallbackPlatform = fallbackPlatform) }
    }
}
```

修改 `LoadedPlugin.importMusicSheet` 内的解析调用：

```kotlin
JsBridge.parseImportMusicSheetResult(result, fallbackPlatform = info.platform)
```

- [ ] **Step 4: 增加插件能力集合**

修改 `PluginInfo`：

```kotlin
data class PluginInfo(
    val platform: String,
    val version: String?,
    val author: String?,
    val description: String?,
    val srcUrl: String?,
    val supportedSearchType: List<String>,
    val appVersion: String? = null,
    val primaryKey: String? = null,
    val defaultSearchType: String? = null,
    val cacheControl: String? = null,
    val hints: Map<String, List<String>>? = null,
    val supportedMethods: Set<String> = emptySet(),
)
```

在 `PluginManager` companion object 中加入核心方法集合：

```kotlin
private val CORE_PLUGIN_METHODS = setOf(
    "search",
    "getMediaSource",
    "getMusicInfo",
    "getLyric",
    "getAlbumInfo",
    "getArtistWorks",
    "importMusicSheet",
    "importMusicItem",
    "getTopLists",
    "getTopListDetail",
    "getMusicSheetInfo",
    "getRecommendSheetTags",
    "getRecommendSheetsByTag",
    "getMusicComments",
)
```

在 `extractPluginInfo(engine)` 内创建方法检测：

```kotlin
suspend fun supportedMethods(): Set<String> {
    return CORE_PLUGIN_METHODS.filter { method ->
        try {
            engine.evaluate<Boolean>("typeof __plugin.$method === 'function'")
        } catch (_: Exception) {
            false
        }
    }.toSet()
}
```

在 `PluginInfo(...)` 构造中加入：

```kotlin
supportedMethods = supportedMethods(),
```

- [ ] **Step 5: 运行插件测试**

运行：

```bash
./gradlew :plugin:testDebugUnitTest --tests com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest
```

预期：通过。

- [ ] **Step 6: 提交**

```bash
git add plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginInfo.kt plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt
git commit -m "feat(plugin): expose import sheet capability"
```

---

### Task 2: 添加到歌单状态支持批量歌曲

**文件：**
- 修改： `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistSheetState.kt`
- 修改： `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
- 修改： `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- 修改： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt`
- 修改： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
- 测试： `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelTest.kt`

- [ ] **Step 1: 写失败测试，断言单曲入口写入 `pendingItems`**

在 `PlayerViewModelTest` 的 `showAddToPlaylistSheet sets visible with current item` 测试中，把断言改成：

```kotlin
val s = viewModel.sheetState.value
assertTrue(s.visible)
assertEquals(item, s.pendingItem)
assertEquals(listOf(item), s.pendingItems)
```

- [ ] **Step 2: 运行测试，确认失败**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModelTest
```

预期：失败，错误为 `pendingItems` 未定义。

- [ ] **Step 3: 扩展 `AddToPlaylistSheetState`**

替换 `AddToPlaylistSheetState.kt`：

```kotlin
package com.zili.android.musicfreeandroid.core.ui

import com.zili.android.musicfreeandroid.core.model.MusicItem

/**
 * UI state for the "Add to playlist" bottom sheet, shared by all surfaces that
 * trigger it. Single-song callers use [single]; playlist import uses [batch].
 */
data class AddToPlaylistSheetState(
    val visible: Boolean = false,
    val pendingItems: List<MusicItem> = emptyList(),
) {
    val pendingItem: MusicItem?
        get() = pendingItems.singleOrNull()

    companion object {
        fun single(item: MusicItem): AddToPlaylistSheetState =
            AddToPlaylistSheetState(visible = true, pendingItems = listOf(item))

        fun batch(items: List<MusicItem>): AddToPlaylistSheetState =
            AddToPlaylistSheetState(visible = items.isNotEmpty(), pendingItems = items)
    }
}
```

- [ ] **Step 4: 更新现有单曲调用点**

把以下文件中的 `AddToPlaylistSheetState(visible = true, pendingItem = item)` 改为 `AddToPlaylistSheetState.single(item)`：

```text
feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt
feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt
feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt
feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt
```

现有 `val item = _sheetState.value.pendingItem ?: return` 保持不变，因为兼容 getter 已保留。

- [ ] **Step 5: 运行相关测试**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModelTest
./gradlew :feature:search:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.search.SearchViewModelTest
```

预期：通过。

- [ ] **Step 6: 提交**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistSheetState.kt feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelTest.kt
git commit -m "feat(core): support batch add-to-playlist state"
```

---

### Task 3: 批量导入写入改为事务并返回新增数量

**文件：**
- 修改： `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`
- 修改： `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt`

- [ ] **Step 1: 写失败测试，覆盖新增数和重复跳过**

在 `PlaylistRepositoryTest` 中追加：

```kotlin
@Test
fun addMusicsToPlaylist_returnsAddedCountAndSkipsDuplicates() = runBlocking {
    val id = UUID.randomUUID().toString()
    playlistRepo.createPlaylist(Playlist(id = id, name = "Imported", coverUri = null))
    val items = listOf(
        sampleMusic("m1", title = "One"),
        sampleMusic("m2", title = "Two"),
        sampleMusic("m1", title = "One Again"),
    )

    val first = playlistRepo.addMusicsToPlaylist(id, items)
    val second = playlistRepo.addMusicsToPlaylist(id, items)

    assertEquals(2, first)
    assertEquals(0, second)
    assertEquals(2, playlistRepo.countMusicInPlaylist(id))
}

@Test
fun addMusicsToPlaylist_preservesImportOrderForManualSort() = runBlocking {
    val id = UUID.randomUUID().toString()
    playlistRepo.createPlaylist(Playlist(id = id, name = "Imported", coverUri = null))
    val items = listOf(
        sampleMusic("m3", title = "Third"),
        sampleMusic("m1", title = "First"),
        sampleMusic("m2", title = "Second"),
    )

    playlistRepo.addMusicsToPlaylist(id, items)

    val titles = playlistRepo.observeMusicInPlaylist(id).first().map { it.title }
    assertEquals(listOf("Third", "First", "Second"), titles)
}
```

- [ ] **Step 2: 运行测试，确认现有实现对重复输入列表返回值不符合期望时失败**

运行：

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryTest
```

预期：当前实现可能在无设备环境无法运行；有设备时应至少暴露批量事务和重复列表统计的实现差距。若无设备，继续实现后在最终验收记录“未运行 connected 测试：无设备”。

- [ ] **Step 3: 为 Repository 注入数据库**

修改 `PlaylistRepository` 构造函数：

```kotlin
class PlaylistRepository @Inject constructor(
    private val db: AppDatabase,
    private val playlistDao: PlaylistDao,
    private val musicDao: MusicDao,
    private val coverStore: PlaylistCoverStore,
    private val converters: Converters,
)
```

新增 import：

```kotlin
import androidx.room.withTransaction
import com.zili.android.musicfreeandroid.data.db.AppDatabase
```

在 `PlaylistRepositoryTest.setup()` 中更新构造：

```kotlin
playlistRepo = PlaylistRepository(db, db.playlistDao(), db.musicDao(), coverStore, converters)
```

- [ ] **Step 4: 抽出内部单曲写入并让批量走事务**

在 `PlaylistRepository` 中替换 `addMusicToPlaylist` 和 `addMusicsToPlaylist`：

```kotlin
suspend fun addMusicToPlaylist(playlistId: String, item: MusicItem): Boolean =
    addMusicToPlaylistInternal(playlistId, item)

suspend fun addMusicsToPlaylist(playlistId: String, items: List<MusicItem>): Int {
    if (items.isEmpty()) return 0
    return db.withTransaction {
        var addedCount = 0
        for (item in items) {
            if (addMusicToPlaylistInternal(playlistId, item)) {
                addedCount++
            }
        }
        addedCount
    }
}

private suspend fun addMusicToPlaylistInternal(playlistId: String, item: MusicItem): Boolean {
    musicDao.upsert(item.toEntity(converters))
    val nextOrder = playlistDao.maxSortOrderInPlaylist(playlistId) + 1
    val now = System.currentTimeMillis()
    val rowId = playlistDao.insertCrossRefIgnore(
        PlaylistMusicCrossRef(
            playlistId = playlistId,
            musicId = item.id,
            musicPlatform = item.platform,
            sortOrder = nextOrder,
            addedAt = now,
        )
    )
    val added = rowId != -1L
    if (added) {
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null && playlist.coverUri == null && !item.artwork.isNullOrBlank()) {
            val rel = coverStore.copyFromArtwork(playlistId, item.artwork)
            if (rel != null) playlistDao.setCoverUri(playlistId, rel, System.currentTimeMillis())
        }
    }
    return added
}
```

- [ ] **Step 5: 运行编译和可用测试**

运行：

```bash
./gradlew :data:testDebugUnitTest
./gradlew :data:compileDebugAndroidTestKotlin
```

预期：通过。

设备可用时再运行：

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryTest
```

预期：通过。

- [ ] **Step 6: 提交**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt
git commit -m "feat(data): import playlist songs in batch"
```

---

### Task 4: 新增 PlaylistImportViewModel 状态机

**文件：**
- 创建： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportModels.kt`
- 创建： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportViewModel.kt`
- 创建： `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportViewModelTest.kt`

- [ ] **Step 1: 写 ViewModel 测试骨架和 helper**

创建 `PlaylistImportViewModelTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.feature.home.playlistimport

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pluginManager: PluginManager = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val pluginsFlow = MutableStateFlow<List<LoadedPlugin>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        whenever(pluginManager.getSortedEnabledPlugins()).thenReturn(pluginsFlow)
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlaylistImportViewModel(pluginManager, playlistRepository)

    private fun plugin(
        platform: String,
        methods: Set<String>,
        hints: Map<String, List<String>>? = null,
    ): LoadedPlugin {
        val loaded: LoadedPlugin = mock()
        whenever(loaded.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = listOf("music"),
                hints = hints,
                supportedMethods = methods,
            )
        )
        return loaded
    }

    private fun item(id: String) = MusicItem(
        id = id,
        platform = "demo",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
```

- [ ] **Step 2: 写失败测试，覆盖插件筛选、空输入、解析成功、导入统计**

在测试类中追加：

```kotlin
@Test
fun `openImportSheet exposes only plugins with importMusicSheet capability`() = runTest {
    pluginsFlow.value = listOf(
        plugin("search-only", methods = setOf("search")),
        plugin("sheet-plugin", methods = setOf("search", "importMusicSheet")),
    )
    val vm = viewModel()

    vm.openImportSheet()
    advanceUntilIdle()

    val state = vm.importState.value as PlaylistImportState.ChoosePlugin
    assertEquals(listOf("sheet-plugin"), state.plugins.map { it.platform })
}

@Test
fun `submitUrl with blank text keeps input state and does not call plugin`() = runTest {
    val p = plugin("sheet-plugin", methods = setOf("importMusicSheet"))
    pluginsFlow.value = listOf(p)
    val vm = viewModel()
    vm.openImportSheet()
    advanceUntilIdle()
    vm.selectPlugin("sheet-plugin")

    vm.submitUrl("   ")
    advanceUntilIdle()

    val state = vm.importState.value as PlaylistImportState.InputUrl
    assertEquals("链接有误或目标歌单为空", state.errorMessage)
    verify(p, never()).importMusicSheet(any())
}

@Test
fun `submitUrl with parsed items moves to confirmation`() = runTest {
    val p = plugin("sheet-plugin", methods = setOf("importMusicSheet"))
    whenever(p.importMusicSheet("https://example.com/sheet")).thenReturn(listOf(item("1"), item("2")))
    pluginsFlow.value = listOf(p)
    val vm = viewModel()
    vm.openImportSheet()
    advanceUntilIdle()
    vm.selectPlugin("sheet-plugin")

    vm.submitUrl("https://example.com/sheet")
    advanceUntilIdle()

    val state = vm.importState.value as PlaylistImportState.ConfirmFound
    assertEquals(2, state.items.size)
}

@Test
fun `addImportedItemsToPlaylist reports added and skipped counts`() = runTest {
    val items = listOf(item("1"), item("2"), item("1"))
    whenever(playlistRepository.addMusicsToPlaylist("target", items)).thenReturn(2)
    val vm = viewModel()
    vm.confirmImportTarget(items)

    vm.addImportedItemsToPlaylist("target")
    advanceUntilIdle()

    val state = vm.importState.value as PlaylistImportState.Completed
    assertEquals(2, state.added)
    assertEquals(1, state.skipped)
}
```

- [ ] **Step 3: 运行测试，确认失败**

运行：

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.playlistimport.PlaylistImportViewModelTest
```

预期：失败，类和状态模型尚未创建。

- [ ] **Step 4: 创建状态模型**

创建 `PlaylistImportModels.kt`：

```kotlin
package com.zili.android.musicfreeandroid.feature.home.playlistimport

import com.zili.android.musicfreeandroid.core.model.MusicItem

data class ImportCapablePlugin(
    val platform: String,
    val name: String,
    val hints: List<String> = emptyList(),
)

sealed interface PlaylistImportState {
    data object Idle : PlaylistImportState
    data object LoadingPlugins : PlaylistImportState
    data class ChoosePlugin(val plugins: List<ImportCapablePlugin>) : PlaylistImportState
    data class InputUrl(
        val plugin: ImportCapablePlugin,
        val errorMessage: String? = null,
    ) : PlaylistImportState
    data class Parsing(val pluginName: String) : PlaylistImportState
    data class ConfirmFound(
        val plugin: ImportCapablePlugin,
        val items: List<MusicItem>,
    ) : PlaylistImportState
    data class ChooseTarget(val items: List<MusicItem>) : PlaylistImportState
    data class Completed(val added: Int, val skipped: Int) : PlaylistImportState
    data class Error(val message: String) : PlaylistImportState
}

sealed interface PlaylistImportEvent {
    data class Toast(val message: String) : PlaylistImportEvent
}
```

- [ ] **Step 5: 创建 ViewModel**

创建 `PlaylistImportViewModel.kt`：

```kotlin
package com.zili.android.musicfreeandroid.feature.home.playlistimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val _importState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    private val _events = MutableSharedFlow<PlaylistImportEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlaylistImportEvent> = _events.asSharedFlow()

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.observeAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openImportSheet() {
        viewModelScope.launch {
            _importState.value = PlaylistImportState.LoadingPlugins
            pluginManager.ensurePluginsLoaded()
            val plugins = pluginManager.getSortedEnabledPlugins().first()
                .filter { "importMusicSheet" in it.info.supportedMethods }
                .map { loaded ->
                    ImportCapablePlugin(
                        platform = loaded.info.platform,
                        name = loaded.info.platform,
                        hints = loaded.info.hints?.get("importMusicSheet").orEmpty(),
                    )
                }
            _importState.value = PlaylistImportState.ChoosePlugin(plugins)
        }
    }

    fun selectPlugin(platform: String) {
        val state = _importState.value as? PlaylistImportState.ChoosePlugin ?: return
        val plugin = state.plugins.firstOrNull { it.platform == platform } ?: return
        _importState.value = PlaylistImportState.InputUrl(plugin)
    }

    fun submitUrl(urlLike: String) {
        val state = _importState.value as? PlaylistImportState.InputUrl ?: return
        val trimmed = urlLike.trim()
        if (trimmed.isBlank()) {
            _importState.value = state.copy(errorMessage = "链接有误或目标歌单为空")
            return
        }
        viewModelScope.launch {
            _importState.value = PlaylistImportState.Parsing(state.plugin.name)
            val plugin = pluginManager.getPlugin(state.plugin.platform)
            val result = runCatching { plugin?.importMusicSheet(trimmed) }.getOrNull().orEmpty()
            if (result.isEmpty()) {
                _events.tryEmit(PlaylistImportEvent.Toast("链接有误或目标歌单为空"))
                _importState.value = PlaylistImportState.Idle
            } else {
                _importState.value = PlaylistImportState.ConfirmFound(state.plugin, result)
            }
        }
    }

    fun confirmFoundItems() {
        val state = _importState.value as? PlaylistImportState.ConfirmFound ?: return
        confirmImportTarget(state.items)
    }

    fun confirmImportTarget(items: List<MusicItem>) {
        if (items.isEmpty()) return
        _sheetState.value = AddToPlaylistSheetState.batch(items)
        _importState.value = PlaylistImportState.ChooseTarget(items)
    }

    fun hideTargetSheet() {
        _sheetState.value = AddToPlaylistSheetState()
        _importState.value = PlaylistImportState.Idle
    }

    fun addImportedItemsToPlaylist(targetPlaylistId: String) {
        val items = (_importState.value as? PlaylistImportState.ChooseTarget)?.items
            ?: _sheetState.value.pendingItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playlistRepository.addMusicsToPlaylist(targetPlaylistId, items)
            }.onSuccess { added ->
                val skipped = items.size - added
                _sheetState.value = AddToPlaylistSheetState()
                _importState.value = PlaylistImportState.Completed(added = added, skipped = skipped)
                _events.tryEmit(PlaylistImportEvent.Toast(importResultMessage(added, skipped)))
            }.onFailure {
                _events.tryEmit(PlaylistImportEvent.Toast("导入失败，请重试"))
            }
        }
    }

    fun createPlaylistAndImport(name: String) {
        val items = (_importState.value as? PlaylistImportState.ChooseTarget)?.items
            ?: _sheetState.value.pendingItems
        if (items.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            playlistRepository.createPlaylist(Playlist(id = newId, name = name.trim(), coverUri = null))
            val added = playlistRepository.addMusicsToPlaylist(newId, items)
            val skipped = items.size - added
            _sheetState.value = AddToPlaylistSheetState()
            _importState.value = PlaylistImportState.Completed(added = added, skipped = skipped)
            _events.tryEmit(PlaylistImportEvent.Toast(importResultMessage(added, skipped)))
        }
    }

    fun dismissImportFlow() {
        _sheetState.value = AddToPlaylistSheetState()
        _importState.value = PlaylistImportState.Idle
    }

    private fun importResultMessage(added: Int, skipped: Int): String =
        if (skipped > 0) "已导入 ${added} 首，跳过 ${skipped} 首重复歌曲" else "已导入 ${added} 首"
}
```

- [ ] **Step 6: 运行 ViewModel 测试**

运行：

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.playlistimport.PlaylistImportViewModelTest
```

预期：通过。

- [ ] **Step 7: 提交**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportModels.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportViewModel.kt feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportViewModelTest.kt
git commit -m "feat(home): add playlist import state machine"
```

---

### Task 5: 实现首页导入 UI Host 并接入 HomeScreen

**文件：**
- 创建： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportHost.kt`
- 修改： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`

- [ ] **Step 1: 创建导入 UI Host**

创建 `PlaylistImportHost.kt`：

```kotlin
package com.zili.android.musicfreeandroid.feature.home.playlistimport

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent
import com.zili.android.musicfreeandroid.feature.home.playlist.CreatePlaylistDialog
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportRoute(
    viewModel: PlaylistImportViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.importState.collectAsStateWithLifecycle()
    val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PlaylistImportEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    PlaylistImportHost(
        state = state,
        sheetVisible = sheetState.visible,
        playlists = allPlaylists,
        onDismiss = viewModel::dismissImportFlow,
        onSelectPlugin = viewModel::selectPlugin,
        onSubmitUrl = viewModel::submitUrl,
        onConfirmFound = viewModel::confirmFoundItems,
        onDismissTarget = viewModel::hideTargetSheet,
        onSelectTarget = { viewModel.addImportedItemsToPlaylist(it.id) },
        onCreateTarget = viewModel::createPlaylistAndImport,
        modifier = modifier,
    )
}
```

同文件继续添加 stateless host 和小组件：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportHost(
    state: PlaylistImportState,
    sheetVisible: Boolean,
    playlists: List<com.zili.android.musicfreeandroid.core.model.Playlist>,
    onDismiss: () -> Unit,
    onSelectPlugin: (String) -> Unit,
    onSubmitUrl: (String) -> Unit,
    onConfirmFound: () -> Unit,
    onDismissTarget: () -> Unit,
    onSelectTarget: (com.zili.android.musicfreeandroid.core.model.Playlist) -> Unit,
    onCreateTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        PlaylistImportState.Idle,
        is PlaylistImportState.Completed,
        is PlaylistImportState.Error -> Unit

        PlaylistImportState.LoadingPlugins -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("导入歌单") },
                text = {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                confirmButton = {},
            )
        }

        is PlaylistImportState.ChoosePlugin -> {
            ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("PlaylistImport_PluginSheet")) {
                ImportPluginList(
                    plugins = state.plugins,
                    onSelectPlugin = onSelectPlugin,
                )
            }
        }

        is PlaylistImportState.InputUrl -> {
            ImportUrlDialog(
                state = state,
                onDismiss = onDismiss,
                onSubmit = onSubmitUrl,
            )
        }

        is PlaylistImportState.Parsing -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("导入歌单") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(16.dp))
                        Text("正在导入中...")
                    }
                },
                confirmButton = {},
            )
        }

        is PlaylistImportState.ConfirmFound -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("准备导入") },
                text = { Text("发现 ${state.items.size} 首歌曲! 现在开始导入吗?") },
                confirmButton = {
                    TextButton(onClick = onConfirmFound) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("取消") }
                },
            )
        }

        is PlaylistImportState.ChooseTarget -> Unit
    }

    if (sheetVisible) {
        var showCreateDialog by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = onDismissTarget, modifier = Modifier.testTag("PlaylistImport_TargetSheet")) {
            AddToPlaylistBottomSheetContent(
                playlists = playlists,
                onSelect = onSelectTarget,
                onCreateNew = { showCreateDialog = true },
                folderPlusIcon = painterResource(id = R.drawable.ic_folder_plus),
                favoriteCoverIcon = painterResource(id = R.drawable.ic_playlist_favorite_cover),
            )
        }
        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    showCreateDialog = false
                    onCreateTarget(name)
                },
            )
        }
    }
}

@Composable
private fun ImportPluginList(
    plugins: List<ImportCapablePlugin>,
    onSelectPlugin: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("导入歌单", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        if (plugins.isEmpty()) {
            Text(
                "暂无支持导入歌单的插件",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).testTag("PlaylistImport_NoPlugin"),
            )
        } else {
            LazyColumn {
                items(plugins, key = { it.platform }) { plugin ->
                    Text(
                        text = plugin.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlugin(plugin.platform) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("PlaylistImport_Plugin_${plugin.platform}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportUrlDialog(
    state: PlaylistImportState.InputUrl,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember(state.plugin.platform) { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入歌单") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { next -> if (next.text.length <= 1000) value = next },
                    label = { Text("输入目标歌单") },
                    isError = state.errorMessage != null,
                    supportingText = {
                        Column {
                            state.errorMessage?.let { Text(it) }
                            state.plugin.hints.forEach { Text(it) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("PlaylistImport_Input"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value.text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
```

- [ ] **Step 2: 接入 HomeScreen**

在 `HomeScreen.kt` imports 加入：

```kotlin
import com.zili.android.musicfreeandroid.feature.home.playlistimport.PlaylistImportRoute
import com.zili.android.musicfreeandroid.feature.home.playlistimport.PlaylistImportViewModel
```

在 `HomeScreen` 参数列表内保留现有 `viewModel`，函数体内创建导入 ViewModel：

```kotlin
val importViewModel: PlaylistImportViewModel = hiltViewModel()
```

把 `onImportClick = {}` 改为：

```kotlin
onImportClick = { importViewModel.openImportSheet() },
```

在 `CreatePlaylistDialog` 之后追加：

```kotlin
PlaylistImportRoute(viewModel = importViewModel)
```

- [ ] **Step 3: 编译 feature home**

运行：

```bash
./gradlew :feature:home:compileDebugKotlin
```

预期：通过。

- [ ] **Step 4: 提交**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportHost.kt feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt
git commit -m "feat(home): wire playlist import UI"
```

---

### Task 6: UI 测试覆盖导入面板关键交互

**文件：**
- 创建： `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportHostTest.kt`

- [ ] **Step 1: 写 UI 测试**

创建 `PlaylistImportHostTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.feature.home.playlistimport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaylistImportHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `choose plugin sheet shows empty state`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    state = PlaylistImportState.ChoosePlugin(emptyList()),
                    sheetVisible = false,
                    playlists = emptyList(),
                    onDismiss = {},
                    onSelectPlugin = {},
                    onSubmitUrl = {},
                    onConfirmFound = {},
                    onDismissTarget = {},
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }

        composeRule.onNodeWithTag("PlaylistImport_NoPlugin").assertIsDisplayed()
        composeRule.onNodeWithText("暂无支持导入歌单的插件").assertIsDisplayed()
    }

    @Test
    fun `choose plugin sheet emits selected platform`() {
        var selected = ""
        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    state = PlaylistImportState.ChoosePlugin(
                        listOf(ImportCapablePlugin(platform = "demo", name = "demo"))
                    ),
                    sheetVisible = false,
                    playlists = emptyList(),
                    onDismiss = {},
                    onSelectPlugin = { selected = it },
                    onSubmitUrl = {},
                    onConfirmFound = {},
                    onDismissTarget = {},
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }

        composeRule.onNodeWithTag("PlaylistImport_Plugin_demo").performClick()
        composeRule.runOnIdle { assertEquals("demo", selected) }
    }

    @Test
    fun `confirm found dialog shows parsed count`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    state = PlaylistImportState.ConfirmFound(
                        plugin = ImportCapablePlugin(platform = "demo", name = "demo"),
                        items = listOf(music("1"), music("2")),
                    ),
                    sheetVisible = false,
                    playlists = emptyList(),
                    onDismiss = {},
                    onSelectPlugin = {},
                    onSubmitUrl = {},
                    onConfirmFound = {},
                    onDismissTarget = {},
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }

        composeRule.onNodeWithText("发现 2 首歌曲! 现在开始导入吗?").assertIsDisplayed()
    }

    @Test
    fun `target sheet lists playlists for imported items`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    state = PlaylistImportState.ChooseTarget(listOf(music("1"))),
                    sheetVisible = true,
                    playlists = listOf(Playlist(id = "p1", name = "Road", coverUri = null, worksNum = 3)),
                    onDismiss = {},
                    onSelectPlugin = {},
                    onSubmitUrl = {},
                    onConfirmFound = {},
                    onDismissTarget = {},
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }

        composeRule.onNodeWithTag("PlaylistImport_TargetSheet").assertIsDisplayed()
        composeRule.onNodeWithText("Road").assertIsDisplayed()
    }

    private fun music(id: String) = MusicItem(
        id = id,
        platform = "demo",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
```

- [ ] **Step 2: 运行 UI 测试**

运行：

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.playlistimport.PlaylistImportHostTest
```

预期：通过。

- [ ] **Step 3: 提交**

```bash
git add feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportHostTest.kt
git commit -m "test(home): cover playlist import UI"
```

---

### Task 7: 集成回归和收口

**文件：**
- 检查： `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginInfo.kt`
- 检查： `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistSheetState.kt`
- 检查： `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`
- 检查： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportViewModel.kt`
- 检查： `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlistimport/PlaylistImportHost.kt`

- [ ] **Step 1: 运行模块单测**

运行：

```bash
./gradlew :plugin:testDebugUnitTest :feature:player-ui:testDebugUnitTest :feature:search:testDebugUnitTest :feature:home:testDebugUnitTest :data:testDebugUnitTest
```

预期：通过。

- [ ] **Step 2: 编译 Android test 源集**

运行：

```bash
./gradlew :data:compileDebugAndroidTestKotlin
```

预期：通过。

- [ ] **Step 3: 构建 Debug APK**

运行：

```bash
./gradlew :app:assembleDebug
```

预期：通过，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 4: 设备可用时运行数据层 instrumented 测试**

先检查设备：

```bash
adb devices
```

若存在 `device` 状态的设备，运行：

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryTest
```

预期：通过。

若没有设备，在最终汇报中写明未运行 connected 测试及原因。

- [ ] **Step 5: 运行态手动验收**

使用 debug 包或当前安装包执行：

```text
1. 安装或启用一个支持 importMusicSheet 的插件。
2. 打开首页，点击“我的歌单”区域右侧“导入歌单”。
3. 确认只展示支持 importMusicSheet 的已启用插件。
4. 选择插件，输入有效歌单链接。
5. 确认出现“发现 N 首歌曲! 现在开始导入吗?”。
6. 选择已有歌单，确认目标歌单详情出现导入歌曲。
7. 对同一链接再次导入同一歌单，确认提示跳过重复歌曲。
8. 通过“新建歌单”创建新歌单并导入，确认首页数量和详情列表更新。
```

- [ ] **Step 6: 最终状态检查**

运行：

```bash
git status --short
git log --oneline --decorate -8
```

预期：工作区只包含本功能预期改动；提交历史包含 spec、plan 和实现提交。

---

## 自审结果

- Spec 覆盖：插件能力识别、RN 式首页交互、平台兜底、批量写入去重、错误处理、UI 约束、测试与运行态验收均有对应任务。
- 类型一致性：计划中统一使用 `ImportCapablePlugin`、`PlaylistImportState`、`PlaylistImportEvent`、`AddToPlaylistSheetState.pendingItems`、`PlaylistRepository.addMusicsToPlaylist`。
- Scope 检查：插件管理页“导入歌单”和“导入单曲”不在本计划内。
