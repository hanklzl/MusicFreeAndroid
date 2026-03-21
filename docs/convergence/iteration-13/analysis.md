# 迭代 13 差异分析报告

## 当前状态快照
- 分析时间: 2026-03-21
- 页面覆盖率: 15/19
  - RN 基线仍以 `/Users/zili/code/android/MusicFree/src/core/router/routes.tsx` 的 19 个页面路由为准。
  - Android 当前已在 `AppNavHost` 接线的页面为: `home`, `player`, `playlistDetail`, `search`, `history`, `settings`, `permissions`, `topList`, `topListDetail`, `recommendSheets`, `pluginSheetDetail`, `musicDetail`, `albumDetail`, `artistDetail`, `searchMusicList`。
  - `searchMusicList` 已在当前分支通过以下提交落地并修复启动问题:
    - `06e6d51 feat(convergence-13): add search music list screen`
    - `529a1c4 fix(convergence-13): repair search music list navigation startup`
  - 因此，当前真正仍缺失的页面已收敛为 4 个: `musicListEditor`、`fileSelector`、`downloading`、`setCustomTheme`。
- PluginApi 覆盖率: 14/14
  - 当前分支仍可在 `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt` 与相关实现中对齐 14 个接口。
- 面板/弹窗覆盖率: 5/23
  - RN `src/components/panels/types` 当前有 18 个非 `index` 面板类型。
  - RN `src/components/dialogs/components` 当前仍维持 `STATUS.md` 使用的 5 个业务 dialog 口径；本轮未发现 Android 侧覆盖率有新增。
- 验证能力现状:
  - controller 已确认以下检查通过: `RoutesTest`、`feature:home searchmusiclist` 单测、`feature:home/app compileDebugKotlin`、`MainActivityStartupTest`。
  - 模拟器验证期间曾暴露一次运行时启动崩溃，现已由 `529a1c4` 修复；因此当前问题不再是 `searchMusicList` 是否存在，而是它与整体产品是否真的收敛。
  - iteration-13 已有新的 Android 当前态截图资产:
    - `docs/convergence/screenshots/iteration-13/android-home-current.png`
    - `docs/convergence/screenshots/iteration-13/android-drawer-current.png`
    - `docs/convergence/screenshots/iteration-13/android-history-current.png`
  - 用户提供的原版截图显示，当前差距仍明显存在于两类维度:
    - UI fidelity: 全局视觉系统、Drawer 层次、列表密度、播放器/卡片气质仍未贴近原版。
    - capability density: `musicListEditor`、`fileSelector` 等集合能力/系统能力缺口仍大。
  - 当前模拟器 UI text dump 也支持“页面存在但仍偏薄”的判断:
    - Home 已出现 `点击这里开始搜索`、`推荐歌单`、`榜单`、`播放历史`、`本地音乐`、`播放列表`、`没有找到本地音乐`
    - Drawer 已出现 `更多功能`、`基础设置`、`插件管理`、`权限管理`
    - History 已出现 `播放历史`、`暂无播放历史`
  - Plugin-backed detail flows 依然缺少充分的真实数据端上验证，当前不能因为页面已存在就判定收敛。

## 差异清单（按当前真实缺口排序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 依赖/验证说明 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|--------------|-------------|
| 1 | `musicListEditor` 页面缺失 | 粗 | 缺失 | 5 | 3 | 4 | 3.8 | `searchMusicList` 已补齐后，集合类能力里最显眼的剩余缺口转为批量编辑、加歌单、批量操作 | `src/pages/musicListEditor/index.tsx`, `src/pages/musicListEditor/components/*` |
| 2 | `fileSelector` 缺失且属于子系统 unlock | 粗 | 缺失 | 5 | 2 | 4 | 2.5 | 不只是单页；还关系到本地导入、路径选择、备份/导出等后续能力 | `src/pages/fileSelector/index.tsx`, `src/pages/fileSelector/fileItem.tsx` |
| 3 | Plugin-backed detail flows 真实数据态仍缺强验证 | 中 | 部分实现 | 5 | 4 | 2 | 10.0 | `pluginSheetDetail`、`topListDetail`、`musicDetail -> albumDetail/artistDetail` 仍缺真实插件数据截图与回放记录 | `src/pages/musicDetail/index.tsx`, `src/pages/albumDetail/index.tsx`, `src/pages/artistDetail/index.tsx` |
| 4 | `searchMusicList` 已实现，但仍只算“部分收敛” | 中 | 已实现但未收口 | 4 | 4 | 2 | 8.0 | 已有路由/单测/编译/启动验证，但还没有 dedicated screenshot 与原版能力边界核对 | `src/pages/searchMusicList/index.tsx`, `src/pages/searchMusicList/searchResult.tsx` |
| 5 | 首页 / Drawer / History 当前 UI 与原版差距仍大 | 细 | 已实现但有明显偏差 | 4 | 3 | 3 | 4.0 | iteration-13 截图已足够证明“当前长什么样”，也足够证明“还没收敛” | `src/pages/home/index.tsx`, `src/pages/home/components/drawer/index.tsx`, `src/pages/history/index.tsx` |
| 6 | `downloading` 页面缺失 | 粗 | 缺失 | 4 | 1 | 5 | 0.8 | 依赖下载队列、状态枚举、下载列表 UI，目前仍缺下载基础设施 | `src/pages/downloading/index.tsx`, `src/pages/downloading/downloadingList.tsx` |
| 7 | `setCustomTheme` 页面缺失 | 粗 | 缺失 | 3 | 1 | 5 | 0.6 | 依赖图片选择、背景模糊/透明度与运行时主题持久化，当前基础仍不足 | `src/pages/setCustomTheme/index.tsx`, `src/pages/setCustomTheme/body.tsx` |

## 依赖前置判断
1. `searchMusicList` 已经从“缺页”转成“已实现但未收口”。后续判断它的优先级时，应看 fidelity 与 capability，而不是页面 existence。
2. 在剩余 4 个缺页里，`musicListEditor` 应排第一，因为它最直接补齐集合操作密度，也是当前用户体感最容易察觉的能力缺口。
3. `fileSelector` 不应被当成普通页面排期。它是 SAF/本地文件交互底座的入口，后续会影响本地扫描、路径选择和导入导出能力。
4. Plugin-backed detail flows 虽然不是“缺页”，但验证债很重；在没有真实插件数据回放前，不适合把这些页面写成“已收敛”。
5. `downloading` 与 `setCustomTheme` 仍需要额外基础设施，短期内的价值主要是完成页面计数，而不是提高高频主链路可用性。

## Top 3 推荐（本轮执行）
### 功能点 1: `musicListEditor-lite`
- 粒度: 中
- 原版参考:
  - `src/pages/musicListEditor/index.tsx`
  - `src/pages/musicListEditor/components/*`
- Android 现状依据:
  - `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/*`
  - `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/*`
- 实现范围:
  - 先做 playlist source 的最小可用版，多选、删除、加歌单/队列等高频操作优先。
  - 复用现有集合来源与 player queue，不把下载能力绑进第一版。
- 前置依赖:
  - 需要先明确共享 collection source / capability model，但不依赖 `fileSelector` 或下载底座。
- 推荐理由:
  - `searchMusicList` 已落地后，集合工具链最大的空洞就是编辑能力。
  - 它比 `downloading` / `setCustomTheme` 更接近真实高频使用场景。

### 功能点 2: `fileSelector` / SAF 基础
- 粒度: 粗
- 原版参考:
  - `src/pages/fileSelector/index.tsx`
  - `src/pages/fileSelector/fileItem.tsx`
- Android 现状依据:
  - 当前分支还没有对应的 SAF/file picker 子系统
- 实现范围:
  - 先建立最小存储访问网关与结果模型，再决定首个调用方（本地扫描、备份导出、下载路径三选一）。
- 前置依赖:
  - 无现成页面可复用，需从系统交互底层起步。
- 推荐理由:
  - 这是后续多个页面和能力的 unlock，不只是为了补路由数量。
  - 若没有它，本地能力相关的收敛会持续卡住。

### 功能点 3: Plugin-backed detail flow 验证补强
- 粒度: 中
- 原版参考:
  - `src/pages/pluginSheetDetail/index.tsx`
  - `src/pages/topListDetail/index.tsx`
  - `src/pages/musicDetail/index.tsx`
- Android 现状依据:
  - `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheetdetail/*`
  - `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/*`
  - `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/*`
- 实现/验证范围:
  - 用真实插件数据回放 `recommendSheets`、`topList`、`musicDetail -> albumDetail/artistDetail`。
  - 为每个终态保留截图与失败记录，不再用“待补验收”笼统占位。
- 前置依赖:
  - 依赖稳定插件环境，但不依赖新增页面开发。
- 推荐理由:
  - 当前最大风险之一不是“没有页面”，而是“页面在真实插件数据下是否可靠”。
  - 这类验证能直接决定后续状态文档是否可信。

## 截图对比
| 页面/链路 | 原版截图 | Android 截图 | 当前结论 |
|----------|----------|--------------|----------|
| 首页 | `docs/convergence/screenshots/original/rn-home.png` | `docs/convergence/screenshots/iteration-13/android-home-current.png` | iteration-13 已有当前基线；结合 UI text dump 可确认首页模块存在，但顶部系统、列表密度、卡片气质差距仍明显 |
| Drawer | 用户提供原版截图（仓库内无配套文件） | `docs/convergence/screenshots/iteration-13/android-drawer-current.png` | 当前截图与 `更多功能/基础设置/插件管理/权限管理` 文本足以证明 Drawer 可达，但层级深度与视觉语言仍未贴近原版 |
| History | 用户提供原版截图（仓库内无配套文件） | `docs/convergence/screenshots/iteration-13/android-history-current.png` | `播放历史/暂无播放历史` 说明历史页当前可达，但信息密度与操作丰富度仍有限 |
| `searchMusicList` | 无现成原版截图资产 | 无 dedicated iteration-13 截图 | 它不再是缺页，但还没有视觉/交互对照证据，因此只能记为“部分收敛” |
| 剩余缺页 (`musicListEditor`/`fileSelector`/`downloading`/`setCustomTheme`) | 无 | 无 | 当前仍主要依据代码差异判断缺口，截图无法替代功能缺失结论 |

补充说明:
- iteration-13 已经有可引用的 Android 当前截图，因此不应再沿用“当前无截图资产”的旧表述。
- 这些截图与通过的路由/启动验证只能说明当前分支比旧文档更新，并不能说明界面或能力已与原版收敛。
