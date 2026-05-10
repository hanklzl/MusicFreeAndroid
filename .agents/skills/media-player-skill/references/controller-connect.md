# PlayerController Connect

正确范式（instrumentation）：

```kotlin
@Before
fun setUp() = runBlocking {
    controller = PlayerController(context)
    withTimeout(5_000L) {
        controller.connect()
    }
}
```

helper（bounded await + 异常回传）：

```kotlin
private fun runOnAppThread(description: String = "main thread action", block: () -> Unit) {
    val latch = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    context.mainExecutor.execute {
        try { block() }
        catch (t: Throwable) { failure.set(t) }
        finally { latch.countDown() }
    }
    assertTrue("Timed out waiting for $description", latch.await(5, TimeUnit.SECONDS))
    failure.get()?.let { throw it }
}
```

不允许：`mainExecutor.execute { runBlocking { controller.connect() } }`、无界 `latch.await()`、无 timeout 的 connect。
