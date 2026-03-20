# 迭代 4 差异分析报告

## 当前状态快照
- 页面覆盖率: 10/19
  - 当前可用页面: `home`, `player`, `playlistDetail`, `search`, `settings`, `topList`, `topListDetail`, `recommendSheets`, `pluginSheetDetail`, `history`
- PluginApi 覆盖率: 11/14
  - 已实现: `search`, `getMediaSource`, `getMusicInfo`, `getLyric`, `getMusicSheetInfo`, `importMusicSheet`, `importMusicItem`, `getTopLists`, `getTopListDetail`, `getRecommendSheetTags`, `getRecommendSheetsByTag`
- 面板/弹窗覆盖率: 5/23（粗盘点）

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | PluginApi 仍缺 `getAlbumInfo/getArtistWorks/getMusicComments` | 中 | 缺失 | 4 | 5 | 2 | 10.0 | `src/types/plugin.d.ts` |
| 2 | 首页结构仍未完全对齐（Drawer、深色风格、歌单区视觉） | 细 | 已实现但有偏差 | 3 | 3 | 2 | 4.5 | `src/pages/home/components/drawer/index.tsx` |
| 3 | TopListDetail / PluginSheetDetail 真实数据态与播放链路待复验 | 中 | 已实现但有偏差 | 3 | 2 | 3 | 2.0 | `src/pages/topListDetail/*`, `src/pages/pluginSheetDetail/*` |
| 4 | History 列表态与回放链路（有数据）待补验收 | 中 | 已实现但有偏差 | 3 | 3 | 3 | 3.0 | `src/pages/history/index.tsx` |

## 依赖前置判断
1. 补齐剩余 3 个 PluginApi 方法是后续专辑/歌手/评论相关页面和链路的前置能力。
2. 首页视觉收敛与数据态复验不依赖本轮插件接口补齐，可并行排队到后续迭代。

## Top 3 推荐（本轮直接执行）
### 功能点 1: 补齐 `getAlbumInfo/getArtistWorks/getMusicComments`
- 粒度: 中
- 原版参考:
  - `src/types/plugin.d.ts`
- 实现范围:
  - `PluginApi` 新增 3 个方法
  - `LoadedPlugin` 新增 JS 调用与容错

### 功能点 2: 扩展桥接模型
- 粒度: 中
- 原版参考:
  - `src/types/album.d.ts`
  - `src/types/artist.d.ts`
  - `src/types/media.d.ts`
- 实现范围:
  - 新增 `AlbumItemBase/ArtistItemBase/AlbumInfoResult/ArtistWorksResult/MusicComment`
  - `JsBridge` 增加映射与解析

### 功能点 3: 补齐解析测试
- 粒度: 细
- 原版参考:
  - `src/types/plugin.d.ts`
- 实现范围:
  - 新增 album/artist/comments 解析单测

## 截图对比
本轮为插件接口与桥接层能力补齐，不涉及新增 UI 页面，截图对比延后到消费这些能力的页面迭代中完成。
