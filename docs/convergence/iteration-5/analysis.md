# 迭代 5 差异分析报告

## 当前状态快照
- 页面覆盖率: 10/19
  - 当前可用页面: `home`, `player`, `playlistDetail`, `search`, `settings`, `topList`, `topListDetail`, `recommendSheets`, `pluginSheetDetail`, `history`
- PluginApi 覆盖率: 14/14
- 面板/弹窗覆盖率: 5/23（粗盘点）

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | 首页结构仍未完全对齐（Drawer、深色风格、歌单区视觉） | 细 | 已实现但有偏差 | 3 | 3 | 2 | 4.5 | `src/pages/home/components/drawer/index.tsx` |
| 2 | `musicDetail` 页面缺失 | 粗 | 缺失 | 4 | 4 | 3 | 5.3 | `src/pages/musicDetail/index.tsx` |
| 3 | 新增 PluginApi（album/artist/comments）缺少页面消费链路 | 中 | 已实现但有偏差 | 4 | 4 | 3 | 5.3 | `src/types/plugin.d.ts`, `src/pages/musicDetail/*` |
| 4 | TopListDetail / PluginSheetDetail 真实数据态与播放链路待复验 | 中 | 已实现但有偏差 | 3 | 2 | 3 | 2.0 | `src/pages/topListDetail/*`, `src/pages/pluginSheetDetail/*` |

## 依赖前置判断
1. `musicDetail` 是 comments/lyric/musicInfo 的天然消费页面，可直接承接迭代 4 的接口补齐。
2. 从 TopListDetail/PluginSheetDetail 增加“详情”入口即可打通最短导航链路。

## Top 3 推荐（本轮直接执行）
### 功能点 1: 新增 `musicDetail` 页面
- 粒度: 粗
- 原版参考:
  - `src/pages/musicDetail/index.tsx`
- 实现范围:
  - 新增 `MusicDetailRoute`
  - 新增 `MusicDetailScreen/ViewModel/UiState`

### 功能点 2: 接入详情入口
- 粒度: 中
- 原版参考:
  - `src/pages/topListDetail/*`
  - `src/pages/pluginSheetDetail/*`
- 实现范围:
  - 两个列表页每首歌新增“详情”入口
  - `AppNavHost` 接线到 `MusicDetailRoute`

### 功能点 3: 在 musicDetail 消费插件详情 API
- 粒度: 中
- 原版参考:
  - `src/types/plugin.d.ts`
- 实现范围:
  - 调用 `getMusicInfo/getLyric/getMusicComments`
  - 预览调用 `getAlbumInfo/getArtistWorks`

## 截图对比
本轮以页面与导航链路、编译验证为主。受插件数据态和模拟器稳定性影响，`musicDetail` 端上截图延后到下一轮补做。
