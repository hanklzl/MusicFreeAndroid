# MockWebServer Recipe

`PluginManagerHttpLifecycleTest`（在 `plugin/src/androidTest/.../manager/`）覆盖 `installFromUrl` 与 `updatePlugin` 的编排路径，无需触网。

模板：

```kotlin
@RunWith(AndroidJUnit4::class)
class PluginManagerHttpLifecycleTest {
    private lateinit var server: MockWebServer
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        runBlocking { pluginManager.uninstallAllPlugins() }
        dataStoreScope.cancel()
        server.shutdown()
    }

    @Test
    fun installFromUrl_writesPluginAndLoadsMeta() {
        server.enqueue(MockResponse().setBody(runtimeShimScript))
        val url = server.url("/wy.js").toString()
        // ... installFromUrl(url); assertion 包括 server.takeRequest().path == "/wy.js"
    }
}
```

DataStore 必须按 `produceFile = { File(appContext.cacheDir, "plugin-http-${UUID.randomUUID()}.preferences_pb") }`。
