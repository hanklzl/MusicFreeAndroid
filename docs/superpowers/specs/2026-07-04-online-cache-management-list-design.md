# 在线播放缓存管理列表设计

> 文档状态：当前规范（设置页在线播放缓存管理）
> 适用范围：设置页“歌曲缓存管理”、在线播放解析缓存与 Media3 字节缓存的可视化列表、按歌曲 / 音质清理交互
> 直接执行：是（作为 implementation plan 输入）
> 最后校验：2026-07-04
> 关联 dev-harness：[UI rules](../../dev-harness/ui/rules.md)、[Player rules](../../dev-harness/player/rules.md)、[Cache & Logs](../../dev-harness/player/cache-and-logs.md)
> 上游参考：[在线歌曲字节缓存有效性设计](2026-06-23-byte-cache-validity-design.md)

## 1. 背景

当前“歌曲缓存管理”页面要求用户手动填写平台和歌曲 ID，再清理对应播放缓存。这个入口适合开发排障，但不适合作为用户功能：普通用户无法从 UI 得到插件平台标识和内部歌曲 ID，也无法判断哪首歌真正有缓存、缓存是否完整、清理会影响什么。

Android 侧的在线播放缓存由两层组成：

- Room `media_cache`：保存插件解析出的 URL、headers、userAgent，按 `platform + id` 存储多个音质。
- Media3 `SimpleCache` 与 `byte_cache_status`：保存音频字节缓存和 `None / Partial / Complete / PlayableVerified / StaleOrInvalid` 状态，按 `platform + id + quality` 存储。

本设计把设置页入口改为 RecyclerView-style 的可滚动缓存列表。Compose 实现使用 `LazyColumn`，用户看到的是歌名、歌手、平台、音质、缓存状态和大小，不再接触平台 / ID 输入框。

## 2. 目标与非目标

### 2.1 目标

1. 设置页“歌曲缓存管理”展示在线播放缓存条目列表，替代手填平台和歌曲 ID 的表单。
2. 用户可按歌名、歌手、平台搜索，并按缓存状态筛选。
3. 用户可清理单个音质缓存、整首歌在线播放缓存，或清理全部在线播放缓存。
4. 默认清理范围只包含在线播放解析缓存、字节缓存和字节缓存状态，不删除下载文件、本地音乐库，也不默认解除本地播放关联。
5. 列表展示优先使用可读歌曲信息；旧缓存缺少歌曲信息时仍可展示为“未知歌曲”，并允许清理。
6. 操作过程必须有结构化日志，便于反馈包判断用户清理了什么、释放了多少、是否失败。

### 2.2 非目标

- 不做下载管理页；下载任务、已下载歌曲删除仍归属下载 / 本地音乐功能。
- 不把本页面做成开发诊断页；内部 `platform/id/cacheKey` 只在日志和测试中保留，不作为主 UI 文案。
- 不改变插件 `cacheControl` 语义；在线播放 `no-cache` 的解析策略仍遵守字节缓存有效性设计。
- 不在本功能里新增自动后台下载整首歌。
- 不默认调用 `MusicRepository.clearLocalPlaybackAssociation()`。该接口用于修复本地播放关联异常，不属于在线播放缓存清理的普通用户路径。

## 3. 信息架构与交互

### 3.1 设置页入口

“基本设置 > 缓存”继续保留：

- “音乐缓存上限”
- “歌曲缓存管理”
- “清除音乐缓存”
- “清除歌词缓存”
- “清除图片缓存”

“歌曲缓存管理”的 trailing 文案从“按歌曲清理”改为“查看列表”。点击后进入普通 AppBar 页面，继续使用 `MusicFreeScreenScaffold(title = "歌曲缓存管理")`。

### 3.2 页面布局

页面首屏从上到下：

1. 搜索框：提示“搜索歌曲、歌手或来源”。
2. 状态筛选：全部、可复用、部分缓存、仅解析、异常。
3. 摘要行：显示条目数、估算占用、可复用缓存数量。
4. `LazyColumn` 缓存列表。

第一阶段把“清理全部”作为列表顶部的普通操作行，不扩展公共 AppBar action slot。若后续公共 AppBar 已统一支持 action slot，再单独评估迁移到右上角。

### 3.3 列表行

每个列表行以“歌曲身份”聚合，即同一 `platform + id` 为一行。行内展示：

- 主标题：歌曲名；未知时显示“未知歌曲”。
- 副标题：歌手；未知时显示“未知歌手”。
- 来源与更新时间：插件平台展示名或平台标识、最后更新 / 验证时间。
- 音质标签：例如“标准”“高品”“无损”。
- 状态标签：
  - `PlayableVerified` 且字节完整：可复用。
  - `Complete`：完整，待验证。
  - `Partial`：部分缓存。
  - `StaleOrInvalid`：异常。
  - 只有 `media_cache` 没有字节缓存状态：仅解析。
- 占用：优先使用 `byte_cache_status.cachedBytes` 汇总；没有字节状态时展示解析缓存大小。

排序默认按最近更新时间倒序。搜索和筛选在 ViewModel 层基于已加载列表完成。

### 3.4 详情底部弹窗

点击列表行打开底部弹窗，顶部避让状态栏规则按 UI Harness 执行。弹窗内容：

- 歌曲名、歌手、来源。
- 每个音质一行，展示状态、缓存大小、更新时间。
- 操作：
  - “清理该音质”：只删除该音质的 `media_cache` 条目、对应 SimpleCache key 和 `byte_cache_status`。
  - “清理整首歌在线播放缓存”：删除该歌曲所有音质的在线播放缓存。

每个清理操作都需要二次确认。确认文案必须写清楚“不删除已下载歌曲和本地音乐”。

### 3.5 空状态与错误状态

空状态：

- 没有任何在线播放缓存时，显示“暂无在线播放缓存”。
- 搜索 / 筛选后为空时，显示“没有匹配的缓存”。

错误状态：

- 数据加载失败时保留页面结构，展示失败文案和“重试”按钮。
- 个别缓存行 JSON 损坏时不让页面整体失败；该行展示为“未知歌曲”，状态为“异常”，仍允许清理。

## 4. 数据模型与模块边界

### 4.1 `media_cache` 增加展示信息

为了让后续缓存天然可读，`media_cache` 增加轻量展示列：

- `title TEXT`
- `artist TEXT`
- `album TEXT`
- `artwork TEXT`
- `duration_ms INTEGER`

`MediaCacheRepository.put(item, quality, source)` 在写入解析缓存时同步写入这些字段。现有用户升级走 `Migration(15, 16)`，新列可为空，不做昂贵 backfill。

旧缓存展示信息的补全顺序：

1. `media_cache` 新列。
2. `music_items` 中同 `platform + id` 的本地曲库记录。
3. `listen_event` 中同 `platform + id` 的最新播放记录。
4. 兜底“未知歌曲 / 未知歌手”。

### 4.2 缓存目录模型

新增设置页专用的缓存目录模型：

```text
OnlineCacheSongRow
  platform
  itemId
  title
  artist
  album
  artwork
  updatedAt
  sourceMetadataBytes
  qualities: List<OnlineCacheQualityRow>

OnlineCacheQualityRow
  quality
  status
  cachedBytes
  contentLength
  updatedAt
  invalidReason
```

状态来源：

- 音质列表优先从 `media_cache.sourcesJson` 的 quality keys 得到。
- `byte_cache_status` 提供字节缓存状态和大小。
- 只存在解析结果、没有字节状态的音质标为“仅解析”。

第一阶段不扫描 SimpleCache 文件系统来发现孤立 key；SimpleCache 与 `byte_cache_status` 的同步由现有驱逐路径负责。若后续反馈出现“列表没有但空间被占用”的证据，再单独补孤立 key 扫描。

### 4.3 清理边界

新增或拆分设置清理接口：

- `clearOnlineSongCache(platform, itemId, quality)`：清理单音质在线播放缓存，底层走 `MediaCacheRepository.deleteEntry()`。
- `clearOnlineSongCache(platform, itemId)`：清理整首歌在线播放缓存，底层走 `MediaCacheRepository.deleteItem()`。
- `clearAllOnlinePlaybackCache()`：清理全部在线播放解析缓存与 SimpleCache 字节缓存。

这些接口不得默认调用 `MusicRepository.clearLocalPlaybackAssociation()`。如未来需要暴露“解除本地播放关联”，必须单独做高风险操作，并在文案和日志中明确区分。

## 5. 日志与可观测性

用户行为日志：

- 点击设置入口：`settings.row.cache_management`
- 搜索：防抖后记录 `cache_management.search`，只记录 query length，不记录原文。
- 切换筛选：`cache_management.filter`
- 打开详情弹窗：`dialog_open(dialogId=cache_management_detail)`
- 关闭详情弹窗：`dialog_dismiss(dialogId=cache_management_detail, outcome=...)`
- 点击清理：`cache_management.clear_quality` / `cache_management.clear_song` / `cache_management.clear_all`

业务日志：

- `settings_online_cache_load`：记录 `count`、`qualityCount`、`durationMs`、`result`。
- `settings_online_cache_clear`：记录 `scope`、`platform`、`itemId`、`quality`、`freedBytes`、`durationMs`、`result`。

日志字段不得记录完整 URL、headers、用户搜索词明文或 SimpleCache 绝对路径。URL/headers 诊断仍沿用播放器缓存日志中的 hash / host 口径。

## 6. 验收与测试

实现按 TDD 顺序推进：

1. `:data` 单测：
   - `MediaCacheRepository` 写入展示信息。
   - 旧行缺展示信息时列表仍可返回可清理条目。
   - 单音质 / 整首歌清理仍同步删除 `byte_cache_status`。
2. Room migration 测试：
   - `15 -> 16` 增加展示列，旧数据不丢失。
3. `:feature:settings` ViewModel 单测：
   - 初始加载展示列表。
   - 搜索和状态筛选生效。
   - 清理单音质、整首歌、全部缓存后刷新列表并展示结果。
   - 清理在线播放缓存不调用本地播放关联清理。
4. Compose UI 单测：
   - 页面不再出现“平台”“歌曲 ID”输入框。
   - 有缓存时展示歌曲行、状态标签和大小。
   - 点击行打开详情弹窗，确认清理动作可触发。
   - 空状态和错误状态可见。
5. 本地收尾：
   - `./gradlew :data:testDebugUnitTest --no-daemon`
   - `./gradlew :feature:settings:testDebugUnitTest --no-daemon`
   - `bash scripts/dev-harness/check.sh`
   - `./gradlew :app:assembleDebug --no-daemon`

若实现使用功能 worktree，按仓库 worktree 规则在 squash merge 前保存 tree hash；只有最终 `main` tracked tree 与已验证 worktree tip 完全一致，才跳过 `main` 重复执行 harness check 与 Debug 构建。

## 7. 完成标准

- 用户进入“歌曲缓存管理”后看到的是可滚动歌曲缓存列表，不需要填写平台或歌曲 ID。
- 用户能按歌曲 / 音质清理在线播放缓存，确认弹窗明确说明不会删除下载文件和本地音乐。
- `PlayableVerified / Partial / StaleOrInvalid / 仅解析` 等状态可从 UI 区分。
- 旧缓存缺少标题时仍可被看到和清理。
- 清理操作有结构化日志，失败时不会吞异常或只显示静默失败。
- 实现不改变现有播放缓存策略和 `cacheControl` 语义。
