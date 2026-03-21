# 迭代 10 实现说明（扩展字段透传）

## 变更文件
- `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
- `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt`
- `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt`

## 关键实现
1. `MusicItem` 增加 `raw: Map<String, Any?> = emptyMap()`
- 用于保留插件原始字段（例如 `songmid`、`albummid` 等）。

2. `JsBridge.toMusicItem(map)` 保留原始字段
- 新增 `raw = map.toMap()`。

3. `JsBridge.musicItemToMap(item)` 回传 `raw + 标准字段`
- 先带上 `raw`，再覆盖标准字段，保证：
  - 插件扩展字段不丢失。
  - 标准字段仍由宿主侧当前值为准。

4. 回归测试
- 单元测试新增：
  - `music item round trip preserves plugin extension fields`
  - 验证 `songmid` 在 round-trip 后仍存在。
- 集成测试增强：
  - 本地 runtime-shim 插件新增 `getMediaSource`，强依赖 `songmid`。
  - 覆盖 `search -> getMediaSource` 全链路。
