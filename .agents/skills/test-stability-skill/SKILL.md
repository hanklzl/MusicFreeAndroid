---
name: test-stability
description: >
  Use this skill whenever the task touches *Test.kt files, build.gradle.kts
  test wiring, gradle.properties JVM args, MainDispatcherRule, or any
  test stability concern. Trigger phrases: "单测 hang", "runBlocking",
  "runTest", "advanceUntilIdle", "Robolectric", "DataStore multiple active",
  "D8 OOM", "@Ignore", "instrumentation runner", "connectedAndroidTest".
---

# Test Stability Skill

Cross-tool guidance for unit / integration / instrumentation test
hygiene. Pairs with the test rules + 5 test incidents seeded in v1.

## 必读 gate

- [`docs/dev-harness/test/rules.md`](references/rules.md)
- [`docs/dev-harness/test/incidents.md`](references/incidents.md)

## Workflow checklist

1. 读 rules.md / incidents.md。
2. ViewModel 单测：`runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`，禁 `runBlocking + Flow.first { predicate }`（INC-2026-0001）。
3. instrumentation 单类初始化 / helper：bounded await + 异常回传（INC-2026-0002）。
4. instrumentation DataStore：每个 test class 实例用 `UUID.randomUUID()` 后缀的 prefs 文件；`@After` cancel scope（INC-2026-0004）。
5. feature 模块声明 runner = 必带 runner 依赖（INC-2026-0005）。
6. `gradle.properties` `-Xmx` ≥ 4096m（INC-2026-0003）。
7. `@Ignore` 必须随 incident 登记 + 升级方案；不允许悄悄添加。
8. 跑：`./gradlew <module>:testDebugUnitTest --no-daemon`；instrumentation 用 `:plugin:connectedAndroidTest`、`:player:connectedDebugAndroidTest`；CI 默认通道不跑真网。

## References

- [runtest-idiom.md](references/runtest-idiom.md)
- [bounded-await.md](references/bounded-await.md)
- [datastore-isolation.md](references/datastore-isolation.md)
- [androidtest-runner-baseline.md](references/androidtest-runner-baseline.md)
- [ignore-policy.md](references/ignore-policy.md)
