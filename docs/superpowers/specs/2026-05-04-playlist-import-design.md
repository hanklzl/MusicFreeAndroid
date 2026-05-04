# 歌单导入功能设计

> 文档状态：当前规范
> 适用范围：仅适用于首页“导入歌单”入口、插件 `importMusicSheet` 能力识别、批量添加到用户歌单的数据流。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 依赖规范：[用户歌单功能设计](./2026-05-04-playlist-feature-design.md)
> RN 参考：`../../../../MusicFree/src/components/panels/types/importMusicSheet.tsx`、`../../../../MusicFree/src/pages/home/components/homeBody/sheets.tsx`、`../../../../MusicFree/src/pages/setting/settingTypes/pluginSetting/components/pluginItem.tsx`
> UI Harness 规则：[screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)
> 最后校验：2026-05-04

## 背景

Android 当前已经具备用户歌单的基础能力：

- `HomeScreen` 的“新建歌单”已接入真实 `PlaylistRepository`。
- `PluginApi.importMusicSheet(urlLike)`、`LoadedPlugin.importMusicSheet(urlLike)`、`JsBridge.parseImportMusicSheetResult(payload)` 已存在。
- `PlaylistRepository.addMusicsToPlaylist(playlistId, items)` 已存在，但当前实现是循环单曲写入，不保证事务边界。
- `HomeSheetsHeader` 已有“导入歌单”图标和回调，但 `HomeScreen.onImportClick` 仍为空实现。
- `AddToPlaylistSheetState` 目前只支持单曲，搜索、播放器、插件歌单详情都围绕 `pendingItem` 工作。

RN 原版首页点击“导入歌单”后，打开 `ImportMusicSheet` 面板：先列出支持 `importMusicSheet` 的插件；用户选择插件后输入目标链接；插件解析出歌曲后弹确认；确认后打开 `AddToMusicSheet` 面板选择目标歌单。本设计按 RN 交互落地首页入口，但暂不实现插件管理页里的单插件“导入歌单”入口。

## 目标

1. 首页“我的歌单”区域的“导入歌单”按钮接入真实导入链路。
2. 已启用且支持 `importMusicSheet` 的插件按用户插件排序展示。
3. 导入交互对齐 RN：插件选择 -> 链接输入 -> 解析中 -> 发现歌曲确认 -> 添加到歌单。
4. 插件返回歌曲缺少 `platform` 时，由 Android 侧使用当前插件 `info.platform` 兜底，保证去重、入库和播放身份稳定。
5. 批量导入复用现有用户歌单写入规则：按 `(musicId, platform)` 去重，重复项跳过。
6. 导入完成后明确提示“已导入 X 首”或“已导入 X 首，跳过 Y 首重复歌曲”。
7. 实现和计划文档均使用中文；开发在 `.worktrees/playlist-import` 的 git worktree 中进行。

## 非目标

- 不实现插件管理页单个插件菜单里的“导入歌单”入口。
- 不实现 RN 的“导入单曲”入口。
- 不实现备份 / 恢复类 JSON 歌单导入。
- 不改造插件安装、订阅、排序页面的信息架构。
- 不新增导入历史、导入任务队列、后台通知或断点续传。
- 不改变 `importMusicSheet` 的插件协议返回结构；仍以 `List<MusicItem>` 作为 Android 侧结果。
- 不为导入结果创建新独立页面；继续使用底部面板和确认对话框。

## 模块边界

```text
:plugin
  - 暴露插件支持的方法集合，至少能识别 importMusicSheet
  - importMusicSheet 解析结果使用当前插件平台兜底

:core
  - 扩展 AddToPlaylistSheetState 为批量歌曲模型
  - 扩展 AddToPlaylistBottomSheetContent 的文案和回调以支持批量导入

:data
  - 将 PlaylistRepository.addMusicsToPlaylist 改为事务内批量写入
  - 返回实际新增数量，重复歌曲按既有复合主键跳过

:feature:home
  - 新增 PlaylistImportViewModel 和首页导入 UI flow
  - HomeScreen.onImportClick 接入导入入口

:app
  - 无新增路由
```

依赖方向仍遵循 `:app -> :feature:* -> :data, :player, :plugin -> :core`。导入 flow 位于 `:feature:home`，不让 `:core` 依赖插件模块。

## 插件能力识别

### 当前问题

Android 当前 `PluginInfo` 记录了平台、版本、作者、搜索类型和 `hints`，但没有公开插件支持的方法集合。`LoadedPlugin.hasMethod(name)` 是私有方法，首页无法筛选“支持导入歌单”的插件。

### 设计

`PluginInfo` 新增：

```kotlin
val supportedMethods: Set<String> = emptySet()
```

`PluginManager.extractPluginInfo(engine)` 在加载插件时检测下列核心方法：

```text
search
getMediaSource
getMusicInfo
getLyric
getAlbumInfo
getArtistWorks
importMusicSheet
importMusicItem
getTopLists
getTopListDetail
getMusicSheetInfo
getRecommendSheetTags
getRecommendSheetsByTag
getMusicComments
```

筛选首页导入插件时使用：

```kotlin
plugin.info.supportedMethods.contains("importMusicSheet")
```

插件列表必须基于 `PluginManager.getSortedEnabledPlugins()`，从而同时满足“已启用”和“用户排序”两个条件。

## 导入交互

首页“导入歌单”按钮打开导入底部面板：

1. 面板标题为“导入歌单”。
2. 面板列表展示支持 `importMusicSheet` 的插件名称，排序沿用插件排序。
3. 无支持插件时显示“暂无支持导入歌单的插件”。
4. 点击插件后关闭插件列表面板，打开输入对话框。
5. 输入对话框：
   - 标题：“导入歌单”
   - placeholder：“输入目标歌单”
   - 最大长度：1000
   - 若 `plugin.info.hints?.get("importMusicSheet")` 非空，在输入框下方逐行显示提示。
6. 用户确认空输入时不调用插件，直接提示“链接有误或目标歌单为空”。
7. 非空输入确认后关闭输入框，显示解析中状态，文案“正在导入中...”。
8. 插件返回非空列表后显示确认对话框：
   - 标题：“准备导入”
   - 内容：“发现 N 首歌曲! 现在开始导入吗?”
9. 用户确认后打开“添加到歌单”底部面板。
10. 用户选择已有歌单或新建歌单后批量导入。

所有面板和对话框都支持取消；取消不会写入任何数据。

## 状态模型

首页导入使用独立 `PlaylistImportViewModel`。`HomeViewModel` 继续只负责首页已有数据和歌单创建，避免把插件解析、导入状态和批量写入事件塞进首页主 ViewModel。

```kotlin
data class ImportCapablePlugin(
    val platform: String,
    val name: String, // 当前 Android 侧使用 PluginInfo.platform 作为展示名
    val hints: List<String> = emptyList(),
)

sealed interface PlaylistImportState {
    data object Idle : PlaylistImportState
    data object LoadingPlugins : PlaylistImportState
    data class ChoosePlugin(val plugins: List<ImportCapablePlugin>) : PlaylistImportState
    data class InputUrl(val plugin: ImportCapablePlugin) : PlaylistImportState
    data class Parsing(val pluginName: String) : PlaylistImportState
    data class ConfirmFound(val plugin: ImportCapablePlugin, val items: List<MusicItem>) : PlaylistImportState
    data class ChooseTarget(val items: List<MusicItem>) : PlaylistImportState
    data class Completed(val added: Int, val skipped: Int) : PlaylistImportState
    data class Error(val message: String) : PlaylistImportState
}
```

状态转换：

```text
Idle
  -> ChoosePlugin
  -> InputUrl
  -> Parsing
  -> ConfirmFound
  -> ChooseTarget
  -> Completed
  -> Idle
```

任意阶段取消回到 `Idle`。空输入保持在 `InputUrl` 并显示错误提示。插件解析失败或返回空列表时显示 toast 后回到 `Idle`，对齐 RN 输入面板已关闭后的失败体验。目标歌单写入失败时显示 toast 后保持 `ChooseTarget`，用户可重新选择目标歌单或取消。

## 插件解析结果

`LoadedPlugin.importMusicSheet(urlLike)` 当前调用：

```kotlin
JsBridge.parseImportMusicSheetResult(result)
```

这会导致插件结果缺少 `platform` 时无法对齐 RN 的 `resetMediaItem(_, plugin.name)` 行为。本设计改为：

```kotlin
JsBridge.parseImportMusicSheetResult(result, fallbackPlatform = info.platform)
```

`JsBridge.parseImportMusicSheetResult` 对每条 map 调用 `toMusicItem(map, fallbackPlatform)`。当 map 的 `platform` 缺失或为空时，使用当前插件平台；当插件明确返回非空平台时，尊重插件返回值。

## 批量添加到歌单

`AddToPlaylistSheetState` 从单曲扩展为批量：

```kotlin
data class AddToPlaylistSheetState(
    val visible: Boolean = false,
    val pendingItems: List<MusicItem> = emptyList(),
)
```

为降低现有调用点改动，提供便捷构造或 helper：

```kotlin
AddToPlaylistSheetState.single(item)
AddToPlaylistSheetState.batch(items)
val pendingItem: MusicItem? get() = pendingItems.singleOrNull()
```

现有搜索、播放器、歌单详情单曲入口继续显示同一个底部面板；导入 flow 使用 `batch(items)`。

`PlaylistRepository.addMusicsToPlaylist(playlistId, items)` 事务规则：

1. 进入数据库事务。
2. 按插件返回顺序遍历 `items`。
3. 对每首歌执行 `musicDao.upsert(item.toEntity(...))`。
4. 计算当前歌单 `sortOrder` 的下一个位置。
5. `insertCrossRefIgnore` 写入 `playlist_music`。
6. `rowId != -1L` 计入新增；重复歌曲跳过。
7. 返回新增数量 `added`。

完成统计：

```kotlin
val added = repository.addMusicsToPlaylist(targetPlaylistId, parsedItems)
val skipped = parsedItems.size - added
```

完成提示：

- `skipped == 0`：`已导入 X 首`
- `skipped > 0`：`已导入 X 首，跳过 Y 首重复歌曲`

## 错误处理

| 场景 | 行为 |
|---|---|
| 无支持导入插件 | 底部面板显示“暂无支持导入歌单的插件” |
| 输入为空 | 不调用插件，提示“链接有误或目标歌单为空” |
| 插件返回 `null` 或空列表 | 提示“链接有误或目标歌单为空” |
| 插件抛异常或超时 | 提示“歌单导入失败” |
| 目标歌单写入失败 | 提示“导入失败，请重试” |
| 用户取消任一面板 | 回到 `Idle`，不写入数据 |

`LoadedPlugin.importMusicSheet` 继续吞掉插件异常并返回 `null`，同时保留日志；ViewModel 根据 `null` 和空列表转成用户文案。

## UI 约束

- 首页仍是特殊 Chrome 页面，不新增普通 AppBar。
- 新增导入面板使用 Material3 `ModalBottomSheet`，输入和确认使用 `AlertDialog`，对齐当前项目已存在的歌单创建和添加面板风格。
- 不在导入面板内新增独立导航页面。
- `HomeSheetsHeader` 现有导入图标、contentDescription 和 test tag 保持不变。
- 所有新增文案先使用中文硬编码，后续若项目统一 i18n 再迁移。

## 测试计划

### 单元测试

- `JsBridgeTest`：
  - `parseImportMusicSheetResult` 能解析列表。
  - 当导入结果缺少 `platform` 或 `platform` 为空时，使用 fallback platform。
  - 当导入结果含非空 `platform` 时，尊重插件返回值。
- `PlaylistRepository` 测试：
  - 批量导入返回新增数量。
  - 重复歌曲跳过。
  - 导入顺序对应 `sortOrder`。
  - 批量写入在事务内执行。
- `PlaylistImportViewModel` 测试：
  - 无支持插件显示空态。
  - 支持插件按启用状态和排序筛选。
  - 空输入不调用插件。
  - 解析成功进入确认状态。
  - 解析失败进入错误状态。
  - 确认目标歌单后返回 added/skipped。

### UI 测试

- 首页导入按钮触发导入插件列表面板。
- 无支持插件时显示空态。
- 选择插件后出现输入对话框。
- 解析成功后展示“发现 N 首歌曲”确认。
- 确认后打开添加到歌单底部面板。

### 运行态验收

最低验收：

```bash
./gradlew :plugin:test
./gradlew :data:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :app:assembleDebug
```

设备可用时追加运行态验收：

1. 安装或启用一个支持 `importMusicSheet` 的插件。
2. 首页点击“导入歌单”。
3. 选择插件并输入真实或测试链接。
4. 确认“发现 N 首歌曲”。
5. 选择一个已有歌单，验证目标歌单详情出现导入歌曲。
6. 对同一链接再次导入到同一歌单，验证重复歌曲被跳过。
7. 新建歌单后导入，验证新歌单数量和详情列表正确。

## 开发工作流

- 实现分支：`feat/playlist-import`
- 工作区：`.worktrees/playlist-import`
- spec 和后续 plan 均使用中文。
- 文档互引必须使用相对路径，不写入绝对路径。
- `docs/superpowers/plans/*.md` 只作为本次执行计划产物，不从历史计划反向推导当前规范。
