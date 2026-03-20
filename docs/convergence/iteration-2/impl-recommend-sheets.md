# 功能B：新增 RecommendSheets 页面

## 差异项来源
- analysis 编号: #2（`recommendSheets` 页面缺失）
- 对应原版:
  - `src/pages/recommendSheets/index.tsx`
  - `src/pages/recommendSheets/components/body/*`
  - `src/pages/recommendSheets/hooks/*`

## 技术方案
### 1) 新增页面状态与 VM
实现 `RecommendSheetsViewModel`：
- 插件列表加载与默认插件选择
- tag 列表加载（含 pinned 与分组）
- 歌单分页加载、刷新、加载更多
- 插件缺失与请求失败错误态

### 2) 新增 Compose 页面
实现 `RecommendSheetsScreen`：
- 顶部 `TopAppBar` + 返回
- 插件切换分段按钮
- tag `FilterChip` 横向选择
- 歌单列表 + 空态/错误态/加载更多

### 3) 新增路由与导航接线
- 新增 `RecommendSheetsRoute`
- 首页“推荐歌单”卡片从占位跳转改为真实导航
- `AppNavHost` 增加 `recommendSheetsScreen(...)` 接线

## 变更文件
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsUiState.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/navigation/RecommendSheetsNavigation.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

## 验证记录
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin` ✅
- adb 验证（无插件空态）:
  - 首页截图: `docs/convergence/screenshots/iteration-2/android-home-v3.png`
  - 推荐歌单页截图: `docs/convergence/screenshots/iteration-2/android-recommend-sheets-empty.png`

## UI 还原度对比
### 页面: 推荐歌单（空状态）
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | `src/pages/recommendSheets/index.tsx` | `android-recommend-sheets-empty.png` | ✅ | 已有独立页面、标题栏、空态文案 |
| 间距/尺寸 | RN 代码推断 | `android-recommend-sheets-empty.png` | ⚠️ | 当前仍沿用现有浅色主题尺寸体系 |
| 颜色 | RN 深色风格 | `android-recommend-sheets-empty.png` | ⚠️ | 主题色尚未完成收敛 |
| 字体/字号 | RN 代码推断 | `android-recommend-sheets-empty.png` | ⚠️ | 主体可读，字号仍需微调 |
| 交互行为 | 点击首页入口进入页 | adb 手动操作 | ✅ | 首页入口已打通 |

综合还原度: 70%（2✅3⚠️0❌）
原版截图: `docs/convergence/screenshots/original/rn-recommend-sheets.png`（缺失，当前按 RN 代码推断）
Android版截图: `docs/convergence/screenshots/iteration-2/android-recommend-sheets-empty.png`

## 遗留项
- 真实插件数据态（tag/列表）在当前模拟器环境未完成稳定验收，进入 backlog。
