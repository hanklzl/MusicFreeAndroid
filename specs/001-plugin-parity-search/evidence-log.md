# Evidence Log (Compose vs Original RN)

Feature: `001-plugin-parity-search`  
Default subscription baseline: `https://13413.kstore.vip/yuanli/yuanli.json`

## Entry Template

| Evidence ID | Capability ID | Type | Compose Steps | Original RN Steps | Result | Artifact Path | Notes |
|---|---|---|---|---|---|---|---|
| E-XXX | `plugin.add.url` | automated/manual | concise steps | concise steps | aligned/partial/not-aligned | `docs/...` | optional |

## Evidence Entries

| Evidence ID | Capability ID | Type | Compose Steps | Original RN Steps | Result | Artifact Path | Notes |
|---|---|---|---|---|---|---|---|
| E-101 | plugin.add.url | automated | `connectedDebugAndroidTest` 执行 `yuanliWy_searchAndMediaSource_returnsPlayableUrl` | 原版插件设置页 URL 安装 + 搜索 + 播放链路 | aligned | `plugin/build/reports/androidTests/connected/debug/index.html` | `wy.js` 安装后可搜索且可取播放源 |
| E-102 | plugin.add.local | automated | `SettingsViewModelTest` 文件安装 + `localRuntimeShimPlugin_search_executesWithoutNotFunctionErrors` | 原版本地插件导入链路 | aligned | `feature/settings/build/reports/tests/testDebugUnitTest/index.html` | 覆盖 local 安装成功与可搜索可取源 |
| E-103 | plugin.add.subscription | automated | `defaultSubscription_installAndWyPlaybackChain_succeeds`（订阅 `yuanli.json`） | 原版订阅导入 + WY 搜索播放链路 | aligned | `plugin/build/reports/androidTests/connected/debug/index.html` | 默认订阅导入后 WY 可搜索并可取可播源 |
| E-104 | plugin.update.single | automated | `updatePlugin_thenSearchStillWorks_returnsPlayableResults` + `updatePlugin_afterSearchRegression_keepsSearchablePluginUsable` | 原版单插件更新后继续搜索 | aligned | `plugin/build/reports/androidTests/connected/debug/index.html` | 更新后插件可继续搜索并可取源 |
| E-105 | plugin.update.all | automated | `updateAllPlugins_withoutSources_returnsFailureSummary` + `PluginManagerUpdateFlowTest` | 原版批量更新汇总反馈 | aligned | `plugin/build/reports/androidTests/connected/debug/index.html` | 批量更新结果包含成功/失败统计 |
| E-106 | plugin.identity.unique | automated | `updatePlugin_thenSearchStillWorks_returnsPlayableResults` 中校验同平台实例计数=1 | 原版更新不重复插入同平台插件 | aligned | `plugin/build/reports/androidTests/connected/debug/index.html` | 更新后无重复插件条目 |
| E-201 | plugin.search.selectable-set | automated | `SearchViewModelTest.filters searchable plugins...` | 原版仅展示可搜索插件集合 | aligned | `feature/search/build/reports/tests/testDebugUnitTest/index.html` | 仅 `supportedSearchType` 含 `music` 的插件可选 |
| E-202 | plugin.search.query | automated | `SearchViewModelTest` 查询状态测试 + 连接测试查询 | 原版查询成功/空/错误状态 | aligned | `feature/search/build/reports/tests/testDebugUnitTest/index.html` | 查询状态与插件选择联动正常 |
| E-203 | plugin.search.pagination | automated | `SearchViewModelTest.load more failure keeps existing search results` | 原版分页追加与失败不破坏已加载结果 | aligned | `feature/search/build/reports/tests/testDebugUnitTest/index.html` | 分页失败时保留已有结果 |
| E-204 | plugin.search.after-update | integration | `updatePlugin_afterSearchRegression_keepsSearchablePluginUsable` | 原版更新后再搜索 | aligned | `plugin/build/reports/androidTests/connected/debug/index.html` | 更新后同平台插件仍可搜索 |
| E-301 | release.parity.gate | automated | `validate-matrix.sh` + `check-release-gate.sh` | 原版发布前验证流程 | aligned | `docs/convergence/iteration-14/verification.md` | 发布门禁脚本可复现执行 |

## Readiness Rule

- P1 readiness can only be marked as `PASS` when all P1 capability rows in `parity-matrix.md` contain at least one automated and one manual evidence item with non-`TBD` result.
