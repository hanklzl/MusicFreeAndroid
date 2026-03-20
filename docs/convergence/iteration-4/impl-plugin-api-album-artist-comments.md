# 功能A/B/C：补齐剩余 PluginApi + 桥接模型 + 解析测试

## 差异项来源
- analysis 编号: #1（PluginApi 缺失 3 个方法）
- 对应原版:
  - `src/types/plugin.d.ts`
  - `src/types/album.d.ts`
  - `src/types/artist.d.ts`
  - `src/types/media.d.ts`

## 技术方案
### 1) 扩展 PluginApi 契约
新增方法：
- `getAlbumInfo(albumItem, page)`
- `getArtistWorks(artistItem, page, type)`
- `getMusicComments(musicItem, page)`

### 2) 扩展插件模型
新增模型：
- `AlbumItemBase`
- `ArtistItemBase`
- `AlbumInfoResult`
- `ArtistWorksResult`
- `MusicComment`

### 3) 扩展 JsBridge 解析与映射
新增能力：
- `toAlbumItemBase/albumItemToMap`
- `toArtistItemBase/artistItemToMap`
- `parseAlbumInfoResult`
- `parseArtistWorksResult`
- `parseMusicCommentsResult`（含 replies 递归解析）

### 4) 扩展 LoadedPlugin 调用
新增 3 个 JS 调用链路：
- `__plugin.getAlbumInfo(__albumItem, page)`
- `__plugin.getArtistWorks(__artistItem, page, type)`
- `__plugin.getMusicComments(__musicItem, page)`

统一处理：
- 方法存在性检查
- `undefined/null/blank` 空返回降级
- 异常日志保护

## 变更文件
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginModels.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeTest.kt`

## 验证记录
- `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest" :plugin:compileDebugKotlin :app:compileDebugKotlin` ✅
- 新增单测：
  - `parseAlbumInfoResult parses album payload`
  - `parseArtistWorksResult parses music payload`
  - `parseMusicCommentsResult parses nested replies`

## 对齐度评估（非 UI 特性）
| 维度 | 结果 | 备注 |
|------|------|------|
| 接口覆盖 | ✅ | PluginApi 14/14 全量补齐 |
| 数据结构对齐 | ✅ | 对齐 album/artist/comment 基础结构 |
| 解析健壮性 | ✅ | comments 支持 replies 递归 |
| 调用容错 | ✅ | 缺方法/空响应/异常统一降级 |
| 单测覆盖 | ✅ | 新增 3 条解析单测 |

综合对齐度: 100%

## 遗留项
- 新增接口尚未被页面消费，后续需在 album/artist/detail/comment 页面迭代中做端到端验收。
