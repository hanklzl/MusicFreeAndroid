# 迭代 7 差异分析报告

## 当前状态快照
- 页面覆盖率: 13/19
  - 当前可用页面: `home`, `player`, `playlistDetail`, `search`, `settings`, `permissions`, `topList`, `topListDetail`, `recommendSheets`, `pluginSheetDetail`, `history`, `musicDetail`, `albumDetail`, `artistDetail`
- PluginApi 覆盖率: 14/14
- 面板/弹窗覆盖率: 5/23（粗盘点）

## 差异清单（按综合分降序）
| # | 差异项 | 粒度 | 状态 | 影响面 | 可行性 | 成本 | 综合分 | 原版参考文件 |
|---|--------|------|------|--------|--------|------|--------|-------------|
| 1 | `permissions` 页面缺失，无法独立管理悬浮窗/存储权限 | 粗 | 缺失 | 4 | 5 | 2 | 10.0 | `src/pages/permissions/index.tsx` |
| 2 | 首页 UI 仍偏离原版（无 Drawer，顶栏/搜索条/操作卡尺寸偏差） | 细 | 已实现但有偏差 | 4 | 4 | 3 | 5.3 | `src/pages/home/components/drawer/index.tsx`, `src/pages/home/components/navBar.tsx`, `src/pages/home/components/homeBody/operations.tsx` |
| 3 | 真实订阅导入与真实搜索播放验收链路不完整（仅手动 URL 安装） | 中 | 部分实现 | 5 | 4 | 3 | 6.7 | `src/pages/setting/*`, `src/pages/searchPage/*` |
| 4 | 剩余页面缺失（`downloading/fileSelector/musicListEditor/setCustomTheme/searchMusicList`） | 粗 | 缺失 | 5 | 3 | 3 | 5.0 | `src/pages/*` |

## 依赖前置判断
1. 真实搜索/播放验收依赖“可稳定导入订阅源”，先补订阅导入链路。
2. 首页 UI 收敛可与页面补齐并行推进，本轮先做 Drawer 与关键尺寸收敛。

## Top 3 推荐（本轮执行）
### 功能点 1: 新增 `permissions` 页面与设置入口
- 粒度: 粗
- 原版参考:
  - `src/pages/permissions/index.tsx`
- 实现范围:
  - `PermissionsRoute`
  - `PermissionsScreen/ViewModel`
  - 设置页入口与导航接线

### 功能点 2: 首页 Drawer 与关键 UI 尺寸收敛
- 粒度: 细
- 原版参考:
  - `src/pages/home/components/drawer/index.tsx`
  - `src/pages/home/components/navBar.tsx`
  - `src/pages/home/components/homeBody/operations.tsx`
- 实现范围:
  - Home 菜单改为 `ModalNavigationDrawer`
  - Drawer 入口项（基础设置/插件管理/权限管理）
  - 顶栏搜索条与操作卡片尺寸对齐 `rpx`

### 功能点 3: 真实订阅导入 + 搜索默认插件选择
- 粒度: 中
- 原版参考:
  - `src/pages/setting/*`
  - `src/core/router/*`
  - `src/pages/searchPage/*`
- 实现范围:
  - `PluginManager` 支持订阅 JSON 导入与汇总结果
  - 设置页新增默认订阅一键导入（`https://13413.kstore.vip/yuanli/yuanli.json`）
  - `SearchViewModel` 自动选择首个可用插件

## 截图对比
本轮以代码链路收敛和可验证能力补齐为主，端上真实截图（尤其是“订阅导入 -> 搜索 -> 播放”）安排在下一轮稳定模拟器环境补齐。
