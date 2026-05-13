# Downloaded Local Metadata RN Parity Design

> 文档状态：当前规范
> 适用范围：应用内下载完成后的本地音乐曲库入库、下载状态、原插件曲目元数据保留。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../MusicFree/src/core/downloader.ts`、`../MusicFree/src/core/localMusicSheet.ts`、`../MusicFree/src/utils/mediaExtra.ts`、`../MusicFree/src/types/music.d.ts`
> 最后校验：2026-05-13

## 背景

用户反馈原版 RN 中下载到本地的音乐仍会保留封面、专辑、歌词、插件源等信息，而 Android 当前下载后的本地音乐链路没有达到同等效果。

RN 事实：

- `downloader.ts` 下载完成后调用 `LocalMusicSheet.addMusic({ ...musicItem, $: { localPath } })`，把原始插件曲目整体加入本地音乐列表，只追加本地路径。
- `patchMediaExtra(musicItem, { downloaded: true, localPath })` 只记录下载附加状态，不替代原始曲目。
- `LocalMusicSheet.addMusic()` 去重后直接持久化传入的完整 `IMusicItem`，不会重建字段或改写 `platform`。
- 手动导入或扫描普通本地文件时，RN 才生成本地平台曲目，并通过本地文件元数据补标题、歌手、专辑和路径。

Android 当前事实：

- `DownloadEngine` 下载完成后只写入 `downloaded_tracks`，记录 `id/platform/mediaStoreUri/relativePath/quality/size` 等下载状态。
- `DownloadTaskEntity` 只保存有限 seed 字段：标题、歌手、专辑、封面、时长、初始 URL，未持久化完整 `MusicItem.raw`、`qualities`、歌词字段或原插件返回的其他信息。
- 本地音乐页、搜索本地曲库、批量编辑主要读取 `MusicRepository.observeByPlatform("local")`，因此应用内下载的原插件曲目不会自然出现在本地音乐库中。
- `MusicItemBridgeProjector` 已能根据 `downloaded_tracks` 给插件调用投影 `downloaded/localPath`，但这只覆盖插件桥入参，不等于本地曲库拥有完整下载曲目。

## 设计目标

1. 下载完成的曲目继续保留原始 `id + platform`，语义上是“原插件曲目的本地副本”，而不是改写成 `platform = local`。
2. 下载完成后，本地音乐库能展示下载曲目的原始封面、专辑、插件源、歌词、音质、`raw` 等信息。
3. 普通扫描/导入的设备本地文件继续使用现有本地平台语义，不强行绑定插件源。
4. 本地音乐页、歌单内搜索本地库、批量编辑共享同一个本地音乐库读取口径。
5. 下载失败、未完成任务和旧版下载状态不能污染本地音乐库。

## 非目标

- 不在本轮实现物理删除 MediaStore 文件的确认流。
- 不把普通本地扫描文件反向匹配到插件源。
- 不重写播放器、歌词 UI 或插件 API，只修正下载到本地曲库的数据链路。
- 不迁移旧 `downloaded_tracks` 记录生成猜测元数据；缺少完整曲目 seed 的旧数据只继续作为“已下载”状态存在。

## 方案选择

采用“下载完成时写入完整原插件曲目 + 下载状态表继续记录本地副本”的方案。

备选“下载后创建 `platform = local` 曲目并把原插件信息塞进 `raw`”会导致 UI 显示插件源、歌词、插件详情和缓存刷新都需要额外反查，偏离 RN 语义。

备选“本地音乐页合并 `downloaded_tracks` 与 `music_items(local)` 两路数据”会制造第二套列表数据源，搜索和批量编辑也要重复合并逻辑。

推荐方案直接把本地曲库的领域模型修正为：

- 扫描曲目：`platform = local`。
- 下载曲目：原插件 `platform/id`，并存在一条对应 `downloaded_tracks` 记录。

## 架构

保持现有模块方向：

```text
:feature:* -> :downloader -> :data -> :core
```

下载链路中需要有一个明确的落库边界：

```text
Downloader.enqueue(MusicItem)
  -> DownloadTaskEntity 保存完整 seed
  -> DownloadEngine 解析音源并写入 MediaStore
  -> downloaded_tracks 写下载状态
  -> music_items upsert 原插件 MusicItem + localPath 状态
  -> 本地音乐库 observeLocalLibrary()
```

`MusicRepository` 增加统一读取口径，例如 `observeLocalLibrary()`：

- 返回所有 `platform = local` 的扫描曲目。
- 返回所有与 `downloaded_tracks` 有匹配 `(id, platform)` 的原插件曲目。
- 排序沿用当前本地音乐列表口径，优先保持 title 升序，必要时后续再单独对齐 RN 的本地排序设置。

本地音乐页、`SearchMusicListRoute.localLibrary()`、`MusicListEditorLiteRoute.localLibrary()` 都切到该统一口径，不在 UI 层自行合并数据。

## 数据模型

`DownloadTaskEntity` 需要保存完整曲目 seed。推荐新增 `musicItemJson` 或等价字段，内容由 `MusicItem` 序列化而来，覆盖：

- `id`
- `platform`
- `title`
- `artist`
- `album`
- `duration`
- `url`
- `artwork`
- `qualities`
- `raw`

恢复下载时优先从该 seed 还原 `MusicItem`。若旧任务没有 seed，则继续使用现有有限字段构造临时 `MusicItem`，但完成后只写入已有字段，不能伪造缺失元数据。

`MusicItemEntity` 也需要持久化本地路径状态。当前 `MusicItem` 模型已有 `localPath`，但实体和 mapper 未保存该字段；本设计要求新增可空 `localPath` 列，并同步 `MusicItemMapper` 与 Room schema。下载曲目落库时 `localPath` 保存可播放的本地 URI，优先使用 MediaStore `content://`；普通扫描曲目继续按扫描器现有 URL/localPath 语义处理。

`DownloadedTrackEntity` 保留下载状态职责，必要时可补充稳定本地路径字段：

- `mediaStoreUri`
- `relativePath`
- `displayName` 或等价可定位字段
- `quality`
- `sizeBytes`
- `downloadedAt`

`MusicItem` 的本地路径状态需要稳定表达。优先使用新增实体列对应的 `localPath` 字段；若因 MediaStore 只能安全持久化 `content://`，则 `localPath` 保存 `mediaStoreUri`，并在 `raw` 中记录可搜索的下载元信息，例如 `downloaded=true`、`downloadQuality`、`downloadedAt`。实现时不要使用 RN 的 `"$"` 私有 key 作为 Android 持久协议。

## 数据流

入队：

1. 用户在搜索、详情、播放页或批量编辑中触发下载。
2. `Downloader.enqueue()` 去重时检查 `downloaded_tracks` 与现有任务。
3. `DownloadTaskEntity` 写入完整 seed 与目标音质。

执行：

1. `DownloadEngine` 从完整 seed 还原原始 `MusicItem`。
2. 按目标音质和下载音质回退设置调用插件解析音源。
3. 下载到 cache，再提交到 MediaStore。

完成：

1. 写入 `downloaded_tracks`。
2. upsert `music_items` 中的原插件曲目，保留原始 `platform/id` 和完整元数据，并带上本地路径状态。
3. 删除 `download_tasks`。
4. 发出完成事件，UI 的本地音乐库 Flow 自动更新。

失败：

1. 下载失败只更新 `download_tasks.status/errorReason`。
2. 不写入 `music_items`，不写入新的 `downloaded_tracks`。
3. 已落盘但数据库写入失败时记录结构化错误，并尽量保持可恢复；不能静默显示为已完成。

## 兼容策略

旧版 `downloaded_tracks` 只有下载状态，没有完整曲目 seed。升级后：

- 如果 `music_items` 中已有相同 `(id, platform)`，`observeLocalLibrary()` 可以正常显示该曲目。
- 如果没有对应 `music_items`，不合成缺字段的假曲目，避免错误封面、专辑或插件源。
- 用户重新下载或从原列表再次触发下载后，新链路会写入完整曲目。

数据库当前使用 destructive fallback；本设计仍要求提升 Room schema 版本、导出新 schema，并同步 Room/DAO/mapper 测试，避免开发和测试环境字段漂移。

## 错误处理与日志

下载链路属于日志敏感路径，必须使用 `MfLog` / `MfLogger` 结构化记录，不新增直接 `android.util.Log.*`。

关键事件：

- `download_enqueue`
- `download_source_resolve_start/success/failed`
- `download_file_commit_success/failed`
- `download_local_library_write_success/failed`
- `download_task_completed`

稳定字段：

- `platform`
- `itemId`
- `itemName`
- `quality`
- `pathType`
- `mediaStoreUri`
- `count`
- `durationMs`
- `result`
- `reason`

正常取消按 cancelled 记录，不作为 error；catch 后转成失败状态或 UI 文案时必须记录 error。

## 测试策略

单元测试：

- `DownloadEngine`：下载完成后同时写入 `downloaded_tracks` 与原插件 `music_items`，并保留 `album/artwork/qualities/raw/localPath`。
- `DownloadEngine`：失败、取消、音源解析失败不写入本地音乐库。
- `DownloadEngine`：恢复 pending 任务时优先使用完整 seed。
- `MusicRepository`：`observeLocalLibrary()` 同时返回扫描本地曲目与已下载插件曲目。
- `MusicRepository`：旧 downloaded row 没有对应 `music_items` 时不会合成假曲目。
- `SearchMusicListSourceLoader`：local-library 来源切到 `observeLocalLibrary()`。
- `MusicListEditorLiteViewModel` / `LocalMusicViewModel`：本地库来源切换后仍能播放、移除、下载和批量编辑。

验收命令：

```bash
./gradlew :data:testDebugUnitTest --no-daemon
./gradlew :downloader:testDebugUnitTest --no-daemon
./gradlew :feature:home:testDebugUnitTest --no-daemon
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

有可用设备/模拟器时补充运行态验收：

1. 安装 Debug 包。
2. 用真实或测试插件下载一首带封面、专辑、歌词或 raw 信息的歌曲。
3. 进入本地音乐页确认显示原插件 tag、封面和专辑。
4. 播放该本地曲目，确认走本地副本但插件上下文不丢。
5. 导出或解码 Logan，确认下载和本地曲库写入事件可追踪。

## 实施备注

本设计在 `.worktrees/feat-downloaded-local-metadata-rn-parity` 中实施。所有仓库内文档引用使用相对路径；`docs/superpowers/plans/*.md` 仅作为历史执行快照，不作为当前执行来源。
