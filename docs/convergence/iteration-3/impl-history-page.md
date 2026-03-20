# 功能B：新增 History 页面（播放历史）

## 差异项来源
- analysis 编号: #2（`history` 页面缺失）
- 对应原版:
  - `src/pages/history/index.tsx`

## 技术方案
### 1) 扩展播放器历史数据源
在 `PlayerController` 中新增：
- `playHistory: StateFlow<List<MusicItem>>`
- `clearHistory()`
- 在 `setMediaItemAndPlay()` 内记录历史：
  - 按 `id + platform` 去重
  - 最新项前置
  - 最大保留 200 条

### 2) 新增 History 页面状态与交互
新增 `HistoryViewModel` + `HistoryScreen`：
- 读取 `playerController.playHistory`
- 清空历史
- 点击历史项：`playerController.playQueue(history, index)` 后跳转播放器
- 空态文案：`暂无播放历史`

### 3) 接入路由与首页入口
- 新增 `HistoryRoute`
- 首页“播放历史”入口改为跳转 `HistoryRoute`
- `AppNavHost` 新增 `historyScreen(...)` 接线
- `RoutesTest` 增加 `HistoryRoute` 序列化测试

## 变更文件
- `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryViewModel.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/navigation/HistoryNavigation.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

## 验证记录
- `./gradlew :player:compileDebugKotlin :feature:home:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅
- adb 页面验证:
  - 首页截图: `docs/convergence/screenshots/iteration-3/android-home-iter3.png`
  - History 空态截图: `docs/convergence/screenshots/iteration-3/android-history-empty.png`
  - `uiautomator dump` 可抓到标题“播放历史”和文案“暂无播放历史”
  - 备注: 模拟器仍有间歇性 `System UI isn't responding`，需先点 `Wait` 才能继续链路

## UI 还原度对比
### 页面: 播放历史
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | `src/pages/history/index.tsx` | `android-history-empty.png` | ✅ | 顶栏 + 空态布局已对齐 |
| 间距/尺寸 | RN 代码推断 | `android-history-empty.png` | ⚠️ | 间距体系仍需继续微调 |
| 颜色 | RN 深色风格 | `android-history-empty.png` | ⚠️ | 当前沿用项目现有主题 |
| 字体/字号 | RN 代码推断 | `android-history-empty.png` | ⚠️ | 字号比例待后续收敛 |
| 交互行为 | 历史入口进入、返回、清空历史 | 已验证入口与空态链路 | ✅ | 空态无数据，播放回放需补充数据态验证 |

综合还原度: 70%（2✅3⚠️0❌）
原版截图: `docs/convergence/screenshots/original/rn-history.png`（缺失，当前按 RN 代码推断）
Android版截图: `docs/convergence/screenshots/iteration-3/android-history-empty.png`

## 遗留项
- 在稳定模拟器环境复做 `首页 -> History -> 点击回放 -> 播放器` 端到端截图验收。
