# Parity Test Cases Registry

Feature: `001-plugin-parity-search`

## Case Table

| Case ID | Capability ID | Layer | Preconditions | Expected Outcome | Evidence Target |
|---|---|---|---|---|---|
| PT-ADD-URL-001 | plugin.add.url | e2e_manual | Compose + RN installed | 两端 URL 安装都成功并可见 | docs/convergence/iteration-14/plugin-parity-verification.md |
| PT-ADD-LOCAL-001 | plugin.add.local | e2e_manual | 本地插件文件可访问 | Compose 与 RN 本地安装链路可执行 | docs/convergence/iteration-14/plugin-parity-verification.md |
| PT-ADD-SUB-001 | plugin.add.subscription | e2e_manual | `yuanli.json` 可访问 | 两端订阅导入返回可读汇总 | docs/convergence/iteration-14/plugin-parity-verification.md |
| PT-UPD-SINGLE-001 | plugin.update.single | integration | 已安装插件 + 可更新源 | 单插件更新成功或给出可恢复失败 | plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt |
| PT-UPD-ALL-001 | plugin.update.all | integration | 至少 2 个已安装插件 | 批量更新返回逐插件汇总 | plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt |
| PT-SEARCH-QUERY-001 | plugin.search.query | e2e_manual | 至少 1 个可搜索插件 | 搜索返回 success/empty/error 合法状态 | docs/convergence/iteration-14/plugin-parity-verification.md |
| PT-SEARCH-PAGE-001 | plugin.search.pagination | unit | SearchViewModel 可测试 | load-more 失败不清空已有结果 | feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModelTest.kt |
| PT-SEARCH-UPD-001 | plugin.search.after-update | integration | 更新插件后可再次搜索 | 更新后搜索仍可用且选择状态一致 | plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt |

## Gate Rule

- P1 capability (`plugin.add.*`, `plugin.update.*`, `plugin.identity.unique`) 必须全部存在 `PASS` 证据后，才能在 release gate 通过。
