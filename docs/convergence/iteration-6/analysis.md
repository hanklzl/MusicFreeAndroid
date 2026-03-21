# 迭代 6 差异分析报告

## 当前状态快照
- 页面覆盖率: 11/19
  - 当前可用页面: `home`, `player`, `playlistDetail`, `search`, `settings`, `topList`, `topListDetail`, `recommendSheets`, `pluginSheetDetail`, `history`, `musicDetail`
- PluginApi 覆盖率: 14/14
- 面板/弹窗覆盖率: 5/23（粗盘点）

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | `albumDetail/artistDetail` 页面缺失（API 已具备） | 粗 | 缺失 | 4 | 4 | 3 | 5.3 | `src/pages/albumDetail/*`, `src/pages/artistDetail/*` |
| 2 | 首页结构仍未完全对齐（Drawer、深色风格、歌单区视觉） | 细 | 已实现但有偏差 | 3 | 3 | 2 | 4.5 | `src/pages/home/components/drawer/index.tsx` |
| 3 | `musicDetail` 真实插件数据态与截图验收待补 | 中 | 已实现但有偏差 | 3 | 3 | 3 | 3.0 | `src/pages/musicDetail/*` |
| 4 | TopListDetail / PluginSheetDetail / History 真实数据态待复验 | 中 | 已实现但有偏差 | 3 | 2 | 3 | 2.0 | `src/pages/topListDetail/*`, `src/pages/pluginSheetDetail/*`, `src/pages/history/*` |

## 依赖前置判断
1. `albumDetail/artistDetail` 依赖 `getAlbumInfo/getArtistWorks`，上一轮已具备 API 能力，可直接落地。
2. 以 `musicDetail` 为入口可最小化导航接线成本。

## Top 3 推荐（本轮直接执行）
### 功能点 1: 新增 AlbumDetail 页面
- 粒度: 粗
- 原版参考:
  - `src/pages/albumDetail/index.tsx`
- 实现范围:
  - `AlbumDetailRoute`
  - `AlbumDetailScreen/ViewModel/UiState`

### 功能点 2: 新增 ArtistDetail 页面
- 粒度: 粗
- 原版参考:
  - `src/pages/artistDetail/index.tsx`
- 实现范围:
  - `ArtistDetailRoute`
  - `ArtistDetailScreen/ViewModel/UiState`

### 功能点 3: 从 MusicDetail 打通导航入口
- 粒度: 中
- 原版参考:
  - `src/pages/musicDetail/*`
- 实现范围:
  - `musicDetail` 增加“专辑预览/歌手作品”入口按钮
  - `AppNavHost` 接线至 `albumDetail/artistDetail`

## 截图对比
本轮以页面与路由链路落地为主。受插件数据态与模拟器稳定性影响，`musicDetail -> albumDetail/artistDetail` 端上截图延后到下一轮补做。
