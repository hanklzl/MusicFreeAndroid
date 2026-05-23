# 单元测试耗时审计报告

> 文档状态：审计快照（一次性）
> 数据采集日期：2026-05-19
> 阈值：`testcase.time > 2.0s`
> Spec：[docs/superpowers/specs/2026-05-19-unit-test-perf-audit-design.md](../superpowers/specs/2026-05-19-unit-test-perf-audit-design.md)
> Plan：[docs/superpowers/plans/2026-05-19-unit-test-perf-audit.md](../superpowers/plans/2026-05-19-unit-test-perf-audit.md)

## 1. 概览

- JVM 单测总数：**1248**
- JVM 单测总耗时：**256.82s**
- `> 2s` 候选数：**33**
- 候选总耗时：**193.81s**（占总耗时 **75.5%**）
- 失败 / error 的 testcase 数（已附入慢测详表）：**4**

> 33 个慢 case 拿走了 3/4 的总耗时，是值得专项优化的头部。

## 2. 模块耗时分布

| module | case 数 | 总耗时(s) | > 2s 数 | 模块占比 |
|---|---:|---:|---:|---:|
| :feature:home | 153 | 39.72 | 4 | 15.5% |
| :feature:settings | 77 | 30.66 | 5 | 11.9% |
| :feature:listen-stats | 20 | 29.95 | 2 | 11.7% |
| :updater | 41 | 27.08 | 4 | 10.5% |
| :plugin | 188 | 24.73 | 5 | 9.6% |
| :feature:player-ui | 142 | 23.73 | 2 | 9.2% |
| :data | 199 | 23.63 | 3 | 9.2% |
| :downloader | 44 | 21.79 | 2 | 8.5% |
| :player | 136 | 16.97 | 1 | 6.6% |
| :app | 79 | 6.78 | 2 | 2.6% |
| :core | 112 | 6.74 | 2 | 2.6% |
| :feature:search | 28 | 4.09 | 1 | 1.6% |
| :logging | 29 | 0.96 | 0 | 0.4% |

Top 5 模块解读：

- **`:feature:home`（39.72s / 153 cases）** —— 头部体量大但平均成本约 0.26s，整体由量摊薄，4 个慢 case 占模块 ≈ 1/3 时间，是典型"长尾内有几个 outlier"模式。
- **`:feature:settings`（30.66s / 77 cases）** —— 平均 0.40s/case，5 个 > 2s case，应重点检查是否大量走 DataStore / Robolectric 真实环境。
- **`:feature:listen-stats`（29.95s / 20 cases）** —— 异常：20 个 case 拉到 30s，平均 1.5s/case，且头部一个 case 21.98s（占模块 73%）。模块整体偏重，且头号 case 是绝对优化目标。
- **`:updater`（27.08s / 41 cases）** —— 41 case 平均 0.66s，4 个 > 2s 集中在 `OkHttpApkDownloaderTest`，说明 OkHttp / 文件 IO 类 case 集中重。
- **`:plugin`（24.73s / 188 cases）** —— 大量 case 但平均 0.13s，已经控制得很好；5 个慢 case 几乎都在 axios shim / cheerio require shim 类。

## 3. 慢测详表

按 testcase 耗时降序。**共 37 行 = 33 个 `> 2s` 候选 + 4 个 setup-error case**（后者 `time = 0.00s`，因 Robolectric `@Config` 与 `targetSdkVersion=37` 冲突，详见第 4.0 节）。`必要性分类`：`守门 / 回归 / 高价重复 / 低价值`。耗时落在 `2.0–2.5s` 的行旁加 `⚠噪声敏感` 标记，处置建议偏保守。

| # | module | class.method | time(s) | 成因初诊 | 必要性分类 | 处置建议 | evidence |
|---|---|---|---:|---|---|---|---|
| 1 | :feature:listen-stats | ListenStatsScreenTest.coverage_below_30_hides_language_and_genre_cards | 21.98 | Robolectric `@Config(sdk=29)` + `createComposeRule()` 启动整套 Compose harness（L14-23）；与同 class 另一 case 共享类加载，但该 case 多次 `composeRule.setContent` 渲染完整 `StatelessListenStatsScaffold`（L68-88）| 守门 | 保留 + 评估收敛 `empty_snapshot_renders_*` 与 `coverage_below_30_*` 为单次 setContent 多断言 | Compose UI 回归守门，无对应 incident（feature/listen-stats Screen 渲染 fidelity） |
| 2 | :downloader | DownloadEngineCancelRetryTest.retryFailedResetsToPendingAndRunsAgain | 14.06 | Robolectric + `Room.inMemoryDatabaseBuilder` + `DownloadEngine.start()`（L44-64）；整个 class 三个 case 重复 build 同一 DB | 守门 | 保留 + 共享 `@ClassRule` 单例 Room/Engine 或拆 DB build 到 `companion object`；retry 关键路径不可删 | 端到端 download cancel/retry 关键路径回归守门；harness `rules.md` 未直接登记，但 download UX 高价值 |
| 3 | :feature:home | HomeDrawerContentInsetsTest.drawer title starts below the status bar inset | 13.91 | Robolectric + Compose + `getUnclippedBoundsInRoot()` 实测 `FidelityAnchors.Home.DrawerTitle` 坐标（L46-49）| 守门 | 保留 + 与同 class `drawer_does_not_render_removed_*` 共享单次 setContent | UI Harness `rule-overlay-respects-statusbar` / INC-2026-0015 (Drawer 浮层 status bar 守门) |
| 4 | :feature:home | LocalMusicScannerTest.scan recursively reads configured tree and returns audio files only | 12.82 | Robolectric `@Config(sdk=34)` + 大量 `DocumentsContract.*` URI 构造与 `MatrixCursor` mock（L29-83）| 守门 | 保留 + 评估是否可改 pure JVM（`DocumentsContract` 静态调用在 Robolectric shadow 下成本高）| 本地音乐扫描契约守门；扫描结果直接影响 home 列表 |
| 5 | :updater | OkHttpApkDownloaderTest.successful download writes abi-scoped file and reports progress | 12.43 | Robolectric + `MockWebServer.start()` + 真实 OkHttp socket + 4KB body 写入磁盘（L36-46, L77-92）；4 个 case 各自 start/shutdown server | 守门 | 保留 + 改为 `@ClassRule` 共享 MockWebServer 或 `@Before` 复用单实例 | 升级器 APK 下载 abi-scoped 文件契约（升级流程关键路径）|
| 6 | :feature:player-ui | PlayerControlsTest.mode button shows single mode semantics | 11.89 | Robolectric `@Config(sdk=29)` + Compose `setContent` 渲染 `PlayerControls`；6 个 case 中 4 个调用 setControls 触发完整 Compose tree | 守门 | 保留 + 合并 3 个 `mode_button_shows_*_semantics` 为参数化或单测多断言 | RN parity：repeat mode icon 映射守门（`PlaybackMode.Single` → ic_repeat_song）|
| 7 | :player | SimpleCacheHolderTest.lazy_creates_cache_on_first_access | 10.22 | Robolectric + ExoPlayer `SimpleCache` 真实创建磁盘缓存目录（L17, ApplicationProvider context）| 守门 | 保留 + 评估 `@ClassRule` 共享 holder | 7ad4a9cc `feat(network+traffic+cache): 流量统计与音频本地缓存` 引入；player cache 单例契约 |
| 8 | :feature:settings | PermissionsHelpersTest.hasNotificationPermission returns true below API 33 | 8.73 | Robolectric `@Config(sdk=35)` + `RuntimeEnvironment.getApplication()`（L54-57）仅为获取 Application context 验证 short-circuit；7 个 case 共担 Robolectric 启动开销但此 case 是首例 | 守门 | 保留 + 把不需要 Application context 的 4 个 pure helper 测试拆到非 Robolectric class，减少 Robolectric 初始化摊销 | 通知权限 short-circuit 守门（API 33 以下 helper 返回 true）|
| 9 | :feature:settings | BasicSettingsContentTest.basic settings has no pending marker | 8.40 | Robolectric + Compose `setContent` 渲染 `BasicSettingsContent` 全量树（L379-409，30+ callback 参数）+ 3 次 `scrollToTag` | 守门 | 保留 + 与 `runtime_backed_value_rows_*` 共享 setContent 状态机 | RN parity：basic settings 不应有「待接入」占位的 fidelity 守门 |
| 10 | :plugin | AxiosShimAuthUrlTest.url with user pass becomes Authorization Basic | 6.93 | Robolectric `@Config(sdk=29)` + `MockWebServer.start()` + `AxiosShim.performGet`（L48-67）；7 个 case，2 个真发请求 | 守门 | 保留 + 评估把 6 个纯 `normalizeRequest` 单测从 Robolectric 拆出（仅 2 个 MockWebServer case 需 Robolectric）| plugin AxiosShim RN parity（`user:pass@host` → Basic Auth），与 INC-2026-0019 同一 network harness 域 |
| 11 | :feature:listen-stats | ListenDetailViewModelTest.byArtist_summary_includesFilterValue | 5.27 | 纯 JVM + `MainDispatcherRule` + Mockito stub repo（L52-62）；非 Robolectric，时间应该来自模块首例 class load / mockito init | 守门 | 保留；时间偏高源于模块 fork 启动，本身无可优化项 | listen-stats detail 模式 summary 渲染回归守门 |
| 12 | :data | BackupRepositoryTest.export writes archive to uri | 5.19 | Robolectric + `TemporaryFolder` + 真实 ZIP 写入与 readback（L69-84）；setUp 重建 layout/repository（L37-61）| 守门 | 保留 + 评估 fixture 体积是否可缩（manifest + 1 DB 行已最小）| backup 导出契约（影响数据迁移/恢复关键路径）|
| 13 | :updater | UpdaterClientContractTest.updater_client_event_listener_factory_matches_base | 5.00 | Robolectric + Hilt `@HiltAndroidTest` 注入 `@UpdaterHttp` + `@BaseOkHttp` 客户端（L26-33）；Hilt graph 启动开销 | 守门 | 保留；harness contract 不可删，Hilt 启动是固定成本 | 路径命中 `**/harness/contracts/**`；guards INC-2026-0019 (rule-okhttp-derive-from-base) |
| 14 | :updater | UpdateCheckerTest.available when remote newer and not skipped and abi matches | 4.76 | 纯 JVM + MockK + `StandardTestDispatcher` + `advanceUntilIdle`（L57-66）；9 个 case 共担 mockk class load 初始化 | 守门 | 保留；mockk 初始化为模块首测分摊成本，无单点优化 | 升级判定（newer + abi 匹配 + skip 规则）核心契约 |
| 15 | :data | MediaCacheRepositoryTest.put trims oldest entries when byte limit is exceeded | 4.31 | 纯 JVM + MockK；6 个 case 模块首例 | 守门 | 保留；MockK first-load 成本，无单点优化 | media cache LRU 字节限额行为契约（防止缓存膨胀）|
| 16 | :core | NetworkTrafficEventListenerTest.zero_bytes_call_does_not_offer | 3.58 | 纯 JVM + MockK；5 个 case 模块首例 | 守门 | 保留；MockK 模块首例成本 | NetworkTrafficEventListener 字节统计契约（INC-2026-0019/20/21 系列 network harness 基础组件）|
| 17 | :plugin | PluginManagerCacheCleanupTest.uninstall does not propagate when cache cleanup throws | 3.28 | Robolectric `@Config(sdk=29)` + `TemporaryFolder` + Mockito + 反射注入 `_plugins` StateFlow（L137-141）；3 个 case 共担 | 守门 | 保留 + 评估 `_plugins` 反射注入是否可改 internal setter | plugin uninstall 缓存清理容错契约（避免 DB 异常阻塞卸载流程）|
| 18 | :downloader | DownloaderClientContractTest.downloader_client_event_listener_factory_matches_base | 3.28 | Robolectric + Hilt 注入下载 client + base client（L25-34）；Hilt graph 启动 | 守门 | 保留；harness contract 不可删 | 路径命中 `**/harness/contracts/**`；guards INC-2026-0019 (rule-okhttp-derive-from-base) |
| 19 | :feature:search | SearchViewModelRuntimeStoreTest.viewModelUsesStoreStateAfterRecreation | 3.11 | 纯 JVM + Mockito + `MainDispatcherRule` + `InMemorySnapshotStore`（L115-145）；feature 模块首例 case | 守门 | 保留；feature/search 单 case 模块，启动成本固定 | runtime/rules.md `RuntimeStore` 重建恢复契约（高价值运行态恢复守门） |
| 21 | :feature:home | HomeScreenMockContentTest.home screen content renders mock rows instead of empty state and wires mock row click callback | 3.09 | Robolectric + Compose `setContent` 渲染整个 `HomeScreenContent`（L64-93）+ `performClick` | 守门 | 保留；single case class，无收敛空间 | Home fidelity 守门（mock 行渲染与点击回调，对应首页 fidelity 专项设计）|
| 22 | :feature:settings | SettingsScreenTest.backup restore confirmation can register pending restore | 2.98 | Robolectric + Compose + 真实 `PreferenceDataStoreFactory.create` + Fake repo（L97-130）；多 case 共享 DataStore 创建 | 守门 | 保留 + 评估抽公共 setUp DataStore 实例（已用 `dataStoreScopes` 管理）| settings backup/restore 确认对话框 UX 回归守门 |
| 23 | :feature:player-ui | PlayerViewModelQueueTest.queueUiModel reflects queueState items and currentIndex | 2.92 | 纯 JVM + Mockito + `StandardTestDispatcher` + 大量 mock dependency wiring（L66-84）；6 个 case 共担 setUp | 守门 | 保留；mock 初始化为模块首测分摊 | PlayerViewModel queue UI 模型守门（影响播放队列展示）|
| 24 | :feature:settings | PluginListViewModelTest.importMusicSheet rejects blank url without calling plugin | 2.87 | 纯 JVM + Mockito + `MainDispatcherRule`；class 含 30+ test，模块首例摊销 | 守门 | 保留；plugin 导入空 URL 拒绝是核心 input validation | plugin importMusicSheet 输入校验契约 |
| 25 | :plugin | MediaItemBridgeContractTest.projector emits localPath when DownloadedTrack exists ⚠噪声敏感 | 2.41 | 纯 JVM + Mockito（L46-79）；class doc 明确标注 "dev-harness contract"（L17-26）| 守门 | 保留；阈值附近不轻改 | class doc：JsBridge ↔ MusicItem dev-harness contract（Phase F regression guard）|
| 26 | :feature:settings | BasicSettingsContentTest.runtime backed value rows open dialogs and invoke callbacks ⚠噪声敏感 | 2.34 | Robolectric + Compose `setContent`（L82-105）+ 多次 `scrollToTag` + `performClick`（L107-225）展开数十个对话框 | 守门 | 保留 + 评估按 section 拆为多 case 复用 setContent，减少单 case 内点击数 | RN parity：basic settings 全量 row 回调矩阵守门 |
| 27 | :core | NetworkTypeDetectorTest.reports_CELLULAR_when_active_network_has_cellular_transport ⚠噪声敏感 | 2.27 | 纯 JVM + MockK（L49-60）；4 个 case 摊销 MockK class load | 守门 | 保留；阈值附近不轻改 | NetworkType 检测契约（INC-2026-0019/20/21 系列 network harness 基础）|
| 28 | :app | TestRunTestIdiomContractTest.no_runBlocking_first_predicate_in_viewmodel_tests ⚠噪声敏感 | 2.22 | 纯 JVM；遍历 repo `**/*ViewModelTest.kt` 文件并正则扫描（L21-38）；遍历成本与文件数线性相关 | 守门 | 保留；阈值附近不轻改 | 路径命中 `**/harness/contracts/**`；guards INC-2026-0001 (rule-runtest-mandatory) |
| 29 | :data | DownloadedTrackDaoTest.get returns full row for known id and platform ⚠噪声敏感 | 2.19 | Robolectric + `Room.inMemoryDatabaseBuilder`（L25-29）；4 个 case 共享 in-memory DB build | 守门 | 保留；阈值附近不轻改 | DownloadedTrack DAO 读取契约（影响下载状态展示）|
| 30 | :updater | OkHttpUpdateClientTest.returns null when all mirrors fail ⚠噪声敏感 | 2.09 | 纯 JVM + 2 个 `MockWebServer.start()` + `SocketPolicy.NO_RESPONSE` 触发 1s connectTimeout（L83-92）；timeout-driven | 守门 | 保留 + 评估缩短 connect/read timeout（当前 1s/2s），timeout 成本不可零 | mirror fallback 全失败行为契约（升级请求容错）|
| 31 | :plugin | AxiosShimTimeoutTest.non-positive timeout falls back to default ⚠噪声敏感 | 2.06 | Robolectric + MockWebServer `setBodyDelay(5s)` + 实测 `elapsed in 1800..3500ms`（L104-120）；本测必然耗 ~2s | 守门 | 保留；耗时来自被测的 2000ms 默认 timeout 本身，无法压缩 | RN parity：axios 默认 2000ms timeout 契约 |
| 32 | :plugin | AxiosShimTimeoutTest.default timeout is 2000ms ⚠噪声敏感 | 2.06 | 与上一行同源：MockWebServer 5s 延迟 + 实测 ~2000ms 超时窗口（L50-65）| 守门 | 保留；耗时来自被测 timeout 本身 | RN parity：axios 默认 2000ms timeout 契约（与上行同 class，相互补充）|
| 33 | :feature:home | HomeViewModelTest.download enqueues selected item with requested quality ⚠噪声敏感 | 2.06 | 纯 JVM + Mockito + `StandardTestDispatcher`（L115-124）；6 个 case 摊销 Mockito setUp | 守门 | 保留；阈值附近不轻改 | home → download enqueue 行为契约（影响下载入口）|
| 34 | :feature:listen-stats | ListenDetailScreenTest.initializationError | 0.01 | Robolectric `@Config` 默认 maxSdk=36 与 `targetSdkVersion=37` 冲突，**测试在 setup 阶段抛 IllegalArgumentException**，未真正执行（"failed to configure ... firstSeen_mode_shows_firstSeen_badge_on_row"）| 守门 | 保留 + 修配置：在 class 加 `@Config(sdk=[34])` 或类似显式 SDK，避免依赖 Robolectric maxSdk 默认 | Compose UI 回归守门（SongDetailRow firstSeen badge）但当前实际未运行，必须先修复配置才有守门价值 |
| 35 | :feature:settings | SettingsViewModelTest.initializationError | 0.00 | 同上：`backup export failure sets error state and logs` 在 setup 阶段失败（targetSdk=37 > maxSdk=36）| 守门 | 保留 + 修配置：补 `@Config(sdk=[34])` 显式 SDK | backup 导出失败 → error state + 日志 行为守门（当前未运行）|
| 36 | :feature:settings | SetCustomThemeViewModelTest.initializationError | 0.00 | 同上：`onImagePicked aborts silently when copy fails` 在 setup 阶段失败 | 守门 | 保留 + 修配置：补 `@Config(sdk=[34])` 显式 SDK | 自定义主题 image picker 容错守门（当前未运行）|
| 37 | :feature:settings | FileSelectorLiteViewModelTest.initializationError | 0.00 | 同上：`onDirectorySelected persists selected tree uri` 在 setup 阶段失败 | 守门 | 保留 + 修配置：补 `@Config(sdk=[34])` 显式 SDK | 文件选择器持久化 tree URI 守门（当前未运行）|

## 4. 快速优化清单

按预计可省耗时降序。每条对应详表里的某个慢测；预计可省耗时是估算值，落地前应实测。

### 4.0 测试基线 bug（优先级最高）

详表 #34–#37 共 4 个 `initializationError` case 当前提供 **零守门价值**：Robolectric `@Config` 默认 `maxSdk=36` 与项目 `targetSdkVersion=37` 冲突，测试在 setup 阶段抛 `IllegalArgumentException`，被测代码根本未运行。

- 涉及文件：
  - `feature/listen-stats/src/test/java/com/hank/musicfree/feature/listenstats/screen/ListenDetailScreenTest.kt`
  - `feature/settings/src/test/java/com/hank/musicfree/feature/settings/screen/SettingsViewModelTest.kt`
  - `feature/settings/src/test/java/com/hank/musicfree/feature/settings/customtheme/SetCustomThemeViewModelTest.kt`
  - `feature/settings/src/test/java/com/hank/musicfree/feature/settings/fileselectorlite/FileSelectorLiteViewModelTest.kt`
- 提议修复：在 class 加 `@Config(sdk = [34])`（或其他显式合法 SDK），不再依赖 Robolectric `maxSdk` 默认值。
- 影响：一次性配置修复有可能同时恢复 4 个守门 case（Compose UI fidelity / backup error state / 自定义主题 picker / 文件选择器 tree URI 持久化）的实际守门价值。**这是本轮审计最高价值的发现**。
- 提示：恢复后这些 case 实际耗时未知，可能从当前的 0.00s 上升到 2–10s 量级；属于"先有正确性再谈耗时"的优先级。

### 4.1 机械优化清单（按预计可省耗时降序）

> 估算值均为保守区间，落地前应实测。"约 N s" 指整个 class 可省总和，不是单 case。

1. `:feature:listen-stats` `ListenStatsScreenTest.coverage_below_30_hides_language_and_genre_cards` —— 当前 21.98s，与同 class `empty_snapshot_renders_*` 合并为单次 `setContent` 多断言（按场景共享 Compose tree），约 5–8s。详表 #1。
2. `:updater` `OkHttpApkDownloaderTest.successful download writes abi-scoped file and reports progress` —— 当前 12.43s，class 内 4 个 case 各自 `MockWebServer.start()`/`shutdown()`，改为 `@ClassRule` 共享单例（或 `@Before`/`@After` 复用），约 6–9s。详表 #5。
3. `:downloader` `DownloadEngineCancelRetryTest.retryFailedResetsToPendingAndRunsAgain` —— 当前 14.06s，class 内 3 个 case 重复 build 同一 in-memory Room DB，改为 `@ClassRule` 单例或拆到 `companion object`，约 4–6s。详表 #2。
4. `:feature:player-ui` `PlayerControlsTest.mode button shows single mode semantics` —— 当前 11.89s，3 个 `mode_button_shows_*_semantics` case 合并为参数化测试或单测多断言（共用 setControls 后的 Compose tree），约 4–6s。详表 #6。
5. `:plugin` `AxiosShimAuthUrlTest.url with user pass becomes Authorization Basic` —— 当前 6.93s，6 个纯 `normalizeRequest` case 从 Robolectric class 拆出到独立非 Robolectric class（保留 2 个真发请求的 case 在 Robolectric），约 3–5s。详表 #10。
6. `:feature:settings` `PermissionsHelpersTest.hasNotificationPermission returns true below API 33` —— 当前 8.73s，4 个不需要 Application context 的 pure helper case 拆到非 Robolectric class，减少 Robolectric 启动摊销，约 3–5s。详表 #8。
7. `:feature:home` `HomeDrawerContentInsetsTest.drawer title starts below the status bar inset` —— 当前 13.91s，与同 class `drawer_does_not_render_removed_*` case 共享单次 `setContent`，约 3–5s。详表 #3。
8. `:updater` `OkHttpUpdateClientTest.returns null when all mirrors fail` —— 当前 2.09s ⚠噪声敏感，timeout-driven（1s/2s connect/read），在不破坏行为契约前提下可评估缩短 connect/read timeout，约 0.5s。详表 #30。

> 上述以外的慢 case，详表 处置建议 已明确为"MockK / Mockito / Hilt 首例摊销" "阈值附近不轻改" "被测 timeout 自身决定耗时" 等无单点优化空间的场景，不重复列入本节。

## 5. 删除 / 合并候选清单

Task 5 分类采用"逐级降级"判定流程，任何一档（harness 路径 / incident / RN parity / 关键业务路径）命中即停在 **守门**；37 / 37 case 因此全部落入 守门。本节聚焦"人工 review 可能合理重分类"的边缘 case，并明确"本轮无低价值删除候选"。

### 5.1 低价值型（建议删除）

本轮无该类候选。详表 37 行全部命中 harness 路径 / incident / RN parity / 关键业务路径之一，没有适合直接删除的 case。

### 5.2 高价重复型（建议合并 / 收敛，待人工复核）

以下为 Task 5 实现过程中自我标注、级联判定属于边缘的 3 项；本节列出供人工复核，是否真正下调到 高价重复 需 reviewer 判断。

1. `:feature:settings` `PermissionsHelpersTest.hasNotificationPermission returns true below API 33`（详表 #8）—— 与同 class `requiredNotificationPermission returns null below API 33` 断言面相近：后者通过 pure JVM 验证 null short-circuit，前者用 Robolectric 验证 true short-circuit。可合并为参数化或拆出 pure JVM 测试覆盖两个 short-circuit 分支，保留单个 Robolectric 测验证 Application context 解析路径。预计可省 ~3s。
2. `:plugin` `AxiosShimTimeoutTest.default_timeout_is_2000ms` + `non-positive_timeout_falls_back_to_default`（详表 #31, #32）—— 两个测试断言同一 timeout 契约的相邻分支（默认值 vs 非正值回退到默认）。建议参数化为单个测试，输入 `[0, -1, null]` 都映射到 2000ms。耗时无法压缩（被测的 2000ms timeout 本身决定下界），但可减少一次模块启动 / setup 开销。
3. 同 class `setContent` 复用候选（详表 #1, #6, #26 已在 Section 4.1 列出）—— 严格说不属于跨 class "合并"，而是同一 case 内部的渲染复用，已在快速优化清单覆盖；本节不重复列出。

### 5.3 分类分布说明

- 37 / 37 全部落入 **守门**。
- 原因：(1) 仓库 dev-harness 体系覆盖广，contract test 路径（`**/harness/contracts/**`）、RN parity 守门、UI fidelity 守门均直接命中；(2) 业务 critical path 比例高（download / backup / 升级 / 播放队列 / 插件搜索 等核心链路均有专项测试）；(3) 判定流程严格按"逐级降级"——任何一档命中即停在 守门，对边缘 case 偏保守。
- 含义：本次审计 **没有发现明显应删除的测试**。优化空间集中在 Robolectric / MockWebServer / Compose `setContent` 的复用与拆分（见 Section 4），而非删除。



## 6. 数据局限声明

- **单次运行噪声**：本报告基于一次 `./gradlew testDebugUnitTest --rerun-tasks --continue` 的输出。> 2s 阈值附近（`2.0–2.5s`）的 case 可能因 GC / JIT 抖动忽进忽出，详表中已用 `⚠噪声敏感` 标记，处置建议偏保守。
- **testcase time 不含 fork 启动**：Gradle worker 启动、class loading、Robolectric 首例 ResourceLoader 等成本不计入 `<testcase time="...">`。因此第 2 节模块级总耗时是补充信号，可能比详表总和大。
- **必要性判定为 AI + 启发式**：会有误判；本报告中的「删除 / 合并候选清单」**不会自动执行**，需人复核后通过后续 plan 落地。
- **本次完全不动测试代码 / build 配置**：报告落地不影响 CI / release / 已有 harness 检查。

## 7. 实测复核（优化落地与噪声分析）

> 本节由第二轮全量跑（commit `aac19c2b` 之后）实测数据回填。基线为第 1–6 节的初次审计快照。

### 7.1 已落地的优化

#### O1：修 Robolectric `@Config` `maxSdk=36` 与 `targetSdkVersion=37` 冲突（对应第 4.0 节）

4 个测试类补 `@Config(sdk = [29])`：

- `feature/listen-stats/src/test/.../ListenDetailScreenTest.kt`
- `feature/settings/src/test/.../SettingsViewModelTest.kt`
- `feature/settings/src/test/.../setcustomtheme/SetCustomThemeViewModelTest.kt`
- `feature/settings/src/test/.../fileselector/FileSelectorLiteViewModelTest.kt`

**实测结果**：共 32 个原本 setUp 阶段 throw 的 testcase 现实际运行并全部通过：

| 文件 | 实际 testcase 数 | 实际耗时 |
|---|---:|---:|
| `ListenDetailScreenTest` | 1 | 5.988s |
| `SettingsViewModelTest` | 21 | 7.564s |
| `SetCustomThemeViewModelTest` | 7 | 0.453s |
| `FileSelectorLiteViewModelTest` | 3 | 0.083s |

新增 1 个慢 case：`ListenDetailScreenTest.firstSeen_mode_shows_firstSeen_badge_on_row`（5.72s）—— Compose first-instance 成本，结构性，无收敛空间。**这是本轮唯一明确的正向改动**：把 32 个名义守门转为真守门。

#### O5：缩短 `OkHttpUpdateClientTest` 网络 timeout（对应第 4.1 节 #8）

`updater/src/test/.../api/OkHttpUpdateClientTest.kt`：`connectTimeout 1s → 250ms`，`readTimeout 2s → 500ms`。

**实测结果**（targeted run）：

- 慢 case `returns null when all mirrors fail`：2.09s → 0.526s（**-1.56s**）
- class 总耗时：~3.5s → 1.10s（-2.4s）
- 4 个 case 全部通过

慢 case 在全量跑里跌出 `> 2s` 阈值。

### 7.2 跳过的优化项（实测无空间）

第 4.1 节的 **#1（ListenStatsScreenTest）/ #4（PlayerControlsTest）/ #7（HomeDrawerContentInsetsTest）** 在 targeted run 下逐 case 量化后，发现都是**同一种结构性瓶颈**：

| Class | 总耗时 | 首例 case | 后续 case |
|---|---:|---:|---|
| `ListenStatsScreenTest` | 7.06s | `coverage_below_30_*` 6.591s | `empty_snapshot_*` 0.141s |
| `PlayerControlsTest` | 8.38s | `mode button shows single` 7.231s | 其余 5 case 共 0.55s |
| `HomeDrawerContentInsetsTest` | 7.51s | `drawer title starts` 6.989s | `drawer does not render` 0.167s |

第一个跑的 case 吃完整套 Compose + Robolectric first-instance 成本（≈ 6–7s），后续 case 几乎免费。**合并 / 参数化 / 共享 `setContent` 都救不了**——因为成本不在重复渲染上，在 first-instance 启动；不管哪个 case 跑在第一位都要付一次。

审计第 4.1 节里 `5–8s 可省 / 4–6s 可省 / 3–5s 可省` 的估算（来自 Task 5 subagent）**是错的**——它把"首例 7s + 后续 0.1s"误读成"每 case 7s × N"。实际有 N case 的 class 也只付 ≈ 7s + (N-1)×0.1s，几乎已经达到结构下界。

### 7.3 噪声评估（重要）

第二轮全量跑：

- 总耗时 256.82s → **162.95s（-93.87s，-36.6%）**
- 慢 case（`> 2s`）数 33 → **21**

**但这不是 O1+O5 实际效果**。O1 预期增加 ~10s（新加 32 个真测试），O5 实际省 ~2.4s——合计 +8s。剩下的 **~100s 大头是环境噪声**：

- 第一轮 baseline 用 gradle auto-discovered JVM toolchain（Red Hat JRE 21.0.10），daemon 冷启
- 第二轮显式 `-Dorg.gradle.java.home=/usr/local/opt/openjdk@21`（Homebrew openjdk 21.0.11），`--no-daemon`
- Robolectric / Compose JIT 行为可能差异显著

**具体可疑 case**：

| Case | before | after | 解读 |
|---|---:|---:|---|
| `ListenStatsScreenTest.coverage_below_30_*` | 21.98s | 0.59s | first-instance 成本转嫁到 `ListenDetailScreenTest`（O1 新加，字母序更前）——非真实节省 |
| `LocalMusicScannerTest.scan recursively *` | 12.82s | 8.37s | 部分变快，可能 JIT |
| `PlayerControlsTest.mode button shows single` | 11.89s | 4.42s | 同一 first-instance 转嫁现象 |
| `HomeDrawerContentInsetsTest.drawer title *` | 13.91s | 6.88s | 同上 |

### 7.4 修正后的审计结论

1. **真实可优化空间远小于审计初稿估算的 25–45s**。结构性 Compose first-instance 成本（每个有 Robolectric+Compose 测试的模块都要付一次 ≈ 5–7s）不可压缩，除非：
   - 跨模块共享 Robolectric / Compose harness（工程量大，超出本轮）
   - 把 Compose UI 测试移到 instrumentation（不在本审计范围）
2. **实际节省 ≈ 2.4s**（仅 O5），但**真正高价值的改动是 O1**（把 32 个名义守门转为真守门）。
3. **未来类似审计若想衡量优化效果，必须固定 JDK + daemon 状态多跑 N 轮取中位数**——单次跑噪声 ≈ 50–100s 量级，单次 diff 不可信。
4. **审计第 4 节的优化估算应被本节修正**：除 4.0 节（O1）和 4.1#8（O5）外，其余 #1 / #2 / #3 / #4 / #5 / #6 / #7 的估算需要标注"已实测不可行"。
