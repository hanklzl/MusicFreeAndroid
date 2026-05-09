---
name: plugin-system
description: >
  Use this skill for any change in :plugin (QuickJS engine, JsBridge,
  PluginApi 14 capabilities, PluginManager install/update orchestration,
  require shim, MockWebServer integration tests, network gate). Trigger
  phrases: "插件", "QuickJS", "JsBridge", "PluginApi", "require shim",
  "installFromUrl", "updatePlugin", "-Pintegration", "MockWebServer",
  "PluginManager".
---

# Plugin System Skill

Cross-tool guidance for the plugin engine, manager, and integration tests.

## 必读 gate

调起本 skill 前，必须 Read：

- [`docs/dev-harness/plugin/rules.md`](references/rules.md)
- [`docs/dev-harness/plugin/incidents.md`](references/incidents.md)
- 当涉及 instrumentation 测试隔离时还需 Read [`docs/dev-harness/test/rules.md`](../test-stability-skill/references/rules.md)

## Workflow checklist

1. 读 rules.md / incidents.md，确认改动域：QuickJS runtime / PluginManager / JsBridge / require shim / PluginApi 能力。
2. 任何涉及 QuickJS Context / JsBridge 的访问都必须在专用单线程 dispatcher 上（INC-2026-0009 / rule-quickjs-single-thread）。
3. 新增 androidTest：网络依赖类必须命名 `*NetworkIntegrationTest.kt` 且 `@Before` 调 `Assume.assumeTrue("...", arg == "true")`，参数从 `pluginNetworkTests` 读（rule-network-test-gated / INC-2026-0010）。
4. instrumentation 中手工 `PreferenceDataStoreFactory.create(...)` 必须 `produceFile = { File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb") }`（rule-datastore-per-instance-isolation / INC-2026-0004）。
5. 验证：默认 `./gradlew :plugin:connectedAndroidTest` 跳过真网用例；`-Pintegration` 启用全部用例。
6. 单测 `./gradlew :plugin:testDebugUnitTest --no-daemon`。

## References

- [plugin-api-surface.md](references/plugin-api-surface.md)
- [quickjs-threading.md](references/quickjs-threading.md)
- [mock-webserver-recipe.md](references/mock-webserver-recipe.md)
- [network-test-gate.md](references/network-test-gate.md)
