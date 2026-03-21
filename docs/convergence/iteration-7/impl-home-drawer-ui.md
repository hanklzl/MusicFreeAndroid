# 功能B：首页 Drawer 与关键 UI 尺寸收敛

## 差异项来源
- analysis 编号: #2（首页 UI 偏差）
- 对应原版:
  - `src/pages/home/components/drawer/index.tsx`
  - `src/pages/home/components/navBar.tsx`
  - `src/pages/home/components/homeBody/operations.tsx`

## 技术方案
### 1) 菜单交互改为 Drawer
- `HomeScreen` 使用 `ModalNavigationDrawer`
- 菜单按钮打开抽屉，不再直接跳转设置页
- Drawer 入口项：
  - 基础设置 -> `SettingsRoute`
  - 插件管理 -> `SettingsRoute`
  - 权限管理 -> `PermissionsRoute`

### 2) 导航接线
- `HomeScreen` 新增 `onNavigateToPermissions`
- `HomeNavigation` 与 `AppNavHost` 同步新增回调透传

### 3) 尺寸与间距收敛
- 顶栏左右内边距收敛到 `rpx(24)`
- 搜索条高度收敛到 `rpx(64)`，胶囊圆角
- 操作区横向 padding 收敛到 `rpx(24)`，纵向 margin `rpx(32)`
- 操作卡高度收敛到 `rpx(160)`，圆角 `rpx(18)`

### 4) 交互可靠性
- 抽离 `navigateThenCloseDrawer`，先导航后关闭抽屉，避免关闭动画中断导致丢导航
- 补充 `HomeDrawerNavigationHelperTest` 覆盖该行为

## 变更文件
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`
- `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigationHelperTest.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

## 验证记录
- `./gradlew :feature:home:testDebugUnitTest --tests "*HomeDrawer*"` ✅
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅

## UI 还原度对比
### 页面: 首页（Home）
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | 顶栏 + 操作区 + 列表 + Drawer | 同结构已接线 | ✅ | Drawer 可用 |
| 间距/尺寸 | RN rpx | 关键区域已按 `rpx` 收敛 | ✅ | 操作卡/搜索条已对齐目标值 |
| 颜色 | 原版主题色系 | 复用 `MusicFreeTheme` | ⚠️ | 深色细节仍待继续收敛 |
| 字体/字号 | 原版字体体系 | 现有字体 token | ⚠️ | 待截图复核 |
| 交互行为 | 菜单打开抽屉并导航 | 已支持且补了丢导航防护 | ✅ | 具备单测 |

综合还原度: 80%（3✅2⚠️0❌）
原版截图: 待补
Android版截图: 待补
