# 搜索 → 播放 → 歌单 完整链路设计

**日期**: 2026-04-15
**状态**: 当前规范
**范围**: 搜索页重构、播放器 UI 对齐、MiniPlayer 修复、歌单添加入口、健壮性修复

## 1. 核心目标

全量对齐 RN 原版（`../MusicFree`）的搜索页 UI、播放器 UI、MiniPlayer，并修复链路中的功能断裂与健壮性问题。

## 2. 搜索页重构

### 2.1 搜索栏（替换当前 TopAppBar + OutlinedTextField）

**对标**: `../MusicFree/src/pages/searchPage/components/navBar.tsx`

去掉 Material3 TopAppBar，改为自定义 Row：

```
Row (高 rpx(88), 水平 padding rpx(24))
├── IconButton (返回箭头, icon: arrow-left.svg)
├── SearchBarContainer (flex:1, 药丸形)
│   ├── Icon (magnifying-glass.svg, 左侧 rpx(16))
│   ├── BasicTextField (高 rpx(64), 圆角 rpx(64), 水平 padding rpx(64))
│   └── IconButton (x-mark.svg, 右侧 rpx(16), 仅有内容时显示)
└── TextButton ("搜索", padding rpx(24))
```

- 搜索框背景色: `MusicFreeTheme.colors.pageBackground`
- 按下"搜索"或 IME Search → 触发全源搜索

### 2.2 搜索历史面板

**对标**: `../MusicFree/src/pages/searchPage/components/historyPanel.tsx`

初始状态（未搜索时）显示历史面板：

```
Column (padding rpx(24))
├── Row (标题 "搜索历史" + "清空" 按钮)
└── FlowRow (rpx(24) gap)
    └── Chip × N (历史关键词, 点击触发搜索)
```

- 存储: DataStore，最多 20 条，去重 + 最新置顶
- 新增 `AppPreferences.searchHistory: Flow<List<String>>`
- 新增 `AppPreferences.addSearchQuery(query)` / `clearSearchHistory()`

### 2.3 两层 Tab 结构

**对标**: `../MusicFree/src/pages/searchPage/components/resultPanel/`

搜索结果出现后切换到结果面板：

```
Column
├── ScrollableTabRow (第一层: 媒体类型)
│   └── Tab × 4: 单曲 / 专辑 / 歌手 / 歌单
├── ScrollableTabRow (第二层: 插件源, 无指示器)
│   └── Tab × N: 按 PluginManager 排序的可搜索插件名
└── LazyColumn / LazyVerticalGrid (结果列表)
```

**第一层 Tab 样式**:
- 宽 `rpx(160)` per tab, 底部指示器高 `rpx(4)`, primary 色
- 选中: bold, primary 色; 未选中: medium, text 色

**第二层 Tab 样式**:
- 宽 `rpx(140)` per tab, **无底部指示器**
- 选中: bold, primary 色; 未选中: medium, text 色

### 2.4 全源并行搜索

**对标**: `../MusicFree/src/pages/searchPage/hooks/useSearch.ts`

SearchViewModel 重构:

```kotlin
// 搜索状态: 按 (mediaType, platform) 二维索引
val searchResults: StateFlow<Map<String, Map<String, PluginSearchState>>>

fun searchAll(query: String) {
    // 对所有可搜索插件并行发起搜索
    availablePlugins.forEach { plugin ->
        viewModelScope.launch {
            val result = plugin.search(query, page = 1, type = currentMediaType)
            // 更新对应 (mediaType, platform) 的状态
        }
    }
}
```

- `PluginSearchState`: Idle / Loading / Success(items, isEnd, page) / Error(message)
- 切换第一层 Tab（媒体类型）时，如该类型未搜索过，自动触发该类型的全源搜索
- 切换第二层 Tab（插件源）仅切换显示，不重新搜索
- 分页: `loadMore(platform, mediaType)` 加载下一页

### 2.5 搜索结果项

**对标**: `../MusicFree/src/components/mediaItem/musicItem.tsx`

单曲结果项:
```
Row (高 rpx(120), 水平 padding rpx(24))
├── AsyncImage (rpx(80) × rpx(80), 圆角 rpx(16))
├── Column (flex:1, margin-start rpx(24))
│   ├── Text (标题 + 平台 Tag, fontSize content rpx(28))
│   └── Text (歌手 · 专辑, fontSize description rpx(22), margin-top rpx(16))
└── IconButton (ellipsis-vertical.svg, 宽 rpx(48))
    └── onClick → 弹出选项菜单
```

专辑/歌手结果项: 相同高度 `rpx(120)`，布局类似。
歌单结果项: 网格布局，竖屏 3 列，封面 `rpx(210)×rpx(210)`, 圆角 `rpx(12)`。

### 2.6 三点菜单 / 选项面板

点击三点菜单弹出 BottomSheet 或 Dialog，包含:
- 添加到歌单（复用 `AddToPlaylistDialog`）
- 下一首播放

本次实现: **添加到歌单** + **下一首播放**，其余选项后续迭代。

### 2.7 页面过渡动画

当前全局使用 `slideIntoContainer` 100ms。RN 使用 React Navigation 默认过渡（iOS 风格）。
保持当前滑动方向（Start→End），但可调整时长至 200-300ms 以更接近 iOS 默认过渡手感。运行时对比后微调。

## 3. 播放器 UI 对齐

### 3.1 高斯模糊背景

**对标**: `../MusicFree/src/pages/musicDetail/components/background.tsx`

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // 底层: 纯黑
    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    // 上层: 封面模糊
    AsyncImage(
        model = currentItem?.artwork,
        modifier = Modifier
            .fillMaxSize()
            .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .alpha(0.5f),
        contentScale = ContentScale.Crop,
    )
    // 内容层
    Column { /* NavBar + Cover + Operations + SeekBar + Controls */ }
}
```

### 3.2 顶部导航栏（居中标题）

**对标**: `../MusicFree/src/pages/musicDetail/components/navBar.tsx`

去掉 Material3 TopAppBar，改为自定义 Row:

```
Row (高 rpx(150), verticalAlignment CenterVertically)
├── IconButton (arrow-left.svg, margin rpx(24))
├── Column (flex:1, horizontalAlignment CenterHorizontally, verticalArrangement Center)
│   ├── Text (标题, fontWeight semibold, fontSize title, maxLines 1)
│   └── Row (margin-top rpx(12))
│       ├── Text (歌手, fontSize subTitle)
│       └── Tag (平台名, 小圆角背景)
└── IconButton (share.svg, margin rpx(24))
```

### 3.3 封面区域

**对标**: `../MusicFree/src/pages/musicDetail/components/content/albumCover/index.tsx`

- 竖屏尺寸: `rpx(500) × rpx(500)`, 圆角 12dp
- 居中显示, 使用 AsyncImage + placeholder
- 点击: 预留（切换封面/歌词视图，本次不实现）

### 3.4 操作栏（UI 占位）

**对标**: `../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx`

```
Row (高 rpx(80), horizontalArrangement SpaceAround)
├── heart-outline.svg (收藏)
├── Image (品质图标 PNG, 预留)
├── arrow-down-tray.svg (下载)
├── Image (倍速图标 PNG, 预留)
├── chat-bubble-oval-left-ellipsis.svg (评论)
└── ellipsis-vertical.svg (更多)
```

本次仅做 UI 展示，点击无功能（或 Toast 提示"即将推出"）。

### 3.5 进度条

**对标**: `../MusicFree/src/pages/musicDetail/components/bottom/seekBar.tsx`

```
Row (高 rpx(40))
├── Text (当前时间, fontSize description, color #cccccc)
├── Slider (flex:1, 73% 宽度区域)
│   minimumTrackColor: #cccccc
│   maximumTrackColor: #999999
│   thumbColor: #dddddd
└── Text (总时长, fontSize description, color #cccccc)
```

### 3.6 播放控制按钮

**对标**: `../MusicFree/src/pages/musicDetail/components/bottom/playControl.tsx`

```
Row (高 rpx(100), margin-top rpx(36), horizontalArrangement SpaceAround)
├── shuffle.svg / repeat-song.svg / repeat-song-1.svg (rpx(56))
├── skip-left.svg (rpx(56))
├── play.svg / pause.svg (rpx(96), 中心大按钮)
├── skip-right.svg (rpx(56))
└── playlist.svg (rpx(56))
```

所有图标白色 (`Color.White`)。

## 4. MiniPlayer 修复

### 4.1 封面图加载

**文件**: `MiniPlayerContent.kt`

将占位符替换为:
```kotlin
AsyncImage(
    model = uiModel.coverUri,
    modifier = Modifier
        .size(rpx(96))
        .clip(CircleShape),
    placeholder = painterResource(R.drawable.album_default),
    contentScale = ContentScale.Crop,
)
```

### 4.2 首页统一真实 MiniPlayer

**文件**: `MainActivity.kt`

去掉 `isHomeRoute` 分支中的 mock MiniPlayer，统一为:
```kotlin
val showMiniPlayer = destination != null && !isPlayerRoute
// bottomBar 中统一使用 MiniPlayer(onNavigateToPlayer = ...)
```

### 4.3 尺寸对齐

- 整体高度: `rpx(132)`
- 封面: `rpx(96) × rpx(96)`, 圆形
- 播放按钮半径: `rpx(36)`, 进度环: 活动 `rpx(4)` / 非活动 `rpx(2)`
- 歌单图标: `rpx(56)`, margin-left `rpx(36)`

## 5. 添加到歌单入口

在搜索结果项的三点菜单中新增"添加到歌单"选项:

方案: `:feature:search` 模块已依赖 `:data`（含 `PlaylistRepository`），在 search 模块中新建 `AddToPlaylistBottomSheet` composable，直接注入 `PlaylistRepository` 获取歌单列表并执行添加。不复用 `feature/home` 的 `AddToPlaylistDialog`（避免跨 feature 模块依赖），但保持 UI 一致。

## 6. 健壮性修复

### 6.1 URL 空值防护

**文件**: `MusicItemMediaExt.kt`

```kotlin
fun MusicItem.toMediaItem(): MediaItem {
    val uri = url
    require(!uri.isNullOrBlank()) { "MusicItem.url is required for playback: $title" }
    // ...
    builder.setUri(uri)
}
```

在 `PlayerController.setMediaItemAndPlay()` 中 catch 并通过 SharedFlow 发送错误事件。

### 6.2 MediaController 连接失败反馈

**文件**: `PlayerController.kt`

```kotlin
private val _errorEvents = MutableSharedFlow<String>()
val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()
```

在 `withConnectedController` 连接失败时:
```kotlin
.onFailure { e ->
    scope.launch { _errorEvents.emit("播放服务连接失败: ${e.message}") }
}
```

### 6.3 封面图诊断

- 在 `JsBridge.toMusicItem()` 中添加 debug 日志: `Log.d("JsBridge", "artwork=$artwork for ${title}")`
- Coil ImageLoader 配置自定义 OkHttpClient，添加 `Referer` header 以防资源服务器拦截

## 7. 图标资源

从 `../MusicFree/src/assets/icons/` 拷贝所需 SVG 文件到 Android drawable 资源目录。

本次需要的图标 (从 RN 73 个 SVG 中选取):

**搜索页**: arrow-left, magnifying-glass, x-mark, ellipsis-vertical
**播放器**: arrow-left, share, heart-outline, heart, arrow-down-tray, chat-bubble-oval-left-ellipsis, ellipsis-vertical, shuffle, repeat-song, repeat-song-1, skip-left, skip-right, play, pause, playlist
**MiniPlayer**: play, pause, playlist
**选项菜单**: folder-plus, motion-play

去重后约 **18 个 SVG** 文件需拷贝。存放路径: `core/src/main/res/drawable/`，命名转换: `kebab-case` → `snake_case`（如 `arrow-left.svg` → `ic_arrow_left.xml`）。

PNG 资源（品质/倍速图标）: 从 `../MusicFree/src/assets/imgs/` 拷贝 `*-quality.png` 和 `*x.png`，本次仅做 UI 占位。

## 8. 不在本次范围

- 歌词显示
- 播放品质选择功能
- 下载功能
- 评论功能
- 倍速播放功能
- 横屏适配
- 歌单封面定制
- 可视化动画
- 搜索结果选项面板完整功能（仅实现"添加到歌单"和"下一首播放"）

## 9. 关键文件变更清单

| 文件 | 变更类型 |
|------|---------|
| `feature/search/SearchScreen.kt` | **重写** — 新搜索栏 + 两层 Tab + 历史面板 |
| `feature/search/SearchViewModel.kt` | **重写** — 全源并行搜索 + 二维状态管理 |
| `feature/search/SearchUiState.kt` | **重写** — 新状态模型 |
| `feature/player-ui/PlayerScreen.kt` | **重写** — 模糊背景 + 居中标题 + 操作栏 + 尺寸对齐 |
| `feature/player-ui/component/MiniPlayerContent.kt` | **修改** — 封面图加载 + 尺寸对齐 |
| `feature/player-ui/component/MiniPlayer.kt` | **微调** — 尺寸 |
| `app/MainActivity.kt` | **修改** — 统一 MiniPlayer |
| `data/datastore/AppPreferences.kt` | **扩展** — 搜索历史 |
| `player/MusicItemMediaExt.kt` | **修改** — URL 空值防护 |
| `player/controller/PlayerController.kt` | **修改** — 错误事件 + 连接失败反馈 |
| `core/src/main/res/drawable/` | **新增** — 18 个 SVG 图标资源 |
