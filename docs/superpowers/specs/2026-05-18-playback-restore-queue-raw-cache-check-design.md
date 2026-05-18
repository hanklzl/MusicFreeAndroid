# 后台恢复后切歌解析失败与缓存正常链路确认设计

> 文档状态：当前规范（Player / Data / Plugin 交叉专项）
> 适用范围：`:player` 播放队列恢复与切歌、`:data` 播放队列持久化、`:plugin` 媒体源缓存正常链路、结构化日志观测
> 直接执行：是（作为 implementation plan 输入）
> 最后校验：2026-05-18
> 关联 dev-harness：[player/rules](../../dev-harness/player/rules.md)、[plugin/rules](../../dev-harness/plugin/rules.md)、[test/rules](../../dev-harness/test/rules.md)

## 1. 背景

用户反馈两个问题：

1. 从后台恢复到前台后，在歌曲详情页切换下一首歌会失败，提示解析歌曲失败。
2. 感觉流量消耗偏大，需要确认正常情况下缓存逻辑是否生效。

反馈包 `musicfree-feedback-8875399880813705409.zip` 的错误摘要显示，晚间重点时间段为 `2026-05-18 21:43` 与 `2026-05-18 23:24-23:31`。其中播放失败集中在 `元力QQ / itemId=4930516`，错误链为 `getMediaSource` 抛 `TypeError: cannot read property of undefined`，随后 `playback_resolve_failed reason=no_source`。

用户补充：晚间元力 QQ 源问题已经恢复，但播放问题依然存在。因此本设计不把“远端源不可用”作为主因，而聚焦 Android 恢复后的播放队列对象是否丢失插件所需字段。

## 2. 结论与假设

当前最强假设：

- 播放队列从数据库恢复后，`MusicItem.raw`、`localPath`、`addedAt` 等字段没有被持久化和恢复。
- 插件搜索 / 详情页正常播放时，`JsBridge.toMusicItem(...)` 会保留插件原始字段到 `MusicItem.raw`。
- 后台恢复或进程冷恢复后，用户在播放器详情页切下一首时，`PlayerController` 从恢复后的 `playQueue` 取到的是不完整 `MusicItem`。
- 元力 QQ 插件的 `getMediaSource` 依赖 raw payload 中的字段；字段丢失时 JS 侧读到 `undefined`，即使远端源已经恢复也会解析失败。

证据：

- `PlayQueueEntity` 当前只存 `id/platform/title/artist/album/duration/url/artwork/qualitiesJson/sortOrder`。
- `PlayQueueMapper` 当前恢复 `MusicItem` 时没有恢复 `raw/localPath/addedAt`。
- `MusicItemEntity` 与 `MusicItemMapper` 已经支持 `rawJson/localPath`，说明完整曲目持久化在其他数据面已经是既有模式。

## 3. 范围

### 3.1 本次包含

- 修复播放队列持久化字段丢失，保证恢复后的队列项仍可被插件重放解析。
- 补强观测日志，让后续 Logan 能直接判断恢复队列项和切歌项是否保留关键字段。
- 验证正常媒体源缓存链路是否生效：成功解析后写缓存，策略允许时读缓存。

### 3.2 本次不包含

- 不修改插件失败重试次数。
- 不改变 `cacheControl` 策略。
- 不把解析失败场景下的流量放大作为本次修复目标。
- 不记录 raw 内容本身，避免日志体积和敏感内容风险。

## 4. 方案

### 4.1 播放队列持久化完整曲目信息

在 `play_queue` 表新增字段：

| 字段 | 类型 | 默认值 | 用途 |
|---|---|---|---|
| `rawJson` | `TEXT?` | `NULL` | 保存 `MusicItem.raw`，恢复后传回插件 |
| `localPath` | `TEXT?` | `NULL` | 保留本地路径，避免本地/下载曲目恢复后退化 |
| `addedAt` | `INTEGER NOT NULL` | `0` | 保留曲目加入时间语义，与 `MusicItem` 完整模型一致 |

Room 版本从当前版本提升到下一版本，并补 `Migration(11, 12)`：

```sql
ALTER TABLE play_queue ADD COLUMN rawJson TEXT;
ALTER TABLE play_queue ADD COLUMN localPath TEXT;
ALTER TABLE play_queue ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0;
```

`PlayQueueMapper` 改为：

- `MusicItem.toPlayQueueEntity(...)` 写入 `rawJson = converters.rawMapToJson(raw)`。
- `MusicItem.toPlayQueueEntity(...)` 写入 `localPath` 和 `addedAt`。
- `PlayQueueEntity.toMusicItem(...)` 读取 `raw = converters.jsonToRawMap(rawJson)`。
- 继续使用现有 `qualitiesJson` 逻辑，不改变已有音质缓存字段。

### 4.2 补强观测日志

日志目标是回答三个问题：

1. 恢复队列时，当前队列项是否完整？
2. 切下一首时，切到的队列项是否完整？
3. 解析失败时，传给插件的队列项是否完整？

新增或扩展字段，不新增 raw 内容：

| 事件 | 扩展字段 |
|---|---|
| `playback_startup_restore_completed` | `currentPlatform`、`currentItemId`、`rawKeyCount`、`hasQualities`、`hasUrl`、`hasLocalPath` |
| `player_skip_next` | `fromIndex`、`toIndex`、`fromItemId`、`toItemId`、`toRawKeyCount`、`toHasQualities`、`toHasUrl` |
| `playback_start` | `rawKeyCount`、`hasQualities`、`hasUrl`、`expectedIndex` |
| `playback_resolve_success` | `rawKeyCount`、`hasQualities`、`inputHadUrl`、`resolverPlatform`、`redirected` |
| `playback_resolve_failed` | `rawKeyCount`、`hasQualities`、`inputHadUrl`、`reason` |

实现上在 `PlayerController` 内部抽一个小的私有 helper：

```kotlin
private fun MusicItem.diagnosticFields(prefix: String = ""): Map<String, Any?>
```

字段命名保持稳定小写或 camelCase，避免把 UI 文案当作机器字段。`rawKeyCount` 只记录 `raw.size`，不记录 key 名和值。

### 4.3 缓存正常链路确认

缓存本次只做确认，不改变策略。

当前 Android 行为与 RN 原版语义一致：

- `cacheControl=cache`：在线/离线都允许读缓存。
- `cacheControl=no-cache`：在线不读缓存，离线允许读缓存。
- `cacheControl=no-store`：不读也不写。
- 成功解析后，只要不是 `no-store`，会写入 `MediaCacheRepository`。

验收方式：

- 保留并补强 `PluginMediaSourceServiceTest`，覆盖 cache write、cache hit、online `no-cache` miss、offline `no-cache` hit。
- 保留 `MediaCacheRepository` 的解析和 LRU 测试，必要时补 raw/headers/userAgent 不丢失的断言。
- 不把解析失败后的 retry 请求数作为本次缓存验收指标。

## 5. 数据流

### 5.1 正常播放

```text
插件搜索/详情结果
  -> JsBridge.toMusicItem(raw 保留)
  -> PlayerController.playQueue / playItem
  -> PlayQueueRepository.saveQueue(rawJson 写入 play_queue)
  -> 后续恢复仍保留 raw
```

### 5.2 后台恢复后切下一首

```text
PlaybackStartupCoordinator.start()
  -> PlayQueueRepository.getQueue()
  -> PlayQueueEntity.toMusicItem(rawJson 恢复)
  -> PlayerController.restoreQueue(...)
  -> 用户在 PlayerScreen 点击下一首
  -> PlayerController.skipToNext()
  -> setMediaItemAndPlay(nextItem)
  -> PluginMediaSourceService.resolve(nextItem)
  -> LoadedPlugin.getMediaSource(nextItem, quality)
```

关键保证：`nextItem.raw` 与恢复前保存的 `MusicItem.raw` 一致，插件不会因为恢复链路丢字段而读取 `undefined`。

## 6. 错误处理

- 老版本升级后的 `play_queue.rawJson` 为空时，恢复为 `emptyMap()`，保持兼容，不崩溃。
- `rawJson` 损坏时，`Converters.jsonToRawMap` 当前会抛异常；本次不扩大到全局容错改造。若测试发现真实风险，优先在 mapper 层捕获并记录 `play_queue_raw_parse_failed`，返回 `emptyMap()`，避免启动恢复失败。
- 播放解析失败仍走现有 `playback_resolve_failed` 与 UI 错误提示，但日志必须能体现输入对象完整性。
- Room migration 必须覆盖旧表升级，不允许 destructive migration。

## 7. 测试方案

### 7.1 Data / Room

- `PlayQueueMapperTest`：`MusicItem -> PlayQueueEntity -> MusicItem` 往返保留 `raw`、`localPath`、`addedAt`、`qualities`。
- `PlayQueueRepositoryTest` 或 migration test：旧 schema 升级后新增列存在，默认值正确，旧队列可读。
- 若已有 Room migration 测试基建可复用，新增 `11 -> 12` 测试。

### 7.2 Player

- `PlayerControllerQueueStateTest` 或新增测试：`restoreQueue` 后 `skipToNext` 进入 `playback_start` 的 item 保留 `raw`。
- 日志 recording test：`playback_start` / `playback_resolve_failed` 包含 `rawKeyCount`、`hasQualities`、`hasUrl`。
- `PlaybackStartupCoordinatorTest`：恢复完成日志包含当前项诊断字段。

### 7.3 Plugin / Cache

- `PluginMediaSourceServiceTest`：
  - 成功解析写入缓存。
  - `cacheControl=cache` 时第二次 resolve 命中缓存。
  - `cacheControl=no-cache` 在线不读缓存。
  - `cacheControl=no-cache` 离线读缓存。
  - `cacheControl=no-store` 不写缓存。

### 7.4 验收命令

优先运行：

```bash
./gradlew :data:testDebugUnitTest --tests '*PlayQueue*' --tests '*Migration*'
./gradlew :player:testDebugUnitTest --tests '*PlayerController*'
./gradlew :plugin:testDebugUnitTest --tests '*PluginMediaSourceServiceTest'
./gradlew :app:testDebugUnitTest --tests '*PlaybackStartupCoordinatorTest'
./gradlew :app:assembleDebug
```

如果 migration 测试需要 instrumentation 环境，则追加对应 `:data:connectedDebugAndroidTest` 窄测试。

## 8. 验收标准

- 从持久化队列恢复出的 `MusicItem` 不丢 `raw`、`localPath`、`addedAt`。
- 后台恢复后切下一首时，日志能显示目标曲目 `rawKeyCount > 0`（对原本带 raw 的插件曲目）。
- 若再次出现 `playback_resolve_failed`，日志能区分是输入对象缺字段、无 URL/qualities，还是插件/远端解析自身失败。
- 正常缓存链路测试通过，且结论明确：默认在线 `no-cache` 不读缓存是当前预期，不当作缓存失效。
- Debug 构建通过；本次不要求 Release 构建。
