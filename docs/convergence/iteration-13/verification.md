# 迭代 13 验证报告

## 功能验证
| 功能点 | 操作路径 | 结果 | 问题 |
|--------|----------|------|------|
| `searchMusicList` 页面（playlist/history source） | playlist/history 入口接线 + 路由/单测/编译 | ✅通过 | source 仍只覆盖 `playlist/history`，local music / generic sheet 待后续扩展 |
| `searchMusicList` 启动回归修复 | 启动 `MainActivity` -> 构建 NavGraph -> 进入 RESUMED | ✅通过 | 运行时回归已修复，但 shell 启动链路曾受模拟器安装态影响 |
| `musicListEditor-lite`（playlist-first MVP） | playlist detail -> 编辑 -> 多选/暂存删除/保存/加到下一首/添加到歌单 | ✅通过 | 当前仍只覆盖 playlist source，未扩展到 local music / history |
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
- `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.musiclisteditor.*"` ✅
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
| 搜索结果 | 用户提供原版搜索页截图（2026-03-21 会话） | `docs/convergence/screenshots/iteration-13/android-search-results-current.png` | 已确认真实插件 tabs 与 `in the end` 搜索结果可达 | 仍需对结果点击后的真实播放终态做收口 |
| 播放器（搜索结果点击后） | 用户提供原版播放器截图（2026-03-21 会话） | `docs/convergence/screenshots/iteration-13/android-player-from-search-current.png` | 已能进入播放器页 | 当前仍停留在空状态：`0:00 / 0:00`，播放按钮仍显示 `播放`，不能记作成功播放 |
| 推荐歌单 | 原版推荐歌单页面参考（RN 代码 + 用户首页截图中的入口语义） | `docs/convergence/screenshots/iteration-13/android-recommend-sheets-current.png` | 已确认真实 plugin 数据列表可达 | 仍需逐条验证点击进入详情后的数据一致性 |
| 歌单详情 -> 音乐详情 | 原版 `pluginSheetDetail -> musicDetail` 代码路径 | `docs/convergence/screenshots/iteration-13/android-plugin-sheet-detail-current.png`, `docs/convergence/screenshots/iteration-13/android-music-detail-from-sheet-current.png` | 已确认可从推荐歌单进入歌单详情并点进音乐详情 | 当前音乐详情直接出现 `插件不存在：`，专辑/歌手统计为 `--`，说明 detail hydration 仍有缺口 |

## 尚未完成的真实端上验收
- `Settings -> default subscription import -> Search -> tap result -> Player`
  - 当前状态: 已打通到播放器页，但播放器仍为空状态，未形成可信的“成功播放”终态
- `Home -> Recommend Sheets -> Plugin Sheet Detail -> tap song -> Music Detail`
  - 当前状态: 已验证到 `PluginSheetDetail` 与 `MusicDetail` 页面可达，但 `MusicDetail` 当前报 `插件不存在：`，不能视为稳定通过
- `Home -> Top List -> Top List Detail -> tap song -> Music Detail`
- `Music Detail -> Album Detail / Artist Detail`
  - 当前状态: 尚未独立完成，因为从 `PluginSheetDetail` 进入的 `MusicDetail` 仍未拿到可信 detail 数据

## 回归检查
| 已有功能 | 状态 | 备注 |
|----------|------|------|
| `searchMusicList` 路由序列化 | ✅ | `RoutesTest` 通过 |
| `searchMusicList` 过滤/播放逻辑 | ✅ | `SearchMusicListViewModelTest` / `SearchMusicListSourceLoaderTest` 通过 |
| `MainActivity` 启动不因新路由崩溃 | ✅ | `MainActivityStartupTest` 通过 |
| plugin 默认订阅搜索/解析链路 | ✅ | connected instrumentation 通过 |
| 默认订阅 -> 搜索结果列表 | ✅ | 端上已看到 `in the end` 搜索结果与 plugin tabs |
| 默认订阅 -> 搜索结果点击 -> 播放器 | ⚠️ | 端上可进入播放器页，但当前仍为空状态，未验证出真实播放 |
| 推荐歌单 -> 歌单详情 | ✅ | 已看到真实推荐歌单数据与详情页歌曲列表 |
| 推荐歌单 -> 歌单详情 -> 音乐详情 | ⚠️ | 页面可达，但 `MusicDetail` 当前出现 `插件不存在：`，detail hydration 失败 |
| plugin-backed detail flows | ⚠️ | 已从“未触达”推进到“可触达但仍有 hydration / playback 缺口” |

## 遗留问题（进入下轮迭代 backlog）
- `musicListEditor-lite` 已完成 playlist-first MVP，但仍需扩展到 shared collection tooling、local music 与 history source。
- `searchMusicList` 仍需扩展 local music / generic sheet source。
- `fileSelector` / SAF 基础设施尚缺。
- downloader core 仍未建设，`downloading` 页面不应先行。
- 原版截图显示的全局视觉骨架差异仍然很大，尤其是 Drawer、播放器、列表密度和橙色顶栏体系。
