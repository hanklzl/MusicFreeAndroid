# 功能A：PluginApi 榜单/推荐链路扩展

## 差异项来源
- analysis 编号: #1（PluginApi 方法覆盖不足）
- 对应原版:
  - `src/types/plugin.d.ts`
  - `src/core/pluginManager/plugin.ts`

## 技术方案
### 1) 扩展 Kotlin 侧 PluginApi 契约
在 `PluginApi` 中补齐首批高优先级方法：
- `getTopLists()`
- `getTopListDetail(topListItem, page)`
- `getRecommendSheetTags()`
- `getRecommendSheetsByTag(tag, page)`

### 2) 新增插件数据模型
新增 `PluginModels.kt`，统一承载榜单/推荐歌单相关模型：
- `MusicSheetItemBase`
- `MusicSheetGroupItem`
- `TopListDetailResult`
- `RecommendSheetTagsResult`
- `PaginationResult<T>`

### 3) 扩展 JS 解析桥接层
在 `JsBridge` 中新增：
- `toMusicSheetItemBase` / `musicSheetItemToMap`
- `parseTopListGroups`
- `parseTopListDetailResult`
- `parseRecommendSheetTagsResult`
- `parseRecommendSheetsByTagResult`

### 4) 扩展 LoadedPlugin 调用能力
- 新增上述 4 个 API 方法的 QuickJS 调用实现
- 增加 `hasMethod()` 守卫，避免插件未实现能力时抛异常
- 增加 `parseJsonToAny()` 统一处理对象/数组 JSON 结果

### 5) 测试与验证
- 扩展 `JsBridgeTest`，覆盖新增解析路径
- 编译与单测通过（见下文“验证记录”）

## 变更文件
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginModels.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt`

## 验证记录
- `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest"` ✅
- `./gradlew :feature:home:compileDebugKotlin :feature:search:compileDebugKotlin :app:compileDebugKotlin` ✅

## 对齐度评估（非 UI 特性）
| 维度 | 结果 | 备注 |
|------|------|------|
| 接口覆盖 | ✅ | 本轮目标 4 个方法已补齐 |
| 返回结构对齐 | ✅ | 按 RN `plugin.d.ts` 结构映射 |
| 缺失能力降级 | ✅ | `hasMethod` 防御式处理 |
| 解析健壮性 | ✅ | map/list 两类 JSON 均覆盖 |
| 单测覆盖 | ✅ | 新增 4 组桥接解析测试 |

综合对齐度: 100%

## 遗留项
- 其余 PluginApi 方法（如 `getLyric/getMusicInfo/getArtistWorks/...`）仍未覆盖，进入 backlog。
