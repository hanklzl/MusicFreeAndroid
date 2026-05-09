# runTest Idiom

模板：

```kotlin
@Test
fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
    val appPreferences = createAppPreferences()
    val viewModel = createViewModel(appPreferences)
    val treeUri = "content://..."

    viewModel.setStorageDirectory(treeUri)
    advanceUntilIdle()

    assertEquals(treeUri, appPreferences.storageDirectoryUri.first())
}
```

要点：

- `runTest` 复用 `mainDispatcherRule.dispatcher`，否则 `viewModelScope.launch` 走错 dispatcher，`advanceUntilIdle` 推不动。
- `Flow.first { predicate }` 在测试代码替换为 `advanceUntilIdle()` + `Flow.first()`。
- 不在 `runTest` 内嵌 `runBlocking`。
