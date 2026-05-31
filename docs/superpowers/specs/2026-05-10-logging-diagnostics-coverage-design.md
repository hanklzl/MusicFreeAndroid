# Logging Diagnostics Coverage Design

> 文档状态：当前规范
> 适用范围：第二阶段核心功能日志打点、日志诊断字段约定、AI logging skill。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 依赖规范：[Logan Logging System Design](./2026-05-05-logging-system-design.md)、[Dev Harness INDEX](../../dev-harness/INDEX.md)
> 最后校验：2026-05-10

## 背景

仓库已经落地 `:logging` 模块，提供 `MfLogger` / `MfLog`、Logan 持久化、日志包导出、7 天淘汰、本地解码脚本和第一阶段核心链路打点。当前缺口不在日志底座，而在诊断覆盖面：多个 ViewModel、Repository、文件 IO、下载链路和部分插件/播放调用在失败时仍只更新 UI 文案或静默降级，导出的日志包不能稳定还原用户操作、异步加载版本、输入摘要、失败原因和结果数量。

本设计作为第二阶段，目标是让用户反馈日志包成为定位问题的第一入口：当用户导出日志并贴给 AI / 开发者后，可以从结构化事件中还原“用户做了什么、调用了哪个插件/网络/文件/数据写入、耗时多久、返回了什么摘要、在哪里失败、失败后 UI 或状态如何降级”。

## 目标

1. 用现有 `:logging` 模块覆盖核心故障边界，不引入第二套日志系统。
2. 为 ViewModel 异步加载、插件系统、网络请求、文件 IO、下载、Repository 写入、播放控制补齐结构化事件。
3. 固化稳定字段命名，减少临时文案、domain object `toString()` 和不可搜索字段。
4. 把日志打点要求沉淀为 `.agents/skills/logging-skill/SKILL.md`，并同步到 `.codex/skills/`，让后续迭代自动检查关键逻辑日志。
5. 通过单元测试、dev-harness 和 Debug 构建验证日志 helper、字段格式和主要模块编译。

## 非目标

1. 不做远程日志上传、在线查询平台或崩溃上报服务。
2. 不做应用内日志查看页面。
3. 不做完整脱敏系统；沿用第一阶段“导出前提示可能包含搜索词、URL、插件返回内容和设备信息”的策略。
4. 不做全量函数级 trace。只在用户动作入口、跨模块边界、异步加载、IO/网络/插件/播放/数据写入和失败降级处打点。
5. 不为了打点改业务行为、线程模型、导航或 UI chrome。

## 方案选择

采用“关键链路诊断覆盖 + 轻量日志 helper + logging skill”方案。

备选“全量函数级覆盖”能收集更多事件，但噪音、耗电、审查成本和日志包体积都会明显增加，并且难以判断哪些事件真正有定位价值。

备选“只补 skill 和规则，不补业务打点”更稳，但不能立即改善用户反馈日志的定位能力。

推荐方案直接提升诊断价值，同时把范围控制在可验证的核心边界。

## 日志架构

继续保持依赖方向：

```text
:app, :feature:*, :data, :player, :plugin, :downloader -> :logging
:logging -> Logan + Android/Kotlin 基础库
```

`:logging` 仍不依赖 `:core`。业务模块把 domain model 转成基础类型摘要后传入日志字段。

第二阶段允许 `:data` 和 `:downloader` 新增 `implementation(project(":logging"))`，因为它们分别承担数据库/文件 IO 和下载 IO，是用户问题高发边界。该依赖不改变现有模块单向架构。

## 分类与字段

保留已有分类：

- `app`
- `plugin`
- `search`
- `player`
- `playlist_import`
- `feedback`

新增分类：

- `data`：Repository、Room/DataStore 写入、收藏/历史/队列/歌词缓存。
- `file_io`：DocumentTree、封面文件、本地歌词导入、MediaStore 写入。
- `download`：下载任务调度、质量回退、HTTP 下载、MediaStore 落盘、取消/重试。
- `settings`：用户设置写入、插件管理 UI 操作、存储目录授权。
- `home`：首页/详情页/推荐/榜单/本地音乐等 feature home ViewModel 状态加载。
- `lyrics`：歌词搜索、关联、导入、偏移、删除。播放引擎仍用 `player`。

稳定字段约定：

| 字段 | 用途 |
|---|---|
| `screen` | 用户所在页面或 ViewModel，如 `plugin_sheet_detail` |
| `operation` | 业务操作，如 `load_initial`、`install_from_url` |
| `flowId` | 单次用户动作或异步加载链路 ID |
| `generation` | ViewModel 异步加载版本，用于 stale 诊断 |
| `platform` | 插件平台 |
| `pluginVersion` | 插件版本摘要 |
| `mediaType` | `music` / `album` / `artist` / `sheet` |
| `itemId` / `itemName` | 关键对象摘要，避免传完整 domain object |
| `playlistId` / `sheetId` | 歌单/插件歌单 ID |
| `query` | 用户搜索词；允许记录，导出前已有隐私提示 |
| `url` / `host` | 网络或导入 URL；允许记录完整 URL，优先额外带 `host` |
| `pathType` | `file_uri` / `content_uri` / `document_tree` / `mediastore` |
| `count` | 返回或写入数量 |
| `page` | 分页页码或插件分页 token 摘要 |
| `quality` | 播放/下载音质 |
| `durationMs` | 耗时；长操作必须记录 |
| `result` | `success` / `failure` / `cancelled` / `stale` / `skipped` |
| `reason` | 可搜索失败/跳过原因，使用稳定 snake_case |

事件命名继续使用小写 snake_case。事件名表达“发生了什么”，字段表达“发生在谁身上、输入和结果是什么”。

## ViewModel 覆盖

ViewModel 打点只覆盖用户动作入口和异步状态转移，不给每个 setter 打日志。

必须覆盖：

- `feature/home`：
  - `HomeViewModel`：本地扫描、播放、创建歌单、添加歌单、下载入口。
  - `LocalMusicViewModel`：目录持久化、本地扫描开始/成功/失败/取消、删除本地曲库条目。
  - `RecommendSheetsViewModel`：插件选择、tag 加载、分页加载、stale 丢弃。
  - `TopListViewModel` / `TopListDetailViewModel`：榜单列表加载、详情加载、分页、播放指定 index。
  - `PluginSheetDetailViewModel`：插件歌单详情加载、分页、收藏歌单、播放、stale 丢弃。
  - `MusicDetailViewModel` / `AlbumDetailViewModel` / `ArtistDetailViewModel`：详情加载、分页、下载入口。
  - `PlaylistViewModel` / `PlaylistDetailViewModel` / `MusicListEditorLiteViewModel`：歌单 CRUD、排序、删除、批量编辑、批量下载。
- `feature/search`：
  - 搜索、分页、平台切换、fallback 播放解析、下载入口；已有事件保留并补缺字段。搜索批次终态记录 `search_batch_finished`；单插件首屏超时记录 `search_plugin_timeout`；加载更多超时记录 `search_session_page_timeout`。
- `feature/player-ui`：
  - 收藏、音质切换、速度、添加到歌单、歌词搜索/关联/导入/偏移/删除、播放控制入口。
- `feature/settings`：
  - 设置写入、插件安装/更新/卸载/订阅/导入、文件选择目录写入、反馈日志操作。
- `feature/home/downloading`：
  - 下载取消、重试、重试全部失败、清理失败、取消全部进行中。

异步加载必须记录：

1. start：带 `flowId`、`generation`、输入摘要。
2. success：带 `durationMs`、`count` 或结果摘要。
3. failure：`MfLog.error`，带异常和稳定字段。
4. stale：当 generation 不匹配或旧 job 被丢弃时记录 `result=stale`。
5. cancelled：主动取消或 `CancellationException` 被吞掉时记录 `result=cancelled`，但不把正常取消当 error。

## 插件与网络覆盖

`:plugin` 已有大量第一阶段事件，第二阶段只补诊断缺口：

- `PluginManager`：安装/更新/订阅/卸载/排序/全量更新增加统一 `flowId` 和更完整输入/结果摘要。
- `LoadedPlugin`：14 个 Plugin API 调用统一字段：`platform`、`method`、`durationMs`、`result`、`count`、`page`、输入摘要。
- `JsEngine` / `JsBridge`：evaluate、console、userVariables 写入/刷新错误记录；不得破坏 QuickJS 单线程 dispatcher。
- `AxiosShim`：请求 start/success/failure 带 `method`、`url`、`host`、`statusCode`、`durationMs`、body 片段长度；网络失败使用 `MfLog.error`。
- `RequireShim`：保留 asset/module 失败事件，补 `moduleName` 和 `assetPath`。

插件集成测试网络通道仍按 `pluginNetworkTests` 门控；不新增默认真网测试。

## 数据、文件 IO 与下载覆盖

`:data`：

- `PlaylistRepository`：创建/删除/重命名/排序/添加/移除/批量编辑/封面保存失败。
- `StarredSheetRepository`：收藏/取消收藏插件歌单。
- `MusicRepository`：本地曲库扫描入库、删除、本地历史。
- `PlayQueueRepository`：队列持久化失败。
- `LyricRepository` / `MediaCacheRepository`：歌词缓存、关联、删除、媒体缓存读写失败。

`:downloader`：

- `DownloadEngine`：任务 enqueue、质量回退、开始、成功、失败、取消、重试、清理缓存。
- `OkHttpDownloader`：HTTP 下载 start/success/failure，记录 URL/host/status/bytes/duration。
- `MediaStoreMusicWriter`：MediaStore 写入成功/失败，失败时记录 cleanup 结果。
- `NetworkMonitor`：网络可用性变化只记录状态变化，避免高频噪音。

文件 IO：

- `PlaylistCoverStore`：保存/删除封面，记录 `pathType`、文件大小、失败原因。
- `DocumentTreeStorageAccess`：目录授权解析和不可访问原因。
- 本地歌词导入：记录输入长度、解析结果、关联曲目摘要和失败原因。

## logging skill

新增 `.agents/skills/logging-skill/SKILL.md`，同步到 `.codex/skills/logging-skill/SKILL.md`。

触发范围：

- 新功能或 bugfix 涉及 ViewModel、插件、网络、播放、下载、数据写入、文件 IO、导入导出、跨模块状态变化。
- 任何 catch 后吞异常、转 toast、降级返回、更新 UI error 状态。
- 新增 Repository / Manager / Service / Shim / Downloader / ViewModel 异步加载。

skill 必须要求：

1. 先读本设计和 `AGENTS.md` 日志规范。
2. 只用 `MfLog` / `MfLogger`，业务代码禁止直接 `android.util.Log.*` 或 Logan。
3. start/success/failure/cancelled/stale 成对覆盖关键链路。
4. 长操作使用 `timedSuspend` / `timedFields` 或等价 duration helper。
5. 字段稳定、可搜索、基础类型化。
6. 正常取消不记 error；吞掉异常或转 UI 错误必须记 error。
7. 新增 category 或字段前确认已有分类不能表达。
8. 对涉及插件、播放器、测试的改动继续触发对应 dev-harness skill/rules。

## 测试与验收

单元测试：

- `:logging:testDebugUnitTest`：分类、字段 helper、格式化兼容。
- 触及 ViewModel 的模块运行对应 `testDebugUnitTest`，测试 fixture 必须跟随构造器。
- 新增/修改 ViewModel 单测必须遵守 `runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`。
- 插件改动运行 `:plugin:testDebugUnitTest --no-daemon`。
- 播放器改动运行 `:player:testDebugUnitTest --no-daemon` 和必要的 `:feature:player-ui:testDebugUnitTest --no-daemon`。
- 下载/数据改动运行 `:downloader:testDebugUnitTest --no-daemon`、`:data:testDebugUnitTest --no-daemon`。

守门：

- `bash scripts/dev-harness/check.sh`
- `./gradlew :app:assembleDebug --no-daemon`

运行态验收：

1. Debug 包启动后生成 `app_start`。
2. 手动走一次插件安装或搜索，日志包含插件/网络 start-success/failure。
3. 手动触发一次下载或本地扫描，日志包含下载/文件 IO 事件。
4. 设置页生成日志包并可解码，确认新增事件为 JSON 行。

若无设备/模拟器，必须明确说明运行态验收未执行，不得声称完整闭环通过。

## 实施分组

为配合 sub-agent-driven-development，实施拆为互不重叠的写入域：

1. logging 基础与 skill：
   - `logging/`
   - `.agents/skills/logging-skill/`
   - `.codex/skills/logging-skill/`
   - `docs/DOCS_STATUS.md`
2. feature ViewModel：
   - `feature/home/`
   - `feature/search/`
   - `feature/player-ui/`
   - `feature/settings/`
3. plugin + network：
   - `plugin/`
4. data + file IO：
   - `data/`
   - `core/src/main/.../storage/`
5. downloader：
   - `downloader/`
6. player：
   - `player/`

每个实施包完成后先运行该包测试，再进入全仓守门。

## 风险与约束

- 日志太密会增加噪音和包体。原则是关键边界打点，不记录纯 UI 展示状态。
- 暂不脱敏意味着导出前隐私提示仍是必要条件，不能移除。
- ViewModel 构造器新增依赖会触发测试 fixture lag；优先使用 `MfLog` object，只有需要可注入 logger 才改构造器。
- 插件 QuickJS 相关打点不得把 `Context` / `JsBridge` 调用挪出 owning dispatcher。
- 下载和网络日志记录完整 URL 有诊断价值，但也提高隐私敏感度；事件字段必须明确 `url` / `host`，便于后续脱敏演进。
