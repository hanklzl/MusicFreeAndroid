# 迭代 10 验证报告

## 构建与测试
- `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest.music item round trip preserves plugin extension fields"` ✅
- `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest"` ✅
- `./gradlew :plugin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeIntegrationTest#localRuntimeShimPlugin_search_executesWithoutNotFunctionErrors` ✅
- `./gradlew :plugin:compileDebugAndroidTestKotlin :plugin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeIntegrationTest#yuanliWy_searchAndMediaSource_returnsPlayableUrl` ✅
- `./gradlew :feature:search:compileDebugKotlin :app:compileDebugKotlin` ✅

## 关键结论
- 已确认 `getMediaSource` 失败根因是插件扩展字段在桥接链路丢失。
- 修复后：
  - 扩展字段（如 `songmid`）可从 `search` 结果传递到 `getMediaSource`。
  - WY 真插件 `search -> getMediaSource` 集成链路通过。
  - 本地 runtime-shim 的强约束 `songmid` 场景通过，防止回归。
