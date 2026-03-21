# 功能A：新增 Permissions 页面与设置入口

## 差异项来源
- analysis 编号: #1（`permissions` 页面缺失）
- 对应原版:
  - `src/pages/permissions/index.tsx`

## 技术方案
### 1) 路由与导航
- 新增 `PermissionsRoute`
- `AppNavHost` 与 `feature/settings` 导航扩展新增 `permissionsScreen(...)`
- 设置页新增“权限管理”入口

### 2) 页面能力
- 新增 `PermissionsScreen` + `PermissionsViewModel`
- 两个权限行：
  - 悬浮窗权限（`Settings.canDrawOverlays`）
  - 存储/音频读取权限（`requiredAudioPermission()`）
- 权限状态展示：`已授权` / `未授权`
- 页面恢复到前台时刷新状态

### 3) 可靠性增强
- 抽离 `requiredAudioPermission` 到 `core/permissions/AudioPermission.kt`，Home 与 Settings 共用
- 悬浮窗设置跳转增加可解析性保护与安全 fallback
- 权限按钮在已授权状态禁用，避免重复触发

### 4) 测试
- `RoutesTest` 新增 `PermissionsRoute` 序列化断言
- 新增 `PermissionsHelpersTest`（含 API 分支与 overlay fallback 场景）

## 变更文件
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- `core/src/main/java/com/zili/android/musicfreeandroid/core/permissions/AudioPermission.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/navigation/SettingsNavigation.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/navigation/PermissionsNavigation.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsViewModel.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsHelpers.kt`
- `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsHelpersTest.kt`

## 验证记录
- `./gradlew :feature:settings:testDebugUnitTest --tests "*PermissionsHelpers*"` ✅
- `./gradlew :feature:settings:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅

## UI 还原度对比
### 页面: 权限管理（Permissions）
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | 权限行列表 | Compose 卡片列表 | ✅ | 顶栏 + 两行权限结构一致 |
| 间距/尺寸 | RN rpx | 使用 `rpx` 体系 | ⚠️ | 需端上截图微调 |
| 颜色 | 主题色 + 卡片 | 复用 `MusicFreeTheme` | ⚠️ | 需实机对比 |
| 字体/字号 | 原版字体体系 | 现有字体 token | ⚠️ | 需截图复核 |
| 交互行为 | 跳系统权限页 | 已接线并支持状态刷新 | ✅ | 含安全 fallback |

综合还原度: 70%（2✅3⚠️0❌）
原版截图: 待补
Android版截图: 待补
