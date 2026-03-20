# 功能C：TopList / TopListDetail 页面链路

## 差异项来源
- analysis 编号: #3（`topList` / `topListDetail` 页面链路缺失）
- 对应原版:
  - `src/pages/topList/index.tsx`
  - `src/pages/topList/components/topListBody.tsx`
  - `src/pages/topListDetail/index.tsx`
  - `src/pages/topListDetail/hooks/useTopListDetail.ts`

## 技术方案
### 1) 路由扩展
新增路由：
- `TopListRoute`
- `TopListDetailRoute(pluginPlatform, topListId)`

并挂载到 `AppNavHost`，由首页“榜单”入口跳转。

### 2) TopList 列表页
新增 `TopListScreen + TopListViewModel`：
- 启动时加载本地插件
- 插件分段切换（按平台）
- 调用 `plugin.getTopLists()` 拉取榜单分组
- 列表项点击进入 `TopListDetailRoute`
- 插件缺失/不支持时给出空状态文案

### 3) TopListDetail 详情页
新增 `TopListDetailScreen + TopListDetailViewModel`：
- 通过 `pluginPlatform + topListId` 反查榜单项
- 调用 `plugin.getTopListDetail()` 拉首屏与翻页
- 支持“加载更多”
- 点击歌曲：尝试 `getMediaSource` 解析后推入播放器队列并跳转播放器

### 4) 模块依赖与测试
- `feature/home` 新增 `implementation(project(":plugin"))`
- `RoutesTest` 新增 TopList 路由序列化测试

## 变更文件
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- `feature/home/build.gradle.kts`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListUiState.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailUiState.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/navigation/TopListDetailNavigation.kt`
- `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`

## 验证记录
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin` ✅
- adb 截图验证：
  - 首页可进入榜单页（`android-toplist.png`）
  - 无插件时空状态提示正常

## UI 还原度对比
### 页面: 榜单页（空状态）
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | 代码参考 `src/pages/topList/index.tsx` | `docs/convergence/screenshots/iteration-1/android-toplist.png` | ⚠️ | 已有独立榜单页与插件空状态；原版为 TabView + 多插件分栏 |
| 间距/尺寸 | 代码参考 `src/pages/topList/components/topListBody.tsx` | 同上 | ⚠️ | 结构到位，细节间距仍需像素级校准 |
| 颜色 | 代码参考 `src/core/theme.ts` | 同上 | ⚠️ | 仍沿用当前主题色，暗色还原不足 |
| 字体/字号 | 代码参考 `src/constants/uiConst.ts` | 同上 | ✅ | 字号层级符合当前设计 token |
| 交互行为 | 代码参考 `src/pages/topListDetail/hooks/useTopListDetail.ts` | `docs/convergence/screenshots/iteration-1/android-toplist-detail.png` | ⚠️ | 路由与空态行为可用；无已安装插件，未完成真实榜单数据链路验收 |

综合还原度: 60%
原版截图: （本轮使用代码推断，未获得可用 RN 榜单页截图）
Android版截图: `docs/convergence/screenshots/iteration-1/android-toplist.png`

## 遗留项
- 需要在“已安装支持榜单能力插件”前提下复测详情页真实数据流。
- 推荐歌单页（`recommendSheets`）尚未落地，进入下一轮。
