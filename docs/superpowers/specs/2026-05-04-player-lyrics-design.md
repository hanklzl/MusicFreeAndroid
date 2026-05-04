# 播放页歌词设计

> 文档状态：当前规范
> 适用范围：播放页歌词功能设计，覆盖 Android 原生播放页内歌词，不覆盖悬浮窗/桌面歌词。
> 直接执行：是（作为后续实施计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：`../MusicFree` RN 原版播放页歌词、当前 Android `:feature:player-ui` / `:plugin` / `:data` 实现。
> 最后校验：2026-05-04

## 概要

实现播放页歌词功能，对齐 RN 原版的核心播放页体验：

- 播放页封面与歌词页可相互切换。
- 歌词由插件 `getLyric()` 加载，缺失时默认跨插件自动搜索相似歌词。
- 歌词随播放进度高亮并自动滚动。
- 用户可拖动歌词列表，用中线播放按钮跳转到目标歌词时间。
- 支持翻译、字体大小、歌词偏移、歌词搜索/关联、本地歌词导入。

本设计不实现 Android 悬浮窗/桌面歌词，不新增独立 Gradle 模块；使用现有 `:core`、`:data`、`:plugin`、`:feature:player-ui` 边界完成。

## 已确认决策

用户已确认以下边界：

1. 范围选择：实现 RN 播放页歌词主体验，包含翻译、字体大小、偏移、搜索/关联歌词、本地歌词导入。
2. 非目标：暂不做悬浮窗/桌面歌词。
3. 自动搜索：默认开启，当前歌曲插件无歌词时自动搜索其他支持 `search(type = "lyric")` 的插件。
4. 播放页切换：对齐 RN，点击封面切到歌词，点击歌词空白处切回封面；歌词图标保留为可发现入口。
5. 架构方向：采用歌词仓储/加载器边界，播放器 UI 只消费状态，不把歌词加载逻辑堆进 `PlayerViewModel`。
6. 开发方式：后续开发必须使用 git worktree，当前设计分支为 `feat-player-lyrics`，worktree 为 `.worktrees/feat-player-lyrics`。

## 当前 Android 事实

当前仓库已经具备部分基础能力：

- 播放页：
  - `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
  - `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- 播放状态：
  - `player/src/main/java/com/zili/android/musicfreeandroid/player/model/PlayerState.kt`
  - `PlayerState.position` 约每 200ms 更新，可驱动歌词高亮。
- 歌词模型：
  - `core/src/main/java/com/zili/android/musicfreeandroid/core/model/LyricLine.kt`
  - 当前只有 `timeMs` 和 `text`，不足以表达翻译、行号、meta、来源。
- 插件歌词 API：
  - `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt`
  - `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginModels.kt`
  - `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
  - 当前 `LyricResult` 解析 `rawLrc/rawLrcTxt`，未解析 RN 插件常见的 `translation/trans`。
- 歌曲详情页已有静态歌词预览：
  - `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailViewModel.kt`
  - `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailScreen.kt`
- 持久化基础：
  - `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
  - `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`
  - 当前数据库使用 `fallbackToDestructiveMigration(dropAllTables = true)`，新增表和 schema 可控。

## RN 参考事实

实现时优先对照以下 RN 文件：

- `../MusicFree/src/core/lyricManager.ts`
  - 当前歌曲变化时刷新歌词。
  - 播放进度更新时更新当前歌词行。
  - 支持关联歌词、本地歌词、歌词偏移、自动搜索相似歌词。
- `../MusicFree/src/utils/lrcParser.ts`
  - 支持 LRC meta、时间戳、多时间戳、纯文本 fallback、翻译按时间戳合并。
- `../MusicFree/src/pages/musicDetail/components/content/index.tsx`
  - 竖屏点击封面/歌词在 album 和 lyric 间切换。
- `../MusicFree/src/pages/musicDetail/components/content/lyric/index.tsx`
  - `FlatList` 自动滚动当前歌词。
  - 手动拖动时显示中线时间、水平线、播放按钮。
- `../MusicFree/src/pages/musicDetail/components/content/lyric/lyricOperations.tsx`
  - 字体大小、歌词偏移、搜索、翻译、更多操作入口。
- `../MusicFree/src/core/pluginManager/plugin.ts`
  - `getLyric()` 加载顺序：关联歌词、本地歌词、缓存、自带歌词、插件远程歌词、本地文件 fallback。

## 非目标

第一版不做：

- Android 悬浮窗/桌面歌词。
- 横屏专项视觉复刻。竖屏完整可用是第一验收重点；横屏只要求布局不崩。
- 歌词编辑器。
- 歌词缓存文件落盘管理和“清除歌词缓存”设置页入口。
- 网络歌词 URL 下载的完整兼容层。当前插件桥主要支持直接返回 `rawLrc/rawLrcTxt/translation`；若插件仅返回旧 `lrc` URL，第一版可记录为后续兼容项，除非实现时发现主流测试插件依赖它。

## 架构

采用“领域解析器 + data 层 repository + 插件感知 feature loader + UI 状态”的边界。

重要模块约束：`AGENTS.md` 定义依赖方向为 `:app → :feature:* → :data, :player, :plugin → :core`。因此 `:data` 不能依赖 `:plugin`，插件调用不能放进 `:data` 模块。歌词实现必须拆为：

- `:data`：只负责 Room 缓存、每首歌歌词 metadata、全局歌词偏好。
- `:feature:player-ui`：依赖 `:data`、`:plugin`、`:player`，负责插件歌词加载、自动搜索、旧请求结果保护和播放页 UI 状态。

### `:core`

新增或扩展歌词领域模型：

```kotlin
data class ParsedLyricLine(
    val index: Int,
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

data class LyricDocument(
    val musicId: String,
    val musicPlatform: String,
    val lines: List<ParsedLyricLine>,
    val metaOffsetMs: Long = 0L,
    val source: LyricSourceInfo,
    val rawLrc: String? = null,
    val rawLrcTxt: String? = null,
    val translationRaw: String? = null,
) {
    val hasTranslation: Boolean get() = lines.any { !it.translation.isNullOrBlank() }
}

data class RawLyricPayload(
    val rawLrc: String? = null,
    val rawLrcTxt: String? = null,
    val translation: String? = null,
)
```

`LyricSourceInfo` 应能表达来源：

- `Plugin(platform)`
- `AutoSearch(platform, title, id)`
- `Associated(platform, title, id)`
- `LocalRaw`
- `LocalTranslation`
- `Cache`

新增解析器建议放在 `core`，例如 `core/model` 或 `core/lyric`：

- 输入：`rawLrc`、`rawLrcTxt`、`translation`。
- 输出：`LyricDocument` 或中间解析结果。
- 行为：
  - 支持 `[mm:ss.xx]`、`[hh:mm:ss.xx]` 形式。
  - 支持一行多个时间戳。
  - 支持 `[offset:123]` meta，单位毫秒。
  - 无时间戳但有文本时按纯文本行生成 `timeMs = 0` 的静态歌词。
  - 翻译按相同 `timeMs` 合并；无匹配时翻译为空。
  - 保持空歌词行为：没有有效文本时返回空行列表。

### `:plugin`

补齐 `LyricResult`：

```kotlin
data class LyricResult(
    val rawLrc: String?,
    val rawLrcTxt: String?,
    val translation: String?,
    val lines: List<LyricLine>, // 可保留兼容，后续 UI 不直接依赖它
)
```

`JsBridge.parseLyricResult()` 需要识别：

- `rawLrc`
- `lrc`
- `lyric`
- `rawLrcTxt`
- `txt`
- `translation`
- `trans`

`LoadedPlugin.getLyric()` 保持现有方法签名即可，返回增强后的 `LyricResult`。

### `:data`

新增 `LyricRepository`，职责限定为歌词持久化和偏好，不直接调用插件：

- 读写远程歌词缓存。
- 读写本地导入歌词和本地翻译。
- 读写关联歌词目标。
- 读写每首歌歌词偏移。
- 暴露全局歌词偏好。

建议 API：

```kotlin
class LyricRepository {
    fun observeCache(music: MusicItem): Flow<LyricCache?>
    suspend fun getCache(music: MusicItem): LyricCache?
    suspend fun saveRemoteLyric(music: MusicItem, source: LyricSourceInfo, payload: RawLyricPayload)
    suspend fun associateLyric(music: MusicItem, target: MusicItem)
    suspend fun clearAssociatedLyric(music: MusicItem)
    suspend fun importLocalLyric(music: MusicItem, rawText: String, kind: LocalLyricKind)
    suspend fun deleteLocalLyric(music: MusicItem)
    suspend fun setLyricOffset(music: MusicItem, offsetMs: Long)
}
```

`LyricCache` 是 data 层领域包装，包含 Room entity 中的缓存、本地歌词、关联目标和 offset。

### `:feature:player-ui`

新增插件感知加载用例，建议命名为 `PlayerLyricLoader` 或 `PlayerLyricsRepository`，放在 `feature/player-ui`。它负责组合 `LyricRepository`、`PluginManager`、`AppPreferences` 和 core 解析器：

```kotlin
sealed interface LyricLoadState {
    data object NoTrack : LyricLoadState
    data class Loading(val music: MusicItem) : LyricLoadState
    data class Ready(
        val music: MusicItem,
        val document: LyricDocument,
        val userOffsetMs: Long,
    ) : LyricLoadState
    data class NoLyric(val music: MusicItem) : LyricLoadState
    data class Error(val music: MusicItem, val message: String) : LyricLoadState
}

class PlayerLyricLoader {
    fun observeLyrics(music: MusicItem?): Flow<LyricLoadState>
    suspend fun refresh(music: MusicItem, forceRemote: Boolean = false)
    suspend fun searchCandidates(music: MusicItem, query: String = defaultQuery(music)): List<LyricSearchGroup>
    suspend fun associateLyric(music: MusicItem, target: MusicItem)
    suspend fun clearAssociatedLyric(music: MusicItem)
    suspend fun importLocalLyric(music: MusicItem, rawText: String, kind: LocalLyricKind)
    suspend fun deleteLocalLyric(music: MusicItem)
    suspend fun setLyricOffset(music: MusicItem, offsetMs: Long)
}
```

`LyricSearchGroup` 按插件聚合候选，候选类型复用 `MusicItem`，因为 RN 的歌词候选本质也是 media base。

### Room 持久化

新增表用于每首歌歌词状态。建议实体：

```kotlin
@Entity(
    tableName = "lyric_cache",
    primaryKeys = ["musicId", "musicPlatform"],
)
data class LyricCacheEntity(
    val musicId: String,
    val musicPlatform: String,
    val remoteRawLrc: String?,
    val remoteRawLrcTxt: String?,
    val remoteTranslation: String?,
    val remoteSourceType: String?,
    val remoteSourcePlatform: String?,
    val remoteSourceMusicId: String?,
    val remoteSourceTitle: String?,
    val localRawLrc: String?,
    val localTranslation: String?,
    val associatedMusicJson: String?,
    val userOffsetMs: Long,
    val updatedAt: Long,
)
```

说明：

- 本地导入内容直接保存文本，不复制外部文件，避免 SAF 权限生命周期复杂化。
- `associatedMusicJson` 可用现有 Room converter 或新增 converter 序列化 `MusicItem`。若 converter 不适合 `MusicItem`，存为 `String` 并在 repository 内解析。
- 新增 DAO：
  - `observeByKey(platform, id): Flow<LyricCacheEntity?>`
  - `getByKey(platform, id): LyricCacheEntity?`
  - `upsert(entity)`
  - `deleteLocalLyrics(platform, id)`
  - `clearAssociation(platform, id)`
  - `setOffset(platform, id, offsetMs)`
- `AppDatabase` version 递增并导出 schema。当前 destructive migration 可继续沿用，但 schema 必须更新。

### App 偏好

扩展 `AppPreferences`：

- `lyric_show_translation: Boolean`，默认 `false`。
- `lyric_detail_font_size: Int`，默认 `1`，有效值 `0..3`。
- `lyric_auto_search_enabled: Boolean`，默认 `true`。

字体档位对齐 RN：

- `0 -> rpx(24)`
- `1 -> rpx(30)`
- `2 -> rpx(36)`
- `3 -> rpx(42)`

## 加载算法

`PlayerLyricLoader` 对每首歌执行以下优先级：

1. 若没有当前歌曲，返回 `NoTrack`。
2. 通过 `LyricRepository` 读取 `LyricCacheEntity`。
3. 若当前歌曲有 `localRawLrc` 或 `localTranslation`，优先生成本地歌词文档。Android 第一版明确让“用户手动导入到当前歌曲的歌词”优先于关联目标，避免导入后仍被关联歌词覆盖。
4. 若有 `associatedMusicJson`，把关联目标作为歌词目标；否则歌词目标是当前歌曲。
5. 若有远程缓存 `remoteRawLrc/remoteRawLrcTxt/remoteTranslation`，且缓存来源匹配当前歌词目标：
   - 无关联时，缓存来源应匹配当前歌曲 `musicPlatform/musicId` 或为空的历史缓存。
   - 有关联时，缓存来源应匹配关联目标 `platform/id`。
   - 匹配时生成缓存歌词文档。
6. 若有关联目标，调用关联目标所属插件 `getLyric(associatedMusic)`；歌词状态仍归属当前播放歌曲。
7. 若无关联目标，调用当前歌曲所属插件 `getLyric(currentMusic)`。
8. 若插件返回有效歌词，写入当前歌曲的远程缓存字段，并记录 `remoteSourceType/sourcePlatform/sourceMusicId/sourceTitle` 后返回 `Ready`。
9. 若仍无歌词且 `lyric_auto_search_enabled == true`，搜索其他支持 `lyric` 搜索类型的插件：
   - 候选插件来自 `PluginManager.getSortedEnabledPlugins()` 或新增 `getLyricSearchablePlugins()`。
   - 对齐 RN：未声明 `supportedSearchType` 的旧插件视为 legacy 可搜索插件，应进入歌词候选；显式声明且不包含 `lyric` 的插件不进入候选。
   - 跳过当前播放歌曲所属平台，避免重复。
   - 对每个插件调用 `search(query, page = 1, type = "lyric")`。
   - 每个插件最多取前两个候选。
   - 优先精确匹配 `title == query && artist == current.artist`。
   - 否则使用轻量字符串距离选择最接近候选；可先实现简单归一化编辑距离，后续再优化。
   - 找到候选后调用候选插件 `getLyric(candidate)`。
   - 有效结果写入当前歌曲远程缓存，source 标记为 `AutoSearch`，并记录候选 `platform/id/title`。
10. 全部失败返回 `NoLyric`；插件异常不影响播放。

旧请求结果保护规则：

- 加载开始时记录 `musicKey`。
- 任何异步插件结果返回后，如果当前播放项已变化，丢弃结果。
- `PlayerViewModel` 订阅当前 item 的 flow，换歌时自动取消前一个加载 job。

## 偏移语义

Android 内部统一使用毫秒。

- LRC meta offset：来自 `[offset:...]`，字段为 `metaOffsetMs`。
- 用户偏移：来自 per-track `userOffsetMs`。
- 约定：`userOffsetMs > 0` 表示歌词“提前”，即同一播放位置显示更靠后的歌词。
- 当前行查找使用：

```kotlin
val lyricClockMs = playbackPositionMs + userOffsetMs - metaOffsetMs
```

- 从歌词行跳转播放使用：

```kotlin
val seekMs = (line.timeMs - userOffsetMs + metaOffsetMs)
    .coerceIn(0L, durationMs)
```

UI 文案：

- `0ms`：正常。
- 正数：提前 `x.xs`。
- 负数：延后 `x.xs`。

## 播放页 UI 设计

`PlayerScreen` 保持特殊 Chrome 页面，继续自行负责沉浸式状态栏和顶部 inset，遵守 `docs/ui-harness/screen-chrome-rules.md` 中 `PlayerRoute / PlayerScreen` 登记。

### 屏幕结构

当前播放页结构：

- 黑底。
- 模糊封面背景。
- 顶部 `PlayerNavBar`。
- 中部封面。
- 操作栏。
- seek bar。
- 播放控制。

目标结构：

- 顶部和底部区域保留。
- 中部内容改为 `PlayerMainContent`：
  - `AlbumCoverPage`
  - `LyricsPage`
- 屏内使用本地 UI 状态保存当前页：`Album` 或 `Lyrics`。

### 页面切换

- 默认页：`Album`。
- 点击封面：切换到 `Lyrics`。
- 点击歌词区域空白处：切换到 `Album`。
- 操作栏歌词图标：切换到 `Lyrics`，若已在歌词页则保持或切回封面，具体实现可用 toggle，但必须保证入口可发现。
- 换歌不强制回封面，除非实现中发现 RN 行为不同；第一版保留当前页更符合用户连续看歌词场景。

### 歌词页

布局：

- `LazyColumn` 占据中部主区域。
- 顶部和底部留白，使当前行可滚到视觉中心。
- 中线拖动浮层包含：
  - 目标时间 pill。
  - 水平线。
  - 播放按钮。

自动滚动：

- `Ready` 且歌词有时间戳时，根据 `PlayerState.position` 计算当前行。
- 播放中自动滚到当前行，`viewPosition` 近似 0.5。
- 暂停时不因 position 停止而反复调整列表。
- 用户拖动列表后进入拖动模式，2 秒无交互后退出；退出后若仍播放，恢复自动滚动。
- 纯文本歌词无精确时间戳，显示列表但不做逐行自动滚动。

行样式：

- 当前高亮行：主题主色或 RN 风格 primary，透明度 1。
- 普通行：白色，透明度约 0.6。
- 拖动目标行：白色，透明度约 0.9。
- 行文本居中，左右 padding 对齐 RN `rpx(64)`。
- 显示翻译时，原文和翻译用换行组合；翻译可用稍低透明度或同色。

空/错误状态：

- Loading：居中白色加载中。
- NoLyric：居中 “暂无歌词” + “搜索歌词”。
- Error：居中错误简述 + “重试” + “搜索歌词”。

### 歌词操作

播放页歌词操作栏对齐 RN 入口，第一版实现以下功能：

- 字体大小：4 档，持久化 `lyric_detail_font_size`，切换后滚回当前行。
- 歌词偏移：底部面板或对话框，支持提前、延后、重置，写入 per-track `userOffsetMs`。
- 搜索歌词：打开歌词搜索 bottom sheet。
- 翻译开关：有翻译时可切换；无翻译时禁用或 toast “当前歌曲无翻译”。
- 更多：
  - 导入本地歌词。
  - 导入本地歌词翻译。
  - 删除本地歌词。
  - 解除关联歌词。

现有 favorite/add-to-playlist 操作保持，不被歌词功能回归破坏。

## 歌词搜索与关联

歌词搜索 bottom sheet：

- 默认 query 为当前歌曲 `title`，若后续 `MusicItem` 支持 alias，可优先 alias。
- 展示所有支持 `lyric` 搜索的已启用插件。
- 每个插件有 loading/success/empty/error 状态。
- 结果项显示 title、artist、album/platform。
- 点击候选：
  1. 保存 `associatedMusicJson` 到当前歌曲。
  2. 调用候选插件 `getLyric(candidate)`。
  3. 缓存结果。
  4. 关闭 sheet。
  5. 刷新播放页歌词。

解除关联：

- 只清除 `associatedMusicJson`。
- 不删除当前歌曲本地歌词和远程缓存。
- 清除后重新按加载优先级解析当前歌曲歌词。

## 本地歌词导入

播放页更多菜单中提供：

- 导入本地歌词。
- 导入本地歌词翻译。
- 删除本地歌词。

导入使用 `ActivityResultContracts.OpenDocument()`：

- MIME 可使用 `text/*` 和 `application/octet-stream`，以兼容 `.lrc`。
- 读取 `contentResolver.openInputStream(uri).bufferedReader().use { it.readText() }`。
- 建议限制单文件大小或读取后限制文本长度，例如 512KB，防止异常大文件进入 Room。
- 保存到 `localRawLrc` 或 `localTranslation` 字段。
- 导入后刷新歌词并滚回当前行。

删除本地歌词：

- 清空 `localRawLrc` 和 `localTranslation`。
- 不清远程缓存和关联关系。
- 重新加载歌词。

## 错误处理

- 插件 `getLyric()` 异常：记录日志，继续 fallback。
- 单个自动搜索插件异常：该插件结果标记 error，不中断其他插件。
- 所有插件失败：`NoLyric` 或 `Error`，视是否有可恢复异常信息。
- 歌词解析失败：视作 `NoLyric`，并保留原始错误用于 debug 日志。
- 换歌时返回的旧请求结果必须丢弃。
- 本地导入读取失败：toast 或错误事件提示，不改变已有歌词。

## 测试策略

### `:core:test`

新增 parser 单测：

- LRC 时间戳解析。
- 一行多个时间戳。
- `[offset:...]` meta。
- 纯文本 fallback。
- 翻译按相同时间戳合并。
- 当前行查找：
  - 播放位置在第一句前返回 null 或 index 0 规则明确。
  - 播放位置在两句之间返回前一句。
  - 最后一行后返回最后一句。
- 用户 offset 语义：
  - 正数提前。
  - 负数延后。
  - seek 公式和 display 公式互逆。

### `:plugin:test`

扩展 `JsBridgeTest`：

- `translation` 被解析。
- `trans` 被解析。
- `rawLrcTxt` fallback 不覆盖 `rawLrc`。
- 空 payload 返回空行且不崩。

### `:data:test`

新增 repository/DAO 测试：

- 本地歌词优先于远程缓存。
- 远程缓存优先于插件请求。
- 插件歌词成功后写入缓存。
- 当前插件无歌词时自动搜索其他歌词插件。
- 自动搜索跳过当前平台。
- 自动搜索候选选择规则。
- 关联歌词保存、加载、解除。
- 用户 offset 保存和读取。
- 全局偏好默认值和写入。

如果纯 JVM Room 测试不适合当前配置，可使用 Robolectric 或 Android instrumentation；计划中必须明确具体命令。

### `:feature:player-ui:testDebugUnitTest`

新增 ViewModel/UI 状态测试：

- 当前播放项变化时订阅新歌词状态。
- `position` 变化更新当前高亮行。
- 无歌词时显示搜索入口状态。
- 翻译开关调用 preferences。
- 字体大小切换持久化。
- 歌词拖动 seek 调用 `PlayerController.seekTo()`，并调用 `play()`。
- 导入本地歌词成功触发 repository。

### 运行态验收

至少验证：

1. 安装或准备一个支持 `getLyric()` 的测试插件。
2. 搜索歌曲并播放。
3. 进入播放页，点击封面切到歌词。
4. 歌词 loading 后显示并随播放高亮/滚动。
5. 拖动歌词，点击中线播放按钮，播放进度跳转。
6. 当前插件无歌词时自动搜索其他歌词插件并展示。
7. 手动搜索歌词并关联候选。
8. 导入本地 `.lrc` 后优先展示本地歌词。
9. 导入翻译后翻译开关可用。
10. 设置偏移后当前行变化符合提前/延后语义。
11. 返回封面、播放控制、收藏、加入歌单不回归。

验收证据优先包含：

- 编译和单测命令输出。
- 设备或模拟器运行记录。
- 播放页截图。
- `uiautomator dump` 中关键文本/按钮存在性。

## 验证命令

实现后至少运行：

```bash
./gradlew :core:test :plugin:test :data:test :feature:player-ui:testDebugUnitTest
./gradlew :app:build
```

如果有设备或模拟器：

```bash
./gradlew connectedAndroidTest
```

如果完整 instrumentation 成本过高，必须说明未运行原因，并至少完成可运行的目标模块 instrumentation 或手动运行态验收。

## 实施计划交接要求

后续实施计划必须足够详细，便于新 session 直接执行。计划至少要包含：

1. git worktree 信息：使用 `.worktrees/feat-player-lyrics`，不得在主工作区实现。
2. 任务顺序：
   - 解析器/domain。
   - 插件翻译解析。
   - Room/entity/DAO/repository。
   - preferences。
   - player ViewModel state。
   - Compose UI。
   - search/association sheet。
   - local import。
   - tests。
   - runtime acceptance。
3. 每个任务的具体文件路径。
4. 每个任务的验证命令。
5. 可并行任务边界，但默认由单 session 顺序执行也能完成。
6. 明确不做桌面歌词。
7. 明确所有文档引用使用相对路径。

## 风险

- 自动搜索可能触发多个插件网络请求。应限制每插件候选数量，并允许后续设置关闭。
- Room 存储歌词文本可能增加数据库体积。第一版可接受；后续再设计文件缓存和清理。
- 纯文本歌词没有时间戳，不能精确同步。UI 必须清楚地降级为静态歌词。
- 当前 `MusicItem` 没有 alias 字段，自动搜索 query 先使用 title。
- `PluginManager.getSearchablePlugins()` 目前只面向 music 搜索，需要新增或扩展 lyric-searchable 查询，不能破坏搜索页现有逻辑。
- Compose `LazyColumn` 自动滚动和用户拖动可能互相抢控制，需要明确 dragging mode 锁定。
- 本地导入 `.lrc` 的 MIME 兼容性依赖系统文件选择器，需要允许 broad MIME fallback。
