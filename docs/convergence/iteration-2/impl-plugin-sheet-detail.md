# 功能C：新增 PluginSheetDetail 页面

## 差异项来源
- analysis 编号: #3（`pluginSheetDetail` 页面缺失）
- 对应原版:
  - `src/pages/pluginSheetDetail/index.tsx`
  - `src/pages/pluginSheetDetail/hooks/usePluginSheetMusicList.ts`

## 技术方案
### 1) 新增详情页状态与 VM
实现 `PluginSheetDetailViewModel`：
- 从路由参数构造 seed sheet
- 调用 `getMusicSheetInfo` 拉取详情与分页
- 列表点击时解析 `getMediaSource` 并下发到播放器

### 2) 新增详情页 UI
实现 `PluginSheetDetailScreen`：
- 顶部标题与返回
- 歌单头信息
- 歌曲列表 + 空态/错误态
- 加载更多入口

### 3) 新增路由与导航接线
- 新增 `PluginSheetDetailRoute`
- `RecommendSheets` 列表项点击导航到详情页
- `AppNavHost` 增加 `pluginSheetDetailScreen(...)` 接线
- `RoutesTest` 增加序列化/反序列化测试

## 变更文件
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailUiState.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/PluginSheetDetailNavigation.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

## 验证记录
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅
- 端上导航到详情页验证: ⚠️受阻
  - 原因1: 外部插件脚本依赖超出当前 `require` shim（安装失败）
  - 原因2: 模拟器出现 `System UI isn't responding` 持续弹窗，阻断自动点击链路

## UI 还原度对比
### 页面: 插件歌单详情
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | `src/pages/pluginSheetDetail/index.tsx` | 新增 Compose 页面 | ⚠️ | 代码层已实现，端上截图受环境阻塞 |
| 间距/尺寸 | RN 代码推断 | 新增 Compose 页面 | ⚠️ | 待稳定环境下截图复核 |
| 颜色 | RN 深色风格 | 新增 Compose 页面 | ⚠️ | 主题仍待后续统一 |
| 字体/字号 | RN 代码推断 | 新增 Compose 页面 | ⚠️ | 待真机/模拟器复核 |
| 交互行为 | 歌单详情分页与点击播放 | VM + 路由链路已接线 | ⚠️ | 缺稳定端上链路验收 |

综合还原度: 50%（0✅5⚠️0❌）
原版截图: `docs/convergence/screenshots/original/rn-plugin-sheet-detail.png`（缺失，当前按 RN 代码推断）
Android版截图: 暂缺（环境阻塞）

## 遗留项
- 在稳定模拟器环境复做 `RecommendSheets -> PluginSheetDetail` 端到端截图与播放链路验收。
