# 迭代 2 差异分析报告

## 当前状态快照
- 页面覆盖率: 7/19
  - 当前可用页面: `home`, `player`, `playlistDetail`, `search`, `settings`, `topList`, `topListDetail`
- PluginApi 覆盖率: 6/14
  - 已实现: `search`, `getMediaSource`, `getTopLists`, `getTopListDetail`, `getRecommendSheetTags`, `getRecommendSheetsByTag`
- 面板/弹窗覆盖率: 5/23（粗盘点）

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | PluginApi 缺失 `getMusicSheetInfo`（阻塞插件歌单详情页） | 中 | 缺失 | 4 | 4 | 3 | 5.3 | `src/types/plugin.d.ts`, `src/core/pluginManager/plugin.ts` |
| 2 | `recommendSheets` 页面缺失 | 粗 | 缺失 | 4 | 4 | 3 | 5.3 | `src/pages/recommendSheets/*` |
| 3 | `pluginSheetDetail` 页面缺失 | 粗 | 缺失 | 4 | 3 | 3 | 4.0 | `src/pages/pluginSheetDetail/*` |
| 4 | 首页仍缺 Drawer 行为与深色风格对齐 | 细 | 已实现但有偏差 | 3 | 3 | 2 | 4.5 | `src/pages/home/components/drawer/index.tsx` |
| 5 | `history` 页面缺失 | 粗 | 缺失 | 3 | 2 | 3 | 2.0 | `src/pages/history/index.tsx` |

## 依赖前置判断
1. `getMusicSheetInfo` 是 `pluginSheetDetail` 的前置依赖。
2. `recommendSheets` 可独立落地，但其列表项跳转详情页依赖 `pluginSheetDetail`。
3. 本轮采用“1 中 + 2 粗”组合，优先补齐完整插件歌单链路。

## Top 3 推荐（本轮直接执行）
### 功能点 1: 实现 PluginApi `getMusicSheetInfo`
- 粒度: 中
- 原版参考:
  - `src/types/plugin.d.ts`
  - `src/core/pluginManager/plugin.ts`
- 实现范围:
  - 扩展接口与数据解析
  - `LoadedPlugin` 新增 JS 调用与安全降级

### 功能点 2: 新增 RecommendSheets 页面
- 粒度: 粗
- 原版参考:
  - `src/pages/recommendSheets/index.tsx`
  - `src/pages/recommendSheets/components/body/*`
  - `src/pages/recommendSheets/hooks/*`
- 实现范围:
  - 插件切换、tag 选择、歌单列表分页
  - 首页“推荐歌单”入口跳转至该页面

### 功能点 3: 新增 PluginSheetDetail 页面
- 粒度: 粗
- 原版参考:
  - `src/pages/pluginSheetDetail/index.tsx`
  - `src/pages/pluginSheetDetail/hooks/usePluginSheetMusicList.ts`
- 实现范围:
  - 根据插件歌单加载详情与分页
  - 点击歌曲可解析播放并进入播放器

## 截图对比
| 页面 | 原版截图 | Android版截图 | 差异说明 |
|------|----------|--------------|----------|
| 首页入口 | `docs/convergence/screenshots/original/rn-home.png` | `docs/convergence/screenshots/iteration-1/android-home-v2.png` | 推荐歌单入口已具备，但目标页 `recommendSheets` 尚未实现，当前仍为占位跳转。 |
