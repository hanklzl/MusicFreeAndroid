# 插件管理 RN 完整对齐设计

> 文档状态：当前规范
> 适用范围：插件管理页 RN 操作对齐、插件用户变量、音源重定向、插件内导入单曲/歌单、插件安装/更新反馈，以及所有插件音源解析入口的统一接入。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 依赖规范：[插件管理链路设计规格](./2026-04-14-plugin-management-design.md)、[歌单导入功能设计](./2026-05-04-playlist-import-design.md)
> RN 参考：`../../../../MusicFree/src/pages/setting/settingTypes/pluginSetting/components/pluginItem.tsx`、`../../../../MusicFree/src/pages/setting/settingTypes/pluginSetting/views/pluginList.tsx`、`../../../../MusicFree/src/core/pluginManager/`、`../../../../MusicFree/src/components/panels/types/setUserVariables.tsx`、`../../../../MusicFree/src/components/panels/types/importMusicSheet.tsx`
> UI Harness 规则：[screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)
> 最后校验：2026-05-09

## 背景

Android 当前已经具备第一阶段插件管理能力：

- `PluginListScreen`、`PluginSortScreen`、`PluginSubscriptionScreen` 已拆成独立页面，并使用公共 `MusicFreeScreenScaffold`。
- `PluginMetaStore` 已持久化禁用插件、插件排序、用户变量值和订阅源。
- `PluginInfo` 已包含 `srcUrl`、`hints`、`supportedMethods` 等核心字段。
- 插件列表已有更新、分享、卸载、启用/禁用、更新全部、更新订阅等基础入口。
- 首页已有“导入歌单”入口，复用 `importMusicSheet` 和批量添加到歌单能力。

与 RN 原版相比，当前仍缺少插件卡片内完整操作、用户变量编辑 UI、音源重定向配置与全局生效，以及批量安装/更新失败详情。当前 Android 还有多个页面直接调用 `plugin.getMediaSource()`，导致音源解析规则分散，无法保证重定向、回退和队列切歌一致。

## 目标

1. 插件列表卡片按 RN 原版直译展示所有可用操作，不把低频操作折叠到二级菜单。
2. 补齐插件卡片操作：更新、分享、卸载、音源重定向、导入单曲、导入歌单、用户变量。
3. 网络安装支持 `.js` 直装和 `.json` 插件集合 URL。
4. 更新全部、更新订阅、批量安装提供成功、部分失败、全部失败和失败详情。
5. `PluginMetaStore` 新增音源重定向配置，持久化“源插件 -> 解析插件”关系。
6. `PluginInfo` 解析插件声明的 `userVariables`，并由设置页生成编辑 UI。
7. 用户变量保存后立即刷新已加载插件运行时的 `env.getUserVariables()` 结果。
8. 新增共享插件音源解析服务，统一处理原插件、重定向插件、回退和失败。
9. 全局覆盖搜索播放、插件歌单详情、榜单详情、专辑详情、歌手详情、播放器队列切歌、通知切歌和下载。
10. 实现和计划文档均使用中文；本功能从 spec 编写开始在 `.worktrees/plugin-management-parity` worktree 中进行。

## 非目标

- 不实现插件懒加载。
- 不补齐 WebDAV、`URL` polyfill、Cookie Manager 等 JS runtime 扩展。
- 不改插件协议本身；只补 Android 侧解析、持久化、UI 和调用链。
- 不做完整 i18n 迁移；新增文案先使用中文硬编码。
- 不新增独立“导入历史”“导入任务队列”或后台通知。
- 不重新设计插件列表信息架构；本次按 RN 卡片操作直译。

## 模块边界

```text
:plugin
  - 插件声明字段解析：userVariables
  - 插件元数据：音源重定向、用户变量运行时刷新
  - 插件安装/更新：.json URL 展开、失败详情、操作结果模型
  - 共享音源解析实现：源插件、重定向插件、回退策略

:feature:settings
  - 插件列表 RN 直译卡片
  - 用户变量编辑面板
  - 音源重定向单选对话框
  - 插件卡片导入单曲/歌单 flow
  - 安装/更新反馈详情

:feature:home / :feature:search
  - 移除局部 getMediaSource 直调和写死 fallback
  - 接入共享音源解析服务

:player
  - 仅依赖 :core 中的音源解析接口，队列当前曲、下一曲、通知切歌均可解析无 URL 插件歌曲

:downloader
  - 下载音源解析接入共享服务，并保留音质降级逻辑

:core
  - 承载跨模块音源解析接口与返回模型，不依赖插件实现

:data
  - 继续承载既有歌单写入能力
```

依赖方向仍保持 `:app -> :feature:* -> :data, :player, :plugin -> :core`。不得让 `:core` 依赖 `:plugin`。

## 插件元数据模型

### 音源重定向

`PluginMetaStore` 新增持久化项：

```kotlin
val alternativePlugins: Flow<Map<String, String?>>
suspend fun setAlternativePlugin(sourcePlatform: String, targetPlatform: String?)
fun getAlternativePlugin(sourcePlatform: String): Flow<String?>
```

存储 key 建议：

| Key | 类型 | 说明 |
|---|---|---|
| `alternative_plugins` | `String` JSON | `{ "sourcePlatform": "targetPlatform" }`；值为空或缺失表示无重定向 |

规则：

- 设置为 `null`、空字符串或自身 platform 时，按“无重定向”处理。
- 不做链式多跳。若 A 设置为 B，B 设置为 C，解析 A 时只考虑 B。
- 候选目标只展示已启用且支持 `getMediaSource` 的插件。
- 目标插件被卸载、禁用或不支持 `getMediaSource` 时，不删除配置，但运行时回退源插件，并在 UI 中显示配置异常提示。

### 用户变量声明

当前 Android 已存储用户变量值，但没有解析插件声明。`PluginInfo` 新增：

```kotlin
data class PluginUserVariable(
    val key: String,
    val name: String? = null,
    val hint: String? = null,
)

data class PluginInfo(
    // existing fields...
    val userVariables: List<PluginUserVariable> = emptyList(),
)
```

解析规则对齐 RN：

- 读取 `__plugin.userVariables`。
- 仅保留 `key` 非空的条目。
- `name` 为空时 UI 展示 `key`。
- `hint` 用作输入框 placeholder。
- 插件没有声明时不展示“用户变量”按钮。

## 共享音源解析服务

新增共享音源解析能力时，接口和返回模型放在 `:core`，具体实现放在 `:plugin`。这样 `:player` 可以依赖 `:core` 接口，不直接依赖 sibling 模块 `:plugin`。

```kotlin
interface MediaSourceResolver {
    suspend fun resolve(
        item: MusicItem,
        quality: String = "standard",
    ): MediaSourceResolution?
}

data class MediaSourceResolution(
    val item: MusicItem,
    val source: MediaSourceResult,
    val requestedPlatform: String,
    val resolverPlatform: String,
    val redirected: Boolean,
)

class PluginMediaSourceService : MediaSourceResolver
```

Hilt 绑定由插件模块提供，App 组合 `:player` 与 `:plugin` 后注入生效；`PlayerController` 只看见 `MediaSourceResolver`。

解析流程：

1. 根据 `item.platform` 查找源插件。
2. 读取源插件的音源重定向目标。
3. 若目标插件存在、已启用且支持 `getMediaSource`，优先使用目标插件解析。
4. 目标插件解析失败或返回空 URL 时，回退源插件解析。
5. 源插件也失败时返回 `null`。
6. 返回的 `item` 保留原始 `id/platform` 身份，只写入解析后的 URL 和必要扩展信息，避免队列、收藏、历史和去重身份被替换为目标插件。
7. 对本地音乐或已经有可播放 URL 的歌曲，调用方可跳过插件解析；若仍进入服务，服务应直接返回现有 URL 或空结果，由调用场景决定。

现有搜索页写死的 `WY_FALLBACK_PLATFORM` 逻辑应迁移到该服务能力之外的显式用户配置：用户选择哪个插件做音源重定向，就使用哪个插件；不再内置固定平台 fallback。

### 全局接入点

必须替换以下直接 `plugin.getMediaSource()` 调用：

- `feature/search` 搜索结果播放。
- `feature/home` 插件歌单详情播放。
- `feature/home` 榜单详情播放。
- `feature/home` 专辑详情播放。
- `feature/home` 歌手详情播放。
- `player` 队列当前曲播放、下一曲、上一曲、通知切歌、播放结束自动下一首。
- `downloader` 的 `PluginMediaSourceResolver`。

播放器是关键边界：如果只在 ViewModel 点击时解析当前歌曲，队列里的下一首仍可能没有 URL。因此 `PlayerController` 需要在 `setMediaItemAndPlay` 前接入异步解析，或通过注入的 resolver 在设置 MediaItem 前完成解析。

## 插件列表 UI

插件列表页继续使用普通 AppBar 和 `MusicFreeScreenScaffold`，遵守 [screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)。

### 顶部菜单

保持现有入口：

- 订阅设置
- 排序
- 卸载全部

### FAB 菜单

保持现有入口并补齐行为：

- 从本地安装：支持选择 `.js` 文件。
- 从网络安装：输入 URL，支持 `.js` 和 `.json`。
- 更新全部插件：遍历有 `srcUrl` 的插件。
- 更新订阅：遍历订阅源并安装或更新其中插件。

### 插件卡片

按 RN 直译方式展示所有可用操作。卡片内容：

- 插件名称。
- 描述入口：当 `description` 非空时显示说明按钮，弹出描述对话框；描述支持 Markdown 可后续增强，本次可先纯文本展示。
- 启用/禁用开关。
- 版本和作者。
- 当前音源重定向说明：例如 `音源重定向：网易云`；无配置时不显示或显示 `无音源重定向`。
- 操作按钮区：直接展示所有可用按钮，允许换行。

按钮展示规则：

| 按钮 | 展示条件 | 行为 |
|---|---|---|
| 更新 | `srcUrl` 非空 | 调用单插件更新 |
| 分享 | `srcUrl` 非空 | 复制 `srcUrl` 到剪贴板 |
| 卸载 | 总是显示 | 二次确认后卸载 |
| 音源重定向 | 总是显示 | 打开单选对话框 |
| 导入单曲 | `supportedMethods` 包含 `importMusicItem` | 输入链接并解析单曲 |
| 导入歌单 | `supportedMethods` 包含 `importMusicSheet` | 输入链接并解析歌曲列表 |
| 用户变量 | `userVariables` 非空 | 打开用户变量编辑面板 |

## 音源重定向 UI

点击“音源重定向”后打开单选对话框：

- 标题：`设置音源重定向`
- 候选：
  - `无音源重定向`
  - 已启用且支持 `getMediaSource` 的插件名称
- 默认选中当前配置；若配置目标已失效，选中 `无音源重定向`，并显示提示。
- 保存后写入 `PluginMetaStore.setAlternativePlugin(sourcePlatform, targetPlatform)`。
- 若用户选择当前插件自身，保存为 `null`。

UI 文案说明：`该插件实际使用「X」插件解析音乐的音源`，对齐 RN。

## 用户变量 UI

点击“用户变量”后打开编辑面板。面板结构对齐 RN `SetUserVariables`：

- 标题：`设置用户变量`。
- 每个变量一行：左侧名称（`name ?: key`），右侧输入框。
- 输入框初始值来自 `PluginMetaStore.getUserVariables(platform)`。
- placeholder 使用变量 `hint`。
- 保存后调用 `PluginManager.setUserVariables(platform, values)`。

`PluginManager.setUserVariables` 必须同时完成：

1. 写入 `PluginMetaStore`。
2. 更新当前已加载插件 JS 运行时的 `globalThis.__userVariables`。

这样保存后下一次插件 API 调用立即读取新值，不要求重启或重新安装插件。

## 插件卡片导入能力

### 导入单曲

流程：

1. 点击支持 `importMusicItem` 的插件卡片按钮。
2. 弹出输入面板或对话框：
   - 标题：`导入单曲`
   - placeholder：`输入目标单曲`
   - hints：`plugin.info.hints["importMusicItem"]`
   - maxLength：1000
3. 空输入不调用插件，提示 `链接有误或目标为空`。
4. 调用 `plugin.importMusicItem(text)`。
5. 返回非空歌曲时弹确认：`发现歌曲「标题」，现在添加到歌单吗？`
6. 确认后打开现有添加到歌单面板，默认新建歌单名可为 `来自 <插件名>`。

### 导入歌单

流程：

1. 点击支持 `importMusicSheet` 的插件卡片按钮。
2. 弹出输入面板或对话框：
   - 标题：`导入歌单`
   - placeholder：`输入目标歌单`
   - hints：`plugin.info.hints["importMusicSheet"]`
   - maxLength：1000
3. 空输入不调用插件，提示 `链接有误或目标为空`。
4. 调用 `plugin.importMusicSheet(text)`。
5. 返回非空列表时弹确认：`发现 N 首歌曲！现在开始导入吗？`
6. 确认后打开批量添加到歌单面板，复用 [歌单导入功能设计](./2026-05-04-playlist-import-design.md) 的批量写入、去重和统计规则。

首页“导入歌单”入口继续保留，不与插件卡片入口互斥。

## 安装与更新反馈

当前 `InstallState` 字符串不足以表达批量结果，替换为结构化状态：

```kotlin
data class FailureDetail(
    val source: String?,
    val pluginName: String?,
    val message: String,
)

sealed interface PluginOperationUiState {
    data object Idle : PluginOperationUiState
    data class Loading(val label: String) : PluginOperationUiState
    data class Success(val message: String) : PluginOperationUiState
    data class PartialFailure(
        val message: String,
        val failures: List<FailureDetail>,
    ) : PluginOperationUiState
    data class Failure(
        val message: String,
        val failures: List<FailureDetail> = emptyList(),
    ) : PluginOperationUiState
}
```

网络安装 URL 规则：

- `.js`：直接下载并安装。
- `.json`：按 RN 订阅格式解析 `plugins[].url`，批量下载安装。
- 其他 URL：先按插件 JS 尝试，失败时返回可读失败原因。

反馈规则：

| 场景 | 行为 |
|---|---|
| 全部成功 | 显示成功提示 |
| 部分失败 | 显示“部分失败”，提供“查看详情” |
| 全部失败 | 显示失败提示，提供“查看详情” |
| 单插件无 `srcUrl` | 不显示更新/分享按钮 |
| 更新目标下载失败 | 失败详情包含 URL 和原因 |
| 订阅 JSON 格式无效 | 失败详情显示订阅 URL 格式无效 |

## 错误处理

| 场景 | 行为 |
|---|---|
| 音源重定向目标不存在 | 回退源插件解析，UI 标记配置异常 |
| 音源重定向目标禁用 | 回退源插件解析 |
| 目标插件解析失败 | 回退源插件解析 |
| 源插件也解析失败 | 播放/下载失败，由调用方显示失败事件 |
| 用户变量保存失败 | 保持面板打开并显示失败 |
| 用户变量运行时刷新失败 | 保存失败，避免 UI 显示已生效但插件仍读旧值 |
| 导入单曲返回空 | 提示 `导入单曲失败` |
| 导入歌单返回空 | 提示 `链接有误或目标歌单为空` |
| 批量安装部分失败 | 显示详情对话框 |

## 测试计划

### 单元测试

| 模块 | 覆盖 |
|---|---|
| `:plugin:test` | `PluginMetaStore` 音源重定向 CRUD、`PluginInfo.userVariables` 解析、运行时用户变量刷新、共享音源解析服务重定向/回退/失效目标、`.json` URL 展开、失败详情模型 |
| `:feature:settings:testDebugUnitTest` | 插件卡片按钮可见性、音源重定向保存、用户变量编辑保存、导入单曲/歌单状态流、批量安装/更新 UI 状态 |
| `:player` tests | 无 URL 队列歌曲在当前曲、下一曲、通知切歌时进入共享解析服务 |
| `:downloader` tests | 下载解析使用共享服务，同时保留音质降级 |
| `:feature:search` tests | 移除写死平台 fallback，播放解析委托共享服务 |

### 构建验证

默认收尾验证：

```bash
./gradlew :plugin:test
./gradlew :feature:settings:testDebugUnitTest
./gradlew :app:assembleDebug
```

根据实际改动追加：

```bash
./gradlew :player:testDebugUnitTest
./gradlew :downloader:test
./gradlew :feature:search:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
```

### 运行态验收

设备可用时执行：

1. 安装两个支持 `getMediaSource` 的插件。
2. 在插件管理页为插件 A 设置音源重定向到插件 B。
3. 搜索插件 A 的歌曲并播放，确认实际音源由插件 B 解析。
4. 将多首无 URL 插件歌曲加入队列，确认自动下一首和通知切歌都能播放。
5. 从插件歌单、榜单、专辑、歌手详情页点击播放，确认使用同一解析规则。
6. 下载插件歌曲，确认下载解析同样遵守重定向。
7. 编辑插件用户变量，保存后立即再次调用插件能力，确认无需重启即可生效。
8. 使用插件卡片导入单曲和导入歌单，确认能添加到用户歌单并正确去重。
9. 使用 `.json` URL 批量安装，制造部分失败，确认失败详情可查看。

## 开发工作流

- 实现分支：`feat/plugin-management-parity`
- 工作区：`.worktrees/plugin-management-parity`
- spec、plan 和后续实现均在该 worktree 中进行。
- 文档互引必须使用相对路径，不写入绝对路径。
- `docs/superpowers/plans/*.md` 只作为执行计划产物，不从历史计划反向推导当前规范。

## 实现记忆（2026-05-09）

- 已新增 `:core` 共享 `MediaSourceResolver` 合约，`:plugin` 提供插件实现并处理音源重定向、目标失效、目标失败后的源插件回退。
- 播放器队列、通知切歌、搜索播放、插件歌单/榜单/专辑/歌手详情播放，以及下载解析均已接入共享音源解析入口，避免继续分散直调 `plugin.getMediaSource()`。
- 插件元数据已支持音源重定向持久化、插件声明 `userVariables` 解析、用户变量运行时刷新；保存失败时不写入持久化值，避免 UI 显示已生效但插件运行时仍读旧值。
- 插件管理页卡片已补齐 RN 对齐操作：更新、分享、单插件卸载确认、音源重定向、导入单曲、导入歌单、用户变量、说明；批量安装/更新失败通过结构化详情展示。
- 用户变量保存 UI 使用 request-scoped `UserVariableSaveUiState`，只响应当前保存请求；失败时保留弹窗和已编辑草稿，成功后才关闭。
- 插件卡片导入单曲/歌单解析成功后复用 `AddToPlaylistBottomSheetContent`，可选择已有歌单或新建歌单写入；新建歌单后导入失败会删除刚创建的空歌单并保留待导入项。
- 收尾静态验证已通过：
  `./gradlew :core:test :plugin:test :feature:settings:testDebugUnitTest :feature:search:testDebugUnitTest :feature:home:testDebugUnitTest :downloader:test :player:testDebugUnitTest :app:assembleDebug`
