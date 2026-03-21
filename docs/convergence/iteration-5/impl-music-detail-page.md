# 功能A/B/C：新增 MusicDetail 页面并接入详情 API

## 差异项来源
- analysis 编号: #2（`musicDetail` 页面缺失）
- analysis 编号: #3（新增 PluginApi 缺少页面消费）
- 对应原版:
  - `src/pages/musicDetail/index.tsx`
  - `src/pages/topListDetail/*`
  - `src/pages/pluginSheetDetail/*`

## 技术方案
### 1) 新增路由与导航
- 新增 `MusicDetailRoute`
- `AppNavHost` 新增 `musicDetailScreen(...)` 接线
- TopListDetail / PluginSheetDetail 列表项新增“详情”按钮并导航到 `MusicDetailRoute`

### 2) 新增页面状态与 ViewModel
实现 `MusicDetailViewModel`：
- 从路由构造基础 `MusicItem`
- 调用插件 API：
  - `getMusicInfo`
  - `getLyric`
  - `getMusicComments`
  - 预览调用 `getAlbumInfo`
  - 预览调用 `getArtistWorks(type = "music")`
- 统一错误态与重试

### 3) 新增 Compose 页面
实现 `MusicDetailScreen`：
- 顶栏返回
- 歌曲基础信息（封面/标题/歌手/专辑）
- 专辑与歌手作品预览计数
- 歌词列表（前 12 行）
- 评论列表（含回复数提示）

### 4) 补充路由测试
- `RoutesTest` 新增 `MusicDetailRoute` 序列化测试

## 变更文件
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListDetailNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetDetailNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailUiState.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/navigation/MusicDetailNavigation.kt`

## 验证记录
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅

## UI 还原度对比
### 页面: 音乐详情（MusicDetail）
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | `src/pages/musicDetail/index.tsx` | 新增 Compose 页面 | ⚠️ | 结构已具备，端上截图待补 |
| 间距/尺寸 | RN 代码推断 | 新增 Compose 页面 | ⚠️ | 待真机/稳定模拟器复核 |
| 颜色 | RN 深色风格 | 新增 Compose 页面 | ⚠️ | 当前沿用现有主题体系 |
| 字体/字号 | RN 代码推断 | 新增 Compose 页面 | ⚠️ | 待截图复核 |
| 交互行为 | 详情信息 + 歌词 + 评论 | API 已接线 | ⚠️ | 真实插件数据态待复验 |

综合还原度: 50%（0✅5⚠️0❌）
原版截图: `docs/convergence/screenshots/original/rn-music-detail.png`（缺失，当前按 RN 代码推断）
Android版截图: 暂缺（待补）

## 遗留项
- 在稳定插件数据环境补做 `TopList/PluginSheetDetail -> MusicDetail` 端到端截图与真实数据态验收。
