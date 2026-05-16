# 过期音源播放修复设计

> 文档状态：当前规范
> 适用范围：插件歌曲在导入歌单、加入歌单、收藏或从持久化队列恢复后播放前的音源解析。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/core/trackPlayer/index.ts`、`../../../../MusicFree/src/core/pluginManager/plugin.ts`、`../../../../MusicFree/src/core/musicSheet/index.ts`
> Dev Harness： [plugin rules](../../dev-harness/plugin/rules.md)、[player rules](../../dev-harness/player/rules.md)、[test rules](../../dev-harness/test/rules.md)
> 最后校验：2026-05-11

## 背景

用户反馈：导入的歌单/专辑歌曲或手动添加到歌单的歌曲，过一段时间后无法播放。该症状符合插件真实音源 URL 过期：搜索、导入或详情接口可能返回临时直链，Android 会把 `MusicItem.url` 作为普通字段持久化到 Room；再次从歌单读取时，播放器看到 `url` 非空就直接交给 Media3，不再调用插件刷新真实源。

RN 原版的播放链路不同：`TrackPlayer.play()` 在设置实际音源前总是优先通过当前插件 `getMediaSource(musicItem, quality)` 获取新 URL；只有插件没有解析能力、解析失败且原歌曲已有 `url` / `qualities` 时才回退到原始 URL。`Plugin.importMusicSheet()` 与详情类结果会调用 `resetMediaItem` 补 platform，但播放时不会信任历史直链为长期有效音源。

## 根因

Android 当前有两处短路：

1. `PlayerController.resolvePlayableItem()` 在 `item.url` 非空时直接返回 `item`。
2. `PluginMediaSourceService.resolve()` 在 `item.url` 非空时直接返回 `null`。

这让被持久化的临时 URL 变成“最终音源”，绕过了插件刷新、音源重定向和音质回退。导入或添加后立即播放可能成功；URL 过期后，同一条持久化歌曲就会失败。

## 目标

1. 插件歌曲即使已有远端 `url`，播放前也优先走 `MediaSourceResolver` 刷新音源。
2. 保留 RN 回退语义：解析失败且原歌曲有可播放 URL 时继续回退原 URL，不让无 `getMediaSource` 的直链插件歌曲立刻失效。
3. 本地音乐和本地 URI (`file://`、`content://`、`android.resource://`、绝对路径) 不进入插件解析。
4. 队列当前曲、通知切歌、播放结束自动下一首、歌单详情播放都通过 `PlayerController` 获得同一修复。
5. 持久化 `MusicItem.raw`，避免导入/添加后插件重新解析时丢失扩展字段。
6. `PlayerController` 在设置 MediaItem 前写入 `TrackHeaderRegistry`，让解析得到的 headers / userAgent 被 Media3 请求使用。
7. 补充单元测试覆盖“已有过期 URL 仍刷新”“插件服务不因 URL 非空跳过解析”“raw 入库回读不丢”和“解析 headers 会注册”。

## 非目标

- 不新增 URL 过期时间字段或媒体缓存淘汰策略。
- 不迁移 Room 既有数据，不清空 `music_items.url`。
- 不重构各 ViewModel 里已有的预解析逻辑；本次只修复最终播放边界。
- 不改变 QuickJS 线程模型、不新增 instrumentation 真网测试。

## 设计

`PlayerController.resolvePlayableItem()` 调整为：

1. 先应用现有移动网络播放门禁。
2. 如果是本地播放源，直接返回原 item。
3. 对非本地歌曲总是尝试 `mediaSourceResolver.resolve(item)`。
4. 若 resolver 返回带非空 URL 的 item，再次经过网络门禁后使用该新 item，并替换队列当前项。
5. 若 resolver 返回空或异常，而原 item 有非空 URL，则记录 fallback 日志并使用原 item。
6. 若 resolver 返回空且原 item 也没有 URL，则保持当前“无法解析音源”错误。

`PluginMediaSourceService.resolve()` 移除 `item.url` 非空时的 early return。服务继续按“替代插件优先，源插件回退，音质 fallback 顺序”的规则解析。若源插件不支持 `getMediaSource` 或返回空，服务返回 `null`，由播放器负责回退原 URL。

`MusicItemEntity` 新增 `rawJson` 字段，使用现有 `Converters.rawMapToJson()` / `jsonToRawMap()` 做 Room 持久化。`AppDatabase.version` 从 7 升到 8；项目当前使用 `fallbackToDestructiveMigration(dropAllTables = true)`，本次按开发阶段约束不补手写 migration，但通过 schema export 生成 v8 schema。

`PlayerController` 新增依赖 `TrackHeaderRegistry`。当 `MediaSourceResolver` 返回 `MediaSourceResolution` 且 URL 非空时，若 `source.headers` 非空或 `source.userAgent` 非空，则在 `controller.setMediaItem(...)` 之前调用：

```kotlin
trackHeaderRegistry.put(playable.url, source.headers.orEmpty(), source.userAgent)
```

fallback 到原 `item.url` 时没有新的 `MediaSourceResult`，因此不注册 headers。

## 测试

- `player` 单测新增：`playItem` 对已有 `url` 的远端插件歌曲仍调用 resolver，并用刷新后的 URL 替换队列当前项。
- 更新既有“已解析歌曲不调用 resolver”的断言，使其符合新的过期 URL 保护语义。
- `player` 单测新增：resolver 返回 headers / userAgent 时，`TrackHeaderRegistry` 能按刷新后的 URL 读到该 header entry。
- `plugin` 单测新增：`PluginMediaSourceService.resolve()` 对已有旧 URL 的 item 仍调用插件并返回新 URL。
- `data` 单测新增：`MusicItemMapper` 对嵌套 `raw` map 做 round-trip。
- 验证命令：
  - `./gradlew :plugin:testDebugUnitTest --tests com.hank.musicfree.plugin.media.PluginMediaSourceServiceTest`
  - `./gradlew :data:testDebugUnitTest --tests com.hank.musicfree.data.mapper.MusicItemMapperTest`
  - `./gradlew :player:testDebugUnitTest --tests com.hank.musicfree.player.controller.PlayerControllerNotificationControlsTest`
  - `./gradlew :player:testDebugUnitTest`
  - `./gradlew :app:assembleDebug`

## 后续修订

本设计中的 “**不新增 URL 过期时间字段或媒体缓存淘汰策略**” 非目标，在
[`2026-05-11-plugin-engine-alignment-design.md`](2026-05-11-plugin-engine-alignment-design.md) §5.7
被替换为**基于播放失败的失败驱动 eviction**（不基于时间）。后者实施后，
`PluginMediaSourceService` 重新按 `cacheControl` 读 cache，但
`PlayerController` 监听 ExoPlayer `ERROR_CODE_IO_BAD_HTTP_STATUS` 触发 evict +
`resolveFresh`，对单条目单次播放设 1 次重试上限。
