# 迭代 1 差异分析报告

## 当前状态快照
- 页面覆盖率: 5/19
  - Android 当前可导航 Screen: `home`, `player`, `playlistDetail`, `search`, `settings`
  - 参考: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- PluginApi 覆盖率: 2/14
  - 已实现: `search`, `getMediaSource`
  - 参考: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt`
- 面板/弹窗覆盖率: 5/23（粗盘点）
  - 原版基准: panel 类型 18 + 业务 dialog 5（排除 `index.ts` 和基础 dialog）
  - Android 当前: 5 个业务 dialog（`Create/Rename/Delete/AddToPlaylist/InstallPlugin`），未见 panel 体系

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | PluginApi 方法覆盖不足（缺 12/14） | 中 | 缺失 | 5 | 3 | 2 | 7.5 | `src/types/plugin.d.ts`, `src/core/pluginManager/plugin.ts` |
| 2 | 首页结构与视觉层级偏差（缺抽屉入口、搜索栏、四功能入口卡片、歌单区布局） | 细 | 已实现但有偏差 | 5 | 4 | 3 | 6.7 | `src/pages/home/components/navBar.tsx`, `src/pages/home/components/homeBody/operations.tsx`, `src/pages/home/components/homeBody/sheets.tsx` |
| 3 | `topList` / `topListDetail` 页面链路缺失 | 粗 | 缺失 | 4 | 3 | 3 | 4.0 | `src/pages/topList/*`, `src/pages/topListDetail/*` |
| 4 | `recommendSheets` 页面缺失 | 粗 | 缺失 | 4 | 3 | 3 | 4.0 | `src/pages/recommendSheets/*` |
| 5 | `history` 页面缺失 | 粗 | 缺失 | 3 | 4 | 3 | 4.0 | `src/pages/history/*` |
| 6 | `pluginSheetDetail` 页面缺失 | 粗 | 缺失 | 4 | 2 | 2 | 4.0 | `src/pages/pluginSheetDetail/*` |
| 7 | 数据模型不完整（`IMusicItem` 字段与多媒体实体缺口） | 中 | 部分实现 | 4 | 3 | 3 | 4.0 | `src/types/music.d.ts`, `src/types/album.d.ts`, `src/types/artist.d.ts` |
| 8 | 测试插件 fallback 快照缺失（`test/fixtures/yuanli-snapshot.json`） | 细 | 缺失 | 3 | 5 | 5 | 3.0 | 设计规范 `2026-03-21-convergence-loop-design.md` |

## 依赖前置判断
1. `PluginApi` 扩展（#1）是 `topList/topListDetail`（#3）、`recommendSheets`（#4）、`pluginSheetDetail`（#6）的前置依赖。
2. 首页结构对齐（#2）可独立推进，不阻塞 API 与页面链路。
3. 数据模型补齐（#7）建议与 #1 同步推进，避免重复改动 DTO/Mapper。

## Top 3 推荐
### 功能点 1: 补齐 PluginApi 核心缺失能力（优先榜单与推荐链路）
- 粒度: 中
- 原版参考:
  - `src/types/plugin.d.ts`
  - `src/core/pluginManager/plugin.ts`
- 实现范围:
  - 第一批优先补齐 `getTopLists`, `getTopListDetail`, `getRecommendSheetTags`, `getRecommendSheetsByTag`
  - 同步梳理 Kotlin 侧返回模型，确保字段与 RN 侧结构兼容
- 前置依赖: 无（本轮最优先）

### 功能点 2: 首页结构与交互入口对齐（UI 收敛）
- 粒度: 细
- 原版参考:
  - `src/pages/home/components/navBar.tsx`
  - `src/pages/home/components/homeBody/operations.tsx`
  - `src/pages/home/components/homeBody/sheets.tsx`
- 实现范围:
  - 顶部改为“菜单 + 搜索入口”形态
  - 补齐四个快捷入口（推荐歌单/榜单/播放历史/本地音乐）
  - 保留现有本地音乐能力，向原版首页信息架构对齐
- 前置依赖: 无（可与功能点 1 并行设计、串行落地）

### 功能点 3: 新增 TopList/TopListDetail 页面链路
- 粒度: 粗
- 原版参考:
  - `src/pages/topList/index.tsx`
  - `src/pages/topList/components/topListBody.tsx`
  - `src/pages/topListDetail/index.tsx`
  - `src/pages/topListDetail/hooks/useTopListDetail.ts`
- 实现范围:
  - 新增榜单列表页与榜单详情页
  - 首页快捷入口可跳转至榜单页
  - 支持分页加载与基础错误态
- 前置依赖: 有（依赖功能点 1 的 `getTopLists/getTopListDetail`）

## 截图对比
| 页面 | 原版截图 | Android版截图 | 差异说明 |
|------|----------|--------------|----------|
| 首页 | `docs/convergence/screenshots/original/rn-home.png` | `docs/convergence/screenshots/iteration-1/android-home.png` | Android 当前仍为早期橙色 TopBar + 双 Tab 的本地音乐布局；原版为深色主题、搜索入口、四功能卡片与歌单区，信息架构和视觉风格均明显不一致。 |

## 备注
- 本轮已验证模拟器在线、两端 App 可启动并可截图。
- 由于当前计划处于“Top 3 评审点”，实现阶段需用户确认后再进入。
