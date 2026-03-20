# 功能A：PluginApi 补齐 `getMusicInfo/getLyric/importMusicSheet/importMusicItem`

## 差异项来源
- analysis 编号: #1（PluginApi 缺失 4 个高频方法）
- 对应原版:
  - `src/types/plugin.d.ts`
  - `src/core/pluginManager/plugin.ts`

## 技术方案
### 1) 扩展 PluginApi 契约
在 `PluginApi` 中新增：
- `getMusicInfo(musicItem: MusicItem): MusicItem?`
- `getLyric(musicItem: MusicItem): LyricResult?`
- `importMusicSheet(urlLike: String): List<MusicItem>?`
- `importMusicItem(urlLike: String): MusicItem?`

### 2) 补充返回模型
在 `PluginModels.kt` 新增：
- `LyricResult`
  - `rawLrc: String?`
  - `rawLrcTxt: String?`
  - `lines: List<LyricLine>`

### 3) 扩展 JsBridge 解析
新增：
- `parseMusicInfoResult(base, patch)`：对基础 `MusicItem` 做增量 merge
- `parseImportMusicSheetResult(payload)`：列表返回解析
- `parseImportMusicItemResult(map)`：单曲返回解析
- `parseLyricResult(map)`：解析 `rawLrc/rawLrcTxt`，并提取 LRC 时间轴行

### 4) 扩展 LoadedPlugin 调用
新增上述 4 个方法的 JS 调用：
- 调用前 `hasMethod` 检查
- 统一空返回保护（`undefined/null/blank`）
- 统一异常捕获与日志
- 抽出 `escapeJsString` 复用字符串转义逻辑

## 变更文件
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginModels.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt`

## 验证记录
- `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest"` ✅
- 新增单测：
  - `parseMusicInfoResult merges partial payload`
  - `parseImportMusicSheetResult parses list payload`
  - `parseImportMusicItemResult parses single item`
  - `parseLyricResult parses lines from lrc text`

## 对齐度评估（非 UI 特性）
| 维度 | 结果 | 备注 |
|------|------|------|
| 接口覆盖 | ✅ | 本轮新增 4 个方法 |
| 返回结构对齐 | ✅ | `LyricResult` 与 import/mixin 语义对齐 |
| 解析健壮性 | ✅ | 空值、异常、部分字段均有保护 |
| 调用容错 | ✅ | 方法缺失或空响应时安全降级 |
| 单测覆盖 | ✅ | 新增 4 条解析单测 |

综合对齐度: 100%

## 遗留项
- PluginApi 仍缺 3 个方法：`getAlbumInfo/getArtistWorks/getMusicComments`。
