# 功能B：首页结构与交互入口对齐

## 差异项来源
- analysis 编号: #2（首页结构与视觉层级偏差）
- 对应原版:
  - `src/pages/home/components/navBar.tsx`
  - `src/pages/home/components/homeBody/operations.tsx`
  - `src/pages/home/components/homeBody/sheets.tsx`

## 技术方案
### 1) 重构首页头部为“菜单 + 搜索入口”
- 移除原 `TopAppBar` 标题栏
- 新增 `HomeHeader`：左侧菜单按钮 + 右侧胶囊搜索入口
- 搜索入口点击直达 `SearchRoute`

### 2) 新增四功能快捷卡片区
新增 `HomeOperations`，补齐以下入口：
- 推荐歌单（暂跳搜索）
- 榜单（跳 `TopListRoute`）
- 播放历史（暂跳播放器）
- 本地音乐（切回本地音乐 tab）

### 3) 保留已有本地音乐与播放列表能力
- 保留 `LocalMusicPage`、`PlaylistSection` 与长按“添加到播放列表”
- 仅调整信息架构与交互入口，不回退已实现能力

## 变更文件
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

## 验证记录
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin` ✅
- adb 截图验证：首页结构已变化为“菜单 + 搜索 + 四快捷入口”

## UI 还原度对比
### 页面: 首页
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | `docs/convergence/screenshots/original/rn-home.png` | `docs/convergence/screenshots/iteration-1/android-home-v2.png` | ⚠️ | 顶部搜索与四入口已对齐，但歌单区仍保留现有本地/播放列表双 tab 结构 |
| 间距/尺寸 | 同上 | 同上 | ⚠️ | 卡片尺寸与间距接近，仍未完全按 RN rpx 精准对齐 |
| 颜色 | 同上 | 同上 | ⚠️ | 结构对齐后仍使用当前主题配色，深色模式还原未完成 |
| 字体/字号 | 同上 | 同上 | ✅ | 复用全局 FontSizes，信息层级清晰 |
| 交互行为 | 同上 | 同上 | ⚠️ | 搜索/榜单/本地入口可用；菜单当前映射到设置页（非抽屉） |

综合还原度: 60%
原版截图: `docs/convergence/screenshots/original/rn-home.png`
Android版截图: `docs/convergence/screenshots/iteration-1/android-home-v2.png`

## 遗留项
- 侧边抽屉（Drawer）未接入。
- 推荐歌单/播放历史仍是过渡跳转，待对应页面落地后替换。
