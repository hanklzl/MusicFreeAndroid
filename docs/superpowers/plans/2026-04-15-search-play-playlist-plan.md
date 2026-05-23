# 实施计划：搜索 → 播放 → 歌单 完整链路

**日期**: 2026-04-15
**设计文档**: `docs/superpowers/specs/2026-04-15-search-play-playlist-design.md`
**状态**: 待执行

---

## 前置上下文

本计划对应设计文档中的所有改动项。目标是在新 session 中无损执行，因此每个步骤都包含完整的文件路径、当前代码状态、具体改动方案。

**项目根目录**: `/Users/zili/code/android/MusicFreeAndroid`
**RN 原版目录**: `../MusicFree`
**构建命令**: `./gradlew assembleDebug`
**单测命令**: `./gradlew test`
**订阅源测试地址**: `https://example.invalid/subscription.json`

**模块依赖方向**: `:app → :feature:* → :data, :player, :plugin → :core`

---

## 步骤 0: 图标资源准备

### 0.1 从 RN 拷贝 SVG 图标

**源目录**: `../MusicFree/src/assets/icons/`
**目标目录**: `core/src/main/res/drawable/`

需要拷贝的 18 个 SVG（kebab→snake_case，加 `ic_` 前缀）:

| RN 文件名 | Android 资源名 | 用途 |
|-----------|---------------|------|
| `arrow-left.svg` | `ic_arrow_left.xml` | 返回按钮（搜索页/播放器） |
| `magnifying-glass.svg` | `ic_magnifying_glass.xml` | 搜索图标 |
| `x-mark.svg` | `ic_x_mark.xml` | 清除搜索内容 |
| `ellipsis-vertical.svg` | `ic_ellipsis_vertical.xml` | 三点菜单 |
| `share.svg` | `ic_share.xml` | 播放器分享按钮 |
| `heart-outline.svg` | `ic_heart_outline.xml` | 收藏（未收藏） |
| `heart.svg` | `ic_heart.xml` | 收藏（已收藏） |
| `arrow-down-tray.svg` | `ic_arrow_down_tray.xml` | 下载 |
| `chat-bubble-oval-left-ellipsis.svg` | `ic_chat_bubble.xml` | 评论 |
| `shuffle.svg` | `ic_shuffle.xml` | 随机播放 |
| `repeat-song.svg` | `ic_repeat_song.xml` | 列表循环 |
| `repeat-song-1.svg` | `ic_repeat_song_1.xml` | 单曲循环 |
| `skip-left.svg` | `ic_skip_left.xml` | 上一曲 |
| `skip-right.svg` | `ic_skip_right.xml` | 下一曲 |
| `play.svg` | `ic_play.xml` | 播放 |
| `pause.svg` | `ic_pause.xml` | 暂停 |
| `playlist.svg` | `ic_playlist.xml` | 播放队列 |
| `folder-plus.svg` | `ic_folder_plus.xml` | 添加到歌单 |
| `motion-play.svg` | `ic_motion_play.xml` | 下一首播放 |

**操作**:
1. SVG 文件不能直接用于 Android，需要转换为 Android Vector Drawable (XML)
2. 使用 Android Studio 的 Vector Asset 导入工具，或手动将 SVG 转为 `<vector>` XML
3. 每个 SVG 打开查看 viewBox 和 path data，转写为 `core/src/main/res/drawable/ic_xxx.xml`
4. 如果 SVG 足够简单（单 path），可直接手写 vector XML；复杂的用 `avocado` 或 AS 工具转换

### 0.2 拷贝 PNG 资源（品质/倍速图标）

**源目录**: `../MusicFree/src/assets/imgs/`
**目标目录**: `core/src/main/res/drawable/`

```
standard-quality.png → ic_quality_standard.png
100x.png → ic_rate_100.png
```

仅需 `standard-quality.png` 和 `100x.png` 作为默认占位。

### 0.3 默认封面图

**源文件**: `../MusicFree/src/assets/imgs/album-default.jpeg`
**目标**: `core/src/main/res/drawable/album_default.jpg`

---

## 步骤 1: 搜索历史 — DataStore 扩展

### 文件: `data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt`

**当前状态**: 78 行，包含 repeatMode/playQuality/shuffleEnabled/darkMode/currentMusicIndex/storageDirectoryUri

**新增内容** — 在 `setStorageDirectoryUri` 方法之后（约第 68 行后）添加:

```kotlin
// ── Search History ──

private companion object {
    // ... 现有 keys ...
    val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
    const val MAX_SEARCH_HISTORY = 20
}

val searchHistory: Flow<List<String>> = dataStore.data.map { prefs ->
    prefs[KEY_SEARCH_HISTORY]
        ?.split("\u001F")  // Unit Separator 作为分隔符，避免逗号在搜索词中出现
        ?.filter { it.isNotBlank() }
        ?: emptyList()
}

suspend fun addSearchQuery(query: String) {
    dataStore.edit { prefs ->
        val current = prefs[KEY_SEARCH_HISTORY]
            ?.split("\u001F")
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        current.remove(query)  // 去重
        current.add(0, query)  // 最新置顶
        if (current.size > MAX_SEARCH_HISTORY) {
            current.subList(MAX_SEARCH_HISTORY, current.size).clear()
        }
        prefs[KEY_SEARCH_HISTORY] = current.joinToString("\u001F")
    }
}

suspend fun clearSearchHistory() {
    dataStore.edit { it.remove(KEY_SEARCH_HISTORY) }
}
```

**注意**: 需要将现有的 `private companion object` 块（第 70-77 行）合并，把新 key 加进去。

**编译验证**: `./gradlew :data:assembleDebug`

---

## 步骤 2: 搜索页 — SearchUiState 重写

### 文件: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchUiState.kt`

**当前状态**: 18 行，单一维度 sealed interface

**完全重写为**:

```kotlin
package com.hank.musicfree.feature.search

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.ArtistItemBase
import com.hank.musicfree.plugin.api.MusicSheetItemBase

/** 搜索页整体页面状态 */
enum class SearchPageStatus {
    /** 初始/编辑态：显示搜索历史 */
    EDITING,
    /** 正在搜索中（至少一个源在加载） */
    SEARCHING,
    /** 有结果展示 */
    RESULT,
    /** 没有可用插件 */
    NO_PLUGIN,
}

/** 单个插件在单个媒体类型下的搜索状态 */
sealed interface PluginSearchState {
    data object Idle : PluginSearchState
    data object Loading : PluginSearchState
    data class Success(
        val items: List<Any>,  // MusicItem / AlbumItemBase / ArtistItemBase / MusicSheetItemBase
        val isEnd: Boolean,
        val page: Int,
    ) : PluginSearchState
    data class Error(val message: String) : PluginSearchState
}

/** 媒体搜索类型，对齐 RN 的 supportedSearchType */
enum class SearchMediaType(val key: String, val label: String) {
    MUSIC("music", "单曲"),
    ALBUM("album", "专辑"),
    ARTIST("artist", "歌手"),
    SHEET("sheet", "歌单"),
}
```

---

## 步骤 3: 搜索页 — SearchViewModel 重写

### 文件: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`

**当前状态**: 248 行，单插件搜索

**完全重写**。核心变化:

1. **注入 `AppPreferences`** (新增依赖):
   - `feature/search/build.gradle.kts` 需新增 `implementation(project(":data"))`
2. **二维搜索状态**: `Map<SearchMediaType, Map<String/*platform*/, PluginSearchState>>`
3. **全源并行搜索**
4. **搜索历史管理**

**新 ViewModel 结构**:

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
        private const val WY_FALLBACK_PLATFORM = "元力WY"
    }

    // ── 插件状态 ──
    private val _searchablePlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val searchablePlugins: StateFlow<List<PluginInfo>> = _searchablePlugins.asStateFlow()

    // ── 页面状态 ──
    private val _pageStatus = MutableStateFlow(SearchPageStatus.EDITING)
    val pageStatus: StateFlow<SearchPageStatus> = _pageStatus.asStateFlow()

    // ── 搜索历史 ──
    val searchHistory: StateFlow<List<String>> = appPreferences.searchHistory
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── 当前查询 ──
    private val _currentQuery = MutableStateFlow("")
    val currentQuery: StateFlow<String> = _currentQuery.asStateFlow()

    // ── Tab 选择 ──
    private val _selectedMediaType = MutableStateFlow(SearchMediaType.MUSIC)
    val selectedMediaType: StateFlow<SearchMediaType> = _selectedMediaType.asStateFlow()

    private val _selectedPlatform = MutableStateFlow<String?>(null)
    val selectedPlatform: StateFlow<String?> = _selectedPlatform.asStateFlow()

    // ── 二维搜索结果 ──
    // searchResults[mediaType][platform] = PluginSearchState
    private val _searchResults = MutableStateFlow<Map<SearchMediaType, Map<String, PluginSearchState>>>(emptyMap())
    val searchResults: StateFlow<Map<SearchMediaType, Map<String, PluginSearchState>>> = _searchResults.asStateFlow()

    /** 当前选中的 Tab 组合对应的搜索状态 */
    val currentPluginState: StateFlow<PluginSearchState> = combine(
        _searchResults, _selectedMediaType, _selectedPlatform,
    ) { results, mediaType, platform ->
        platform?.let { results[mediaType]?.get(it) } ?: PluginSearchState.Idle
    }.stateIn(viewModelScope, SharingStarted.Lazily, PluginSearchState.Idle)

    init {
        viewModelScope.launch {
            pluginManager.plugins.collect { plugins ->
                val searchable = plugins
                    .filter { it.info.supportedSearchType.contains("music") || it.info.supportedSearchType.isEmpty() }
                    .map { it.info }
                _searchablePlugins.value = searchable
                if (_selectedPlatform.value == null && searchable.isNotEmpty()) {
                    _selectedPlatform.value = searchable.first().platform
                }
                if (searchable.isEmpty()) {
                    _pageStatus.value = SearchPageStatus.NO_PLUGIN
                }
            }
        }
        viewModelScope.launch { pluginManager.loadAllPlugins() }
    }

    // ── 搜索 ──

    fun searchAll(query: String) {
        if (query.isBlank()) return
        _currentQuery.value = query
        _pageStatus.value = SearchPageStatus.SEARCHING

        viewModelScope.launch { appPreferences.addSearchQuery(query) }

        val mediaType = _selectedMediaType.value
        searchForMediaType(query, mediaType)
    }

    private fun searchForMediaType(query: String, mediaType: SearchMediaType) {
        val plugins = _searchablePlugins.value
        if (plugins.isEmpty()) return

        // 初始化该 mediaType 下所有插件为 Loading
        val typeResults = plugins.associate { it.platform to PluginSearchState.Loading as PluginSearchState }
        _searchResults.value = _searchResults.value.toMutableMap().apply {
            put(mediaType, typeResults)
        }

        // 并行搜索每个插件
        plugins.forEach { pluginInfo ->
            viewModelScope.launch {
                val plugin = pluginManager.getPlugin(pluginInfo.platform) ?: return@launch
                try {
                    val result = plugin.search(query, page = 1, type = mediaType.key)
                    updatePluginState(mediaType, pluginInfo.platform, PluginSearchState.Success(
                        items = result.data,
                        isEnd = result.isEnd,
                        page = 1,
                    ))
                } catch (e: Exception) {
                    updatePluginState(mediaType, pluginInfo.platform,
                        PluginSearchState.Error(e.message ?: "搜索失败"))
                }
                // 检查是否所有插件都已完成
                checkSearchCompletion()
            }
        }
    }

    private fun updatePluginState(mediaType: SearchMediaType, platform: String, state: PluginSearchState) {
        _searchResults.value = _searchResults.value.toMutableMap().apply {
            val typeMap = (get(mediaType) ?: emptyMap()).toMutableMap()
            typeMap[platform] = state
            put(mediaType, typeMap)
        }
    }

    private fun checkSearchCompletion() {
        val currentType = _selectedMediaType.value
        val typeResults = _searchResults.value[currentType] ?: return
        val anyLoading = typeResults.values.any { it is PluginSearchState.Loading }
        if (!anyLoading) {
            _pageStatus.value = SearchPageStatus.RESULT
        }
    }

    // ── Tab 切换 ──

    fun selectMediaType(type: SearchMediaType) {
        _selectedMediaType.value = type
        val query = _currentQuery.value
        // 如果该类型还没搜过，触发搜索
        if (query.isNotBlank() && _searchResults.value[type] == null) {
            searchForMediaType(query, type)
        }
        // 自动选第一个插件
        val platforms = _searchResults.value[type]?.keys?.toList()
            ?: _searchablePlugins.value.map { it.platform }
        if (_selectedPlatform.value !in platforms && platforms.isNotEmpty()) {
            _selectedPlatform.value = platforms.first()
        }
    }

    fun selectPlatform(platform: String) {
        _selectedPlatform.value = platform
    }

    // ── 分页 ──

    fun loadMore() {
        val mediaType = _selectedMediaType.value
        val platform = _selectedPlatform.value ?: return
        val current = _searchResults.value[mediaType]?.get(platform)
        if (current !is PluginSearchState.Success || current.isEnd) return

        val plugin = pluginManager.getPlugin(platform) ?: return
        val nextPage = current.page + 1
        val query = _currentQuery.value

        viewModelScope.launch {
            try {
                val result = plugin.search(query, page = nextPage, type = mediaType.key)
                updatePluginState(mediaType, platform, current.copy(
                    items = current.items + result.data,
                    isEnd = result.isEnd,
                    page = nextPage,
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
            }
        }
    }

    // ── 播放 ──

    suspend fun resolveAndPlay(item: MusicItem, queue: List<MusicItem>): Boolean {
        // 保留现有的 resolveAndPlay + resolveMediaSourceWithFallback 逻辑
        // 但改为从 _selectedPlatform 获取当前插件
        val platform = _selectedPlatform.value ?: return false
        val plugin = pluginManager.getPlugin(platform) ?: return false
        // ... 现有 fallback 逻辑不变 ...
    }

    // ── 搜索历史 ──

    fun clearHistory() {
        viewModelScope.launch { appPreferences.clearSearchHistory() }
    }

    fun backToEditing() {
        _pageStatus.value = SearchPageStatus.EDITING
    }
}
```

### build.gradle.kts 修改

**文件**: `feature/search/build.gradle.kts`
**第 43 行后添加**:
```kotlin
implementation(project(":data"))
```

---

## 步骤 4: 搜索页 — SearchScreen 重写

### 文件: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`

**当前状态**: 323 行，TopAppBar + OutlinedTextField + SegmentedButton + LazyColumn

**完全重写**。新结构:

```
SearchScreen
├── SearchNavBar (自定义 Row: 返回键 + 药丸搜索框 + 搜索按钮)
├── when (pageStatus) {
│   EDITING → SearchHistoryPanel (FlowRow chips)
│   NO_PLUGIN → EmptyState("请先安装插件")
│   SEARCHING → LoadingState
│   RESULT → SearchResultPanel
│       ├── MediaTypeTabRow (第一层: 单曲/专辑/歌手/歌单)
│       ├── PluginTabRow (第二层: 插件名, 无指示器)
│       └── when (currentPluginState) {
│           Loading → CircularProgressIndicator
│           Success → LazyColumn/LazyVerticalGrid
│           Error → ErrorState
│       }
│   }
└── (底部 MiniPlayer 由 MainActivity 管理)
```

**关键 Composable 拆分**（建议拆为多个文件，但可先在一个文件中实现后续再拆）:

1. `SearchNavBar` — 药丸形搜索栏
   - 高: `rpx(88)`, padding: `rpx(24)`
   - 返回键: `ic_arrow_left` 图标
   - 输入框: `BasicTextField`, 高 `rpx(64)`, 圆角 `rpx(64)`
   - 背景: `MusicFreeTheme.colors.pageBackground`（RN 中是 `colors.pageBackground`）
   - 搜索按钮: TextButton "搜索"

2. `SearchHistoryPanel` — 搜索历史
   - padding: `rpx(24)`
   - 标题行: "搜索历史" + "清空"
   - `FlowRow` (Compose Foundation) + chips

3. `MediaTypeTabRow` — 第一层 Tab
   - `ScrollableTabRow`, tab 宽 `rpx(160)`, 指示器高 `rpx(4)` primary 色

4. `PluginTabRow` — 第二层 Tab
   - `ScrollableTabRow`, tab 宽 `rpx(140)`, 无指示器

5. `MusicResultItem` — 单曲结果项
   - 高 `rpx(120)`, padding `rpx(24)`
   - 封面 `rpx(80)×rpx(80)` 圆角 `rpx(16)`
   - 右侧三点菜单 `ic_ellipsis_vertical`
   - `combinedClickable`: click → 播放, longClick → 弹出菜单也可

6. `MusicItemOptionsSheet` — 三点菜单 BottomSheet
   - "添加到歌单" (ic_folder_plus) → 弹 AddToPlaylistDialog
   - "下一首播放" (ic_motion_play) → playerController.addNext(item)

### 新增依赖

`feature/search/build.gradle.kts`:
- 确认已有 `implementation(libs.coil.compose)` ✅
- 新增: `implementation(project(":data"))` (步骤 3 已加)

### SearchNavigation.kt 无需改动

现有的 `searchScreen()` NavGraphBuilder 扩展函数签名不变。

---

## 步骤 5: 播放器 — PlayerScreen 重写

### 文件: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`

**当前状态**: 291 行，Material3 TopAppBar + 纯色背景 + Slider + 5 个控制按钮

**完全重写**。新结构:

```kotlin
@Composable
fun PlayerScreen(onBack: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val currentItem = state.currentItem

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: 纯黑背景
        Box(Modifier.fillMaxSize().background(Color.Black))

        // Layer 2: 封面模糊
        currentItem?.artwork?.let { artworkUrl ->
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .alpha(0.5f),
                contentScale = ContentScale.Crop,
            )
        }

        // Layer 3: 内容
        Column(Modifier.fillMaxSize()) {
            // 3.1 自定义导航栏（居中标题）
            PlayerNavBar(
                title = currentItem?.title ?: "",
                artist = currentItem?.artist ?: "",
                platform = currentItem?.platform,
                onBack = onBack,
            )

            Spacer(Modifier.weight(1f))

            // 3.2 封面
            PlayerCoverArt(
                artworkUrl = currentItem?.artwork,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // 3.3 操作栏（UI 占位）
            PlayerOperationsBar()

            Spacer(Modifier.weight(1f))

            // 3.4 进度条
            PlayerSeekBar(
                position = state.position,
                duration = state.duration,
                onSeek = viewModel::seekTo,
            )

            // 3.5 播放控制
            PlayerControls(
                isPlaying = state.isPlaying,
                repeatMode = state.repeatMode,
                shuffleEnabled = state.shuffleEnabled,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipPrevious = viewModel::skipToPrevious,
                onSkipNext = viewModel::skipToNext,
                onCycleRepeatMode = viewModel::cycleRepeatMode,
                onToggleShuffle = viewModel::toggleShuffle,
            )

            Spacer(Modifier.height(rpx(48)))
        }
    }
}
```

### 5.1 PlayerNavBar — 居中标题

```kotlin
@Composable
private fun PlayerNavBar(title: String, artist: String, platform: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(150)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = rpx(24))) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(IconSizes.normal),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = FontSizes.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = rpx(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = FontSizes.subTitle,
                        maxLines = 1,
                    )
                    platform?.let {
                        Spacer(Modifier.width(rpx(8)))
                        // 平台 Tag
                        Text(
                            text = it,
                            fontSize = FontSizes.tag,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(rpx(4)))
                                .padding(horizontal = rpx(8), vertical = rpx(2)),
                        )
                    }
                }
            }
        }
        // 右侧分享按钮（占位）
        IconButton(onClick = { /* TODO: share */ }, modifier = Modifier.padding(end = rpx(24))) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = "分享",
                tint = Color.White,
                modifier = Modifier.size(IconSizes.normal),
            )
        }
    }
}
```

### 5.2 PlayerCoverArt — 封面

```kotlin
@Composable
private fun PlayerCoverArt(artworkUrl: String?, modifier: Modifier = Modifier) {
    val coverSize = rpx(500)
    Box(
        modifier = modifier.size(coverSize),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrl.isNullOrBlank()) {
            // 占位符
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),  // 或 musical-note
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(rpx(120)),
                )
            }
        } else {
            AsyncImage(
                model = artworkUrl,
                contentDescription = "专辑封面",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
```

### 5.3 PlayerOperationsBar — 操作栏 UI 占位

```kotlin
@Composable
private fun PlayerOperationsBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(80))
            .padding(horizontal = rpx(48)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 6 个图标按钮（全部 UI 占位，点击无功能）
        val iconTint = Color.White.copy(alpha = 0.7f)
        val iconSize = IconSizes.normal
        Icon(painterResource(R.drawable.ic_heart_outline), null, tint = iconTint, modifier = Modifier.size(iconSize))
        // 品质图标（PNG）— 简单用 Text 占位
        Text("标准", color = iconTint, fontSize = FontSizes.tag)
        Icon(painterResource(R.drawable.ic_arrow_down_tray), null, tint = iconTint, modifier = Modifier.size(iconSize))
        // 倍速图标（PNG）— 简单用 Text 占位
        Text("1.0x", color = iconTint, fontSize = FontSizes.tag)
        Icon(painterResource(R.drawable.ic_chat_bubble), null, tint = iconTint, modifier = Modifier.size(iconSize))
        Icon(painterResource(R.drawable.ic_ellipsis_vertical), null, tint = iconTint, modifier = Modifier.size(iconSize))
    }
}
```

### 5.4 PlayerSeekBar — 进度条

```kotlin
@Composable
private fun PlayerSeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    // 保留现有 SeekBar 逻辑，修改配色:
    // thumbColor: Color(0xFFDDDDDD)
    // activeTrackColor: Color(0xFFCCCCCC)
    // inactiveTrackColor: Color(0xFF999999)
    // 时间文字色: Color(0xFFCCCCCC)
    // 时间在 Slider 两侧（不是下方）
}
```

### 5.5 PlayerControls — 播放控制

```kotlin
@Composable
private fun PlayerControls(...) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(100))
            .padding(top = rpx(36), start = rpx(24), end = rpx(24)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 用 RN 图标替换 Material Icons:
        // shuffle → ic_shuffle / ic_repeat_song / ic_repeat_song_1 (根据 repeatMode)
        // skip-left → ic_skip_left, size rpx(56)
        // play/pause → ic_play / ic_pause, size rpx(96)
        // skip-right → ic_skip_right, size rpx(56)
        // playlist → ic_playlist, size rpx(56)
        // 所有图标 tint = Color.White
    }
}
```

**新增 import**: `import androidx.compose.ui.draw.blur` (for `Modifier.blur()`)

---

## 步骤 6: MiniPlayer 修复

### 6.1 MiniPlayerContent.kt — 封面图加载

**文件**: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContent.kt`

**当前第 80-94 行** (placeholder Box):
```kotlin
Box(
    modifier = Modifier
        .size(rpx(96))
        .clip(CircleShape)
        .background(MusicFreeTheme.colors.placeholder),
    contentAlignment = Alignment.Center,
) {
    Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = MusicFreeTheme.colors.textSecondary,
        modifier = Modifier.size(rpx(48)),
    )
}
```

**替换为**:
```kotlin
SubcomposeAsyncImage(
    model = uiModel.coverUri,
    contentDescription = null,
    modifier = Modifier
        .size(rpx(96))
        .clip(CircleShape),
    contentScale = ContentScale.Crop,
    loading = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MusicFreeTheme.colors.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, null, tint = MusicFreeTheme.colors.textSecondary, modifier = Modifier.size(rpx(48)))
        }
    },
    error = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MusicFreeTheme.colors.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, null, tint = MusicFreeTheme.colors.textSecondary, modifier = Modifier.size(rpx(48)))
        }
    },
)
```

**新增 import**:
```kotlin
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
```

### 6.2 MainActivity.kt — 统一 MiniPlayer

**文件**: `app/src/main/java/com/hank/musicfree/MainActivity.kt`

**删除第 55-56 行** (mock 状态变量):
```kotlin
var isHomeMockPlaying by rememberSaveable { mutableStateOf(true) }
var mockSongIndex by remember { mutableIntStateOf(0) }
```

**修改第 60 行**:
```kotlin
// 原: val showRealMiniPlayer = destination != null && !isHomeRoute && !isPlayerRoute
// 新:
val showMiniPlayer = destination != null && !isPlayerRoute
```

**替换第 68-89 行** (bottomBar 内容):
```kotlin
bottomBar = {
    if (showMiniPlayer) {
        MiniPlayer(
            onNavigateToPlayer = {
                navController.navigate(PlayerRoute)
            },
        )
    }
},
```

**删除不再使用的 import**:
- `MiniPlayerContent`
- `MiniPlayerMockFactory`
- `mutableIntStateOf`
- `rememberSaveable` (如果没有其他用途)

---

## 步骤 7: 健壮性修复

### 7.1 MusicItemMediaExt.kt — URL 空值防护

**文件**: `player/src/main/java/com/hank/musicfree/player/ext/MusicItemMediaExt.kt`

**当前第 11 行**: `url?.let { builder.setUri(it) }`

**替换整个函数**:
```kotlin
fun MusicItem.toMediaItem(): MediaItem {
    val mediaUri = url
    check(!mediaUri.isNullOrBlank()) {
        "Cannot create MediaItem without URL for: $title ($platform:$id)"
    }

    val builder = MediaItem.Builder()
        .setMediaId("$platform:$id")
        .setUri(mediaUri)

    builder.setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                album?.let { setAlbumTitle(it) }
                artwork?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
    )

    return builder.build()
}
```

### 7.2 PlayerController.kt — 错误事件

**文件**: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`

**在类顶部添加** (现有 `_playerState` 附近):
```kotlin
private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()
```

**修改 `setMediaItemAndPlay`** (第 240-247 行):
```kotlin
private fun setMediaItemAndPlay(item: MusicItem) {
    withConnectedController { controller ->
        try {
            recordHistory(item)
            val mediaItem = item.toMediaItem()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } catch (e: IllegalStateException) {
            scope.launch { _errorEvents.emit("播放失败: ${e.message}") }
        }
    }
}
```

**修改 `withConnectedController` 的 onFailure** (第 272-273 行):
```kotlin
.onFailure { e ->
    scope.launch { _errorEvents.emit("播放服务连接失败: ${e.message}") }
    return@launch
}
```

**新增 import**:
```kotlin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
```

### 7.3 PlayerScreen 中消费错误事件

在步骤 5 重写的 PlayerScreen 中添加:
```kotlin
val errorEvents = viewModel.errorEvents  // 需要在 PlayerViewModel 中暴露
LaunchedEffect(Unit) {
    errorEvents.collect { message ->
        // 使用 SnackbarHostState 或 Toast 展示
    }
}
```

PlayerViewModel 中新增:
```kotlin
val errorEvents: SharedFlow<String> = playerController.errorEvents
```

---

## 步骤 8: 页面过渡动画微调

### 文件: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`

**当前第 67/73/78/84 行**: `tween(100)`

**修改为**: `tween(250)` (所有 4 处)

250ms 更接近 iOS React Navigation 的默认过渡时长。运行后如不满意可继续微调。

---

## 步骤 9: 编译与测试

### 9.1 编译验证
```bash
./gradlew assembleDebug
```

### 9.2 更新测试

**SearchViewModelTest.kt** 需要完全重写以适配新的 ViewModel 接口:
- 测试全源并行搜索
- 测试 Tab 切换
- 测试搜索历史
- 测试分页

**PlayerViewModelTest.kt** 需要新增 errorEvents 测试。

**MiniPlayerContentTest.kt** 需要更新封面图测试。

### 9.3 运行时验收

1. 安装订阅源: 设置 → 插件管理 → 订阅 → 添加 `https://example.invalid/subscription.json`
2. 搜索: 输入"周杰伦"，验证全源并行搜索 + 两层 Tab
3. 播放: 点击搜索结果，验证:
   - 播放器模糊背景
   - 标题居中
   - 操作栏图标显示
   - SeekBar 样式
   - 控制按钮图标
4. MiniPlayer: 返回到首页/其他页面，验证:
   - 封面图加载
   - 首页显示真实 MiniPlayer
5. 添加到歌单: 搜索结果三点菜单 → 添加到歌单
6. 封面图: 检查日志确认 artwork URL 是否正确传递

---

## 执行顺序与依赖关系

```
步骤 0 (图标资源) ─────────────────────────────────┐
步骤 1 (DataStore 搜索历史) ──┐                      │
步骤 2 (SearchUiState) ──────┤                      │
步骤 3 (SearchViewModel) ────┤── 步骤 4 (SearchScreen) ── 步骤 9
步骤 7.1 (URL 防护) ─────────┤                      │
步骤 7.2 (错误事件) ─────────┤── 步骤 5 (PlayerScreen) ─┤
步骤 6 (MiniPlayer) ─────────┘── 步骤 8 (动画微调) ────┘
```

**推荐执行顺序**: 0 → 1 → 2 → 3 → 7.1 → 7.2 → 5 → 6 → 4 → 8 → 9

先完成基础设施（图标、DataStore、状态模型、健壮性），再做 UI 层改动，最后集成测试。

---

## 关键注意事项

1. **SVG 转换**: Android 不直接支持 SVG，必须转为 Vector Drawable XML。SVG 中的 `stroke="currentColor"` 需改为 `android:fillColor` 或 `android:strokeColor`，运行时通过 `tint` 控制颜色。
2. **Modifier.blur()**: 需要 API 31+ (Android 12)。本项目 minSdk=29，需要检查 `Modifier.blur()` 在 API 29-30 上的表现。如果不支持，需要用 Coil 的 `BlurTransformation` 作为 fallback，或改用 `RenderEffect` 并在低版本降级为纯色。
3. **`FlowRow`**: 需要 `foundation-layout` 依赖，确认 Compose BOM 2026.02.01 已包含。
4. **ScrollableTabRow**: Material3 的 `ScrollableTabRow` 支持自定义指示器，第二层 Tab 通过 `indicator = {}` (空 lambda) 去掉指示器。
5. **搜索结果中的 `items: List<Any>`**: PluginSearchState.Success 的 items 类型为 `List<Any>`，因为不同媒体类型返回不同模型。在 UI 层需要根据 `selectedMediaType` 做类型转换。
6. **resolveAndPlay** 中的 `_selectedPlatform` 变化: 新 ViewModel 中 platform 来自 `_selectedPlatform` 而非旧的 `_selectedPlugin`，逻辑等价。
7. **MusicMatch.kt** 和 **MusicMatch 相关 fallback 逻辑**: 保持不变，从旧 ViewModel 直接搬入新 ViewModel。
