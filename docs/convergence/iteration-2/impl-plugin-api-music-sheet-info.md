# 功能A：PluginApi 补齐 `getMusicSheetInfo`

## 差异项来源
- analysis 编号: #1（PluginApi 缺失 `getMusicSheetInfo`）
- 对应原版:
  - `src/types/plugin.d.ts`
  - `src/core/pluginManager/plugin.ts`

## 技术方案
### 1) 扩展 PluginApi 契约
在 `PluginApi` 中新增：
- `getMusicSheetInfo(sheet: MusicSheetItemBase, page: Int): MusicSheetInfoResult?`

### 2) 扩展返回模型
在 `PluginModels.kt` 中新增：
- `MusicSheetInfoResult`
  - `isEnd: Boolean`
  - `sheetItem: MusicSheetItemBase?`
  - `musicList: List<MusicItem>`

### 3) 扩展 JS Bridge 解析
在 `JsBridge` 新增 `parseMusicSheetInfoResult`：
- 兼容对象返回
- 解析 `sheetItem` 与 `musicList`
- 缺失字段时提供安全默认值

### 4) 扩展 LoadedPlugin 调用
在 `LoadedPlugin` 新增 `getMusicSheetInfo` 调用：
- 复用 `musicSheetItemToMap` 做入参桥接
- 调用 JS `__plugin.getMusicSheetInfo(sheet, page)`
- 统一判空与异常保护

## 变更文件
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginModels.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt`

## 验证记录
- `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest"` ✅
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin` ✅

## 对齐度评估（非 UI 特性）
| 维度 | 结果 | 备注 |
|------|------|------|
| 接口覆盖 | ✅ | `getMusicSheetInfo` 已补齐 |
| 返回结构对齐 | ✅ | 与 RN `plugin.d.ts` 语义对齐 |
| 解析健壮性 | ✅ | 桥接层已覆盖 `sheetItem/musicList/isEnd` |
| 调用容错 | ✅ | 方法缺失或返回空值可安全降级 |
| 单测覆盖 | ✅ | 新增 `parseMusicSheetInfoResult` 解析测试 |

综合对齐度: 100%

## 遗留项
- PluginApi 仍有 7 个方法待实现（`getMusicInfo/getLyric/getAlbumInfo/getArtistWorks/importMusicSheet/importMusicItem/getMusicComments`）。
