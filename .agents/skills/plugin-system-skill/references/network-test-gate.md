# Network Test Gate (-Pintegration)

`:plugin/build.gradle.kts` 把 `-Pintegration` 转换为 instrumentation runner 参数 `pluginNetworkTests`：

```kotlin
val pluginNetworkTestsEnabled = providers.gradleProperty("integration")
    .map { value -> value.isBlank() || value.toBooleanStrictOrNull() == true }
    .orElse(false)

android {
    defaultConfig {
        testInstrumentationRunnerArguments["pluginNetworkTests"] =
            pluginNetworkTestsEnabled.get().toString()
    }
}
```

测试侧：

```kotlin
@Before
fun gateNetwork() {
    val arg = InstrumentationRegistry.getArguments().getString("pluginNetworkTests")
    Assume.assumeTrue(
        "Skipping plugin network integration tests; pass -Pintegration to enable.",
        arg == "true",
    )
}
```

调用：

```bash
./gradlew :plugin:connectedAndroidTest                  # 网络用例 SKIPPED
./gradlew :plugin:connectedAndroidTest -Pintegration    # 全跑（需要稳定网络）
```

contract guard：`PluginNetworkTestGateContractTest` 在 PR 3 静态扫真域名出现的测试文件命名 + Assume 模式。
