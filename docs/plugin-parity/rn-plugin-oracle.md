# RN 插件能力 Oracle

> 文档状态：当前规范
> 适用范围：RN `MusicFree` 插件协议变更与 Android `:plugin` 对齐验证
> 直接执行：是
> 当前入口：`../DOCS_STATUS.md`、`../dev-harness/plugin/rules.md`

本目录记录 Android 侧如何把 RN 原版插件系统作为可执行 oracle。目标不是替代真网 smoke，而是在本地单测层先暴露 RN 插件协议、runtime shim、状态机和默认 fixture 的漂移。

## 组成

- RN 侧生成器：`../../../MusicFree/scripts/plugin-oracle/generate-plugin-oracle.js`
- RN 真网 probe：`../../../MusicFree/scripts/plugin-oracle/probe-live-plugin.js`
- Android 快照：`../../plugin/src/test/resources/rn-plugin-oracle.json`
- Android 守门：`../../plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/harness/contracts/RnPluginOracleContractTest.kt`
- Android 全能力本地 probe：`../../plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginApiCapabilityParityIntegrationTest.kt`
- 默认订阅源守门：`../../app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsFixtureContractTest.kt`

## 刷新流程

从 Android 仓库根目录运行：

```bash
bash scripts/plugin-parity/refresh-rn-plugin-oracle.sh
```

如果正在使用 RN worktree：

```bash
RN_MUSICFREE_ROOT=../.worktrees/MusicFree-plugin-parity-oracle bash scripts/plugin-parity/refresh-rn-plugin-oracle.sh
```

刷新后至少运行：

```bash
./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.harness.contracts.RnPluginOracleContractTest" --no-daemon
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsFixtureContractTest" --no-daemon
./gradlew :plugin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginApiCapabilityParityIntegrationTest --no-daemon
```

真网链路仍走 plugin harness 现有门控：

```bash
./gradlew :plugin:connectedAndroidTest -Pintegration --no-daemon
```

默认通道不应依赖真网；真域名测试必须继续放在 `*NetworkIntegrationTest.kt` 并通过 `pluginNetworkTests` runner argument 门控。

## RN 真网矩阵 probe

RN worktree 安装依赖后可直接运行：

```bash
npm run probe:plugin-live -- --subscription https://13413.kstore.vip/yuanli/yuanli.json --query "in the end" --quality standard --attempts 2 --timeout-ms 12000
```

单插件聚焦排障：

```bash
npm run probe:plugin-live -- --url https://13413.kstore.vip/yuanli/wy.js --query "in the end" --quality standard --attempts 5
```

脚本会按 RN `plugin.ts` 的运行时约定注入 `axios`、`cheerio`、`crypto-js`、`dayjs`、`big-integer`、`qs`、`he`、`webdav`、`URL`、`process.env.lang=zh-CN`，再执行插件 `search()` 与 `getMediaSource()`。输出 JSON 中的 `supportedMethods`、`supportedSearchType`、`search.count`、`mediaAttempts[*].hasUrl/error` 可直接和 Android `PluginRuntimeNetworkIntegrationTest` 的失败诊断对照；即使某插件 `search()` 抛错，也会先输出已加载到的插件元信息，避免默认源能力矩阵缺项。

## Android 全能力本地 probe

`PluginApiCapabilityParityIntegrationTest` 安装一个本地 QuickJS probe 插件，动态调用 RN `IPluginDefine` 的 14 个方法，并校验代表性返回值能被 Android 解析为当前 domain model。它覆盖：

- `search`、`getMediaSource`、`getMusicInfo`、`getLyric`
- 专辑 / 歌手 / 歌单 / 榜单详情链路
- `importMusicSheet` / `importMusicItem`
- 推荐标签、推荐歌单、评论分页

该测试不访问网络，默认 instrumentation 通道可执行；真网可用性和上游接口状态仍由 `PluginRuntimeNetworkIntegrationTest` 的 `-Pintegration` 通道负责。

本轮对齐修复了一个由该 probe 暴露的能力缺口：`getTopLists`、`getRecommendSheetTags`、`getRecommendSheetsByTag` 返回空 `platform` 时，现在与其它详情/导入路径一致，回填为当前插件的 `info.platform`。

## 当前真网证据

截至 2026-05-15，默认源矩阵 probe 显示：

- `元力KW`、`元力KG`、`元力QQ`、`bilibili`：RN 侧搜索和 `getMediaSource` 均能返回真实 URL。
- `元力WY`：RN 侧搜索成功，但 `getMediaSource` 访问 `http://175.27.166.236/wy1/wy.php` 后拿到 `{ "code": 201, "msg": "error", "use_cookie": "默认账号" }` 或 `默认账号1`，插件随后读取 `data.url` 抛错。
- `mg` / `xiaomi.js`：RN 侧插件可加载为 `元力MG`，但 `searchMusic` 抛 `Cannot read properties of undefined (reading 'map')`。

Android `:plugin:connectedAndroidTest -Pintegration` 中 WY 失败应先用上述 RN probe 复核。如果 RN 也失败，结论是默认源或插件真网状态异常；如果 RN 成功而 Android 失败，再按 `axios_response` / `plugin_api_call_*` 诊断日志定位 Android runtime 差异。
