# 功能C：真实订阅导入与搜索播放验收链路

## 差异项来源
- analysis 编号: #3（真实订阅与真实搜索播放链路不完整）
- 对应原版:
  - `src/pages/setting/*`
  - `src/pages/searchPage/*`

## 技术方案
### 1) PluginManager 订阅导入能力
- 新增订阅解析模型与结果模型：
  - `SubscriptionParser`
  - `SubscriptionInstallResult`
- 支持订阅 JSON 结构 `plugins[].url`
- `installFromSubscriptionUrl(...)` 返回汇总：总数/成功/失败
- 全批次导入在单次 mutex 内串行执行，避免并发交错
- 采用“临时文件 -> 验证 -> 原子替换”流程，避免失败覆盖已有可用插件
- 订阅条目文件名使用 URL hash + index，规避同名冲突

### 2) 设置页一键验收入口
- 新增“默认订阅导入”卡片，默认地址：
  - `https://13413.kstore.vip/yuanli/yuanli.json`
- `SettingsViewModel.installDefaultSubscription()` 调用导入 API
- 使用现有 `InstallState` 展示导入结果
- Loading 态禁止重复触发，避免并发重复导入

### 3) 搜索可用性增强
- `SearchViewModel` 在插件列表可用时自动选中首个插件
- 若当前选中插件失效（被卸载），自动回退到新的可用插件（或置空）

### 4) 测试
- `PluginSubscriptionParserTest`：有效/无效订阅解析
- `SubscriptionFileNamesTest`：同名 URL 碰撞规避
- `SettingsViewModelTest`：Loading 态重入保护

## 变更文件
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/SubscriptionParser.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/SubscriptionInstallResult.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/SubscriptionFileNames.kt`
- `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/manager/PluginSubscriptionParserTest.kt`
- `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/manager/SubscriptionFileNamesTest.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt`
- `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/MainDispatcherRule.kt`
- `feature/settings/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`

## 验证记录
- `./gradlew :plugin:testDebugUnitTest --tests "*Subscription*"` ✅
- `./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsViewModel*"` ✅
- `./gradlew :feature:settings:compileDebugKotlin :feature:search:compileDebugKotlin :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅

## UI 还原度对比
### 页面: 设置（订阅导入）/ 搜索
| 维度 | 原版 | Android版 | 还原度 | 备注 |
|------|------|-----------|--------|------|
| 布局结构 | 设置含插件管理入口 | 新增默认订阅导入卡片 | ⚠️ | 功能入口已具备，视觉细节待调 |
| 间距/尺寸 | RN rpx | 复用现有设置页布局 | ⚠️ | 待截图复核 |
| 颜色 | 原版主题色系 | 复用 `MusicFreeTheme` | ⚠️ | 待实机对比 |
| 字体/字号 | 原版字体体系 | 现有字体 token | ⚠️ | 待截图复核 |
| 交互行为 | 导入订阅后搜索播放 | 代码链路已打通 | ✅ | 需端上真实验收截图 |

综合还原度: 60%（1✅4⚠️0❌）
原版截图: 待补
Android版截图: 待补
