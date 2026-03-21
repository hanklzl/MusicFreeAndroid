# 迭代 13 验证报告

## 功能验证
| 功能点 | 操作路径 | 结果 | 问题 |
|--------|----------|------|------|
| `searchMusicList` 页面（playlist/history source） | playlist/history 入口接线 + 路由/单测/编译 | ✅通过 | source 仍只覆盖 `playlist/history`，local music / generic sheet 待后续扩展 |
| `searchMusicList` 启动回归修复 | 启动 `MainActivity` -> 构建 NavGraph -> 进入 RESUMED | ✅通过 | 运行时回归已修复，但 shell 启动链路曾受模拟器安装态影响 |
| 默认订阅真实集成链路 | `installFromSubscriptionUrl -> WY search -> getMediaSource` | ✅通过 | 真实端上点击和截图仍需补齐 |

## 运行时回归说明

- 发现时间: 2026-03-21
- 触发方式: controller 使用 emulator 做最终启动验收时，应用启动后立即失去前台，截图链路串回原版 RN app。
- 根因:
  - `SearchMusicListRoute` 使用了嵌套的 `SearchMusicListSource` typed route 参数。
  - Navigation Compose 在构建图时无法为该参数推断 `NavType`，导致 `MainActivity` 启动期崩溃。
- 修复:
  - 提交 `529a1c4` `fix(convergence-13): repair search music list navigation startup`
  - 将 route 扁平化为 primitive args（`sourceType` + `playlistId`）
  - 新增 `app/src/androidTest/java/com/zili/android/musicfreeandroid/MainActivityStartupTest.kt`

## 构建与测试
- `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.searchmusiclist.*"` ✅
- `./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` ✅
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin` ✅
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.MainActivityStartupTest` ✅
- `./gradlew :plugin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeIntegrationTest#defaultSubscription_installAndWyPlaybackChain_succeeds` ✅

## UI 还原度验证
| 页面 | 原版参考 | Android截图 | 当前结论 | 待改进项 |
|------|----------|-------------|----------|----------|
| 首页 | 用户提供原版首页截图（2026-03-21 会话） | `docs/convergence/screenshots/iteration-13/android-home-current.png` | 已能对位首页信息架构 | 顶栏色系、入口卡片细节、底部播放器、列表密度仍偏离 |
| Drawer | 用户提供原版 Drawer 截图（2026-03-21 会话） | `docs/convergence/screenshots/iteration-13/android-drawer-current.png` | Android Drawer 现已可抓当前基线 | 菜单层级和功能深度差距很大 |
| 播放历史（空态） | 用户提供原版历史页截图（2026-03-21 会话） | `docs/convergence/screenshots/iteration-13/android-history-current.png` | 已确认 history 页截图链路恢复可用 | 仍需有数据状态和回放链路验收 |

## 尚未完成的真实端上验收
- `Settings -> default subscription import -> Search -> tap result -> Player`
- `Home -> Recommend Sheets -> Plugin Sheet Detail -> tap song -> Music Detail`
- `Home -> Top List -> Top List Detail -> tap song -> Music Detail`
- `Music Detail -> Album Detail / Artist Detail`

## 回归检查
| 已有功能 | 状态 | 备注 |
|----------|------|------|
| `searchMusicList` 路由序列化 | ✅ | `RoutesTest` 通过 |
| `searchMusicList` 过滤/播放逻辑 | ✅ | `SearchMusicListViewModelTest` / `SearchMusicListSourceLoaderTest` 通过 |
| `MainActivity` 启动不因新路由崩溃 | ✅ | `MainActivityStartupTest` 通过 |
| plugin 默认订阅搜索/解析链路 | ✅ | connected instrumentation 通过 |
| plugin-backed detail flows | ⚠️ | 代码存在但端上真实点击/截图未补齐 |

## 遗留问题（进入下轮迭代 backlog）
- `musicListEditor-lite` 与共享 collection tooling 尚未开始实现。
- `searchMusicList` 仍需扩展 local music / generic sheet source。
- `fileSelector` / SAF 基础设施尚缺。
- downloader core 仍未建设，`downloading` 页面不应先行。
- 原版截图显示的全局视觉骨架差异仍然很大，尤其是 Drawer、播放器、列表密度和橙色顶栏体系。
