# Bounded Await

instrumentation helper 模板：

```kotlin
private fun runOnAppThread(description: String, block: () -> Unit) {
    val latch = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    context.mainExecutor.execute {
        try { block() }
        catch (t: Throwable) { failure.set(t) }
        finally { latch.countDown() }
    }
    assertTrue("Timed out: $description", latch.await(5, TimeUnit.SECONDS))
    failure.get()?.let { throw it }
}
```

connect 模板：

```kotlin
@Before
fun setUp() = runBlocking {
    controller = PlayerController(context)
    withTimeout(5_000L) { controller.connect() }
}
```

不允许：无界 `latch.await()`；`mainExecutor.execute { runBlocking { ... } }`。
