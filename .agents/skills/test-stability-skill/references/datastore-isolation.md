# DataStore Isolation in Instrumentation Tests

模板：

```kotlin
private fun testPreferencesFile(prefix: String): File =
    File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb")

private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

val dataStore = PreferenceDataStoreFactory.create(
    scope = dataStoreScope,
    produceFile = { testPreferencesFile("plugin-runtime-local-it") },
)

@After
fun tearDown() {
    runBlocking { pluginManager.uninstallAllPlugins() }
    dataStoreScope.cancel()
}
```

不允许：固定 `*.preferences_pb` 文件名 + 多 `@Test` 方法 + 未关闭 scope。
