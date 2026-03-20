# 迭代 3 差异分析报告

## 当前状态快照
- 页面覆盖率: 9/19
  - 当前可用页面: `home`, `player`, `playlistDetail`, `search`, `settings`, `topList`, `topListDetail`, `recommendSheets`, `pluginSheetDetail`
- PluginApi 覆盖率: 7/14
  - 已实现: `search`, `getMediaSource`, `getTopLists`, `getTopListDetail`, `getRecommendSheetTags`, `getRecommendSheetsByTag`, `getMusicSheetInfo`
- 面板/弹窗覆盖率: 5/23（粗盘点）

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | PluginApi 缺失 `getMusicInfo/getLyric/importMusicSheet/importMusicItem` | 中 | 缺失 | 4 | 5 | 2 | 10.0 | `src/types/plugin.d.ts`, `src/core/pluginManager/plugin.ts` |
| 2 | `history` 页面缺失 | 粗 | 缺失 | 4 | 4 | 3 | 5.3 | `src/pages/history/index.tsx` |
| 3 | PluginApi 仍缺 `getAlbumInfo/getArtistWorks/getMusicComments` | 中 | 缺失 | 3 | 4 | 3 | 4.0 | `src/types/plugin.d.ts` |
| 4 | 首页结构仍未完全对齐（Drawer、深色风格、歌单区视觉） | 细 | 已实现但有偏差 | 3 | 3 | 2 | 4.5 | `src/pages/home/components/drawer/index.tsx` |
| 5 | PluginSheetDetail / TopListDetail 真实数据态端上验收未完成 | 中 | 已实现但有偏差 | 3 | 2 | 3 | 2.0 | `src/pages/pluginSheetDetail/*`, `src/pages/topListDetail/*` |

## 依赖前置判断
1. `getMusicInfo/getLyric/importMusicSheet/importMusicItem` 无前置依赖，可直接补齐。
2. `history` 页面依赖现有播放器状态，需先补历史数据源（`PlayerController`）。
3. `getAlbumInfo/getArtistWorks/getMusicComments` 与本轮两项无硬依赖，可后置到下轮。

## Top 3 推荐（本轮执行前排序）
### 功能点 1: 批量补齐 4 个 PluginApi 方法
- 粒度: 中
- 原版参考:
  - `src/types/plugin.d.ts`
  - `src/core/pluginManager/plugin.ts`
- 实现范围:
  - 扩展 `PluginApi` 契约
  - 增加 bridge 解析能力（musicInfo merge / lyric / import）
  - `LoadedPlugin` 增加 JS 调用与容错

### 功能点 2: 新增 History 页面（播放历史）
- 粒度: 粗
- 原版参考:
  - `src/pages/history/index.tsx`
- 实现范围:
  - `PlayerController` 增加播放历史维护
  - 首页入口跳转 `history`
  - History 列表、清空历史、点击回放并跳播放器

### 功能点 3: 补齐剩余 3 个 PluginApi 方法
- 粒度: 中
- 原版参考:
  - `src/types/plugin.d.ts`
- 实现范围:
  - `getAlbumInfo/getArtistWorks/getMusicComments`
- 前置依赖: 无
- 本轮处理: 延后到下一轮（优先先打通历史页和高频 API 缺口）

## 截图对比
| 页面 | 原版截图 | Android版截图 | 差异说明 |
|------|----------|--------------|----------|
| 首页（入口复核） | `docs/convergence/screenshots/original/rn-home.png` | `docs/convergence/screenshots/iteration-3/android-home-iter3.png` | 首页入口可见“播放历史”卡片，点击进入独立 History 页。 |
| History 页面（空态） | 代码推断（`src/pages/history/index.tsx`） | `docs/convergence/screenshots/iteration-3/android-history-empty.png` | 已验证标题“播放历史”与空态“暂无播放历史”；模拟器仍存在间歇性 `System UI` ANR。 |
