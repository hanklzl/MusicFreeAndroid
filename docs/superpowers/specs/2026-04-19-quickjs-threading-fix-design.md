# QuickJS 线程亲和性修复与插件加载幂等化

**日期**: 2026-04-19
**状态**: 设计完成，待实施

## 问题

两个相互关联的 bug：

### Bug 1: QuickJS destroy 线程亲和性违规

`JsEngine.destroy()` 直接在调用线程上执行 `context?.destroy()`，但 QuickJS 要求 destroy 必须在创建 context 的同一线程上执行。`PluginManager.loadAllPlugins()` 在 `Dispatchers.IO` 上调用 destroy，触发 `QuickJSException: Must be call same thread in QuickJSContext.create!`。

堆栈：
```
com.whl.quickjs.wrapper.QuickJSContext.checkSameThread
  → QuickJSContext.destroy
  → JsEngine.destroy (JsEngine.kt:54)
  → LoadedPlugin.destroy (LoadedPlugin.kt:412)
  → PluginManager$loadAllPlugins (PluginManager.kt:77)
```

### Bug 2: 冷启动首次搜索 "The task was rejected"

9 个 ViewModel 在 `init` 块中各自调用 `pluginManager.loadAllPlugins()`。每次调用先销毁所有已加载插件（关闭 dispatcher），再重新加载。竞态路径：

1. ViewModel A 调用 `loadAllPlugins()`，插件加载完成
2. 用户发起搜索，拿到插件引用
3. ViewModel B 创建，触发第二次 `loadAllPlugins()`
4. 第二次调用先 `destroy()` 所有旧插件（关闭 dispatcher）
5. 搜索使用步骤 2 中已失效的插件引用 → `RejectedExecutionException`

受影响的 ViewModel（均在 init 中调用 `loadAllPlugins()`）：
- `SearchViewModel`
- `TopListViewModel`
- `TopListDetailViewModel`
- `PluginSheetDetailViewModel`
- `MusicDetailViewModel`
- `AlbumDetailViewModel`
- `RecommendSheetsViewModel`
- `ArtistDetailViewModel`
- `PluginListViewModel`

## 设计

### 修复 1: JsEngine.destroy() 线程亲和性

将 `destroy()` 改为 suspend 函数，在 `jsDispatcher` 上执行 `context.destroy()`：

```kotlin
// JsEngine.kt
suspend fun destroy() {
    try {
        withContext(jsDispatcher) {
            context?.destroy()
            context = null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error destroying QuickJS context", e)
        context = null
    } finally {
        jsDispatcher.close()
    }
}
```

`LoadedPlugin.destroy()` 同步改为 suspend。

### 修复 2: PluginManager 幂等加载

新增 `_loaded` 标志和 `ensurePluginsLoaded()` 方法：

```kotlin
// PluginManager.kt
private val _loaded = AtomicBoolean(false)

suspend fun ensurePluginsLoaded() {
    if (_loaded.get()) return
    loadAllPlugins()
}

suspend fun loadAllPlugins() = mutex.withLock {
    withContext(Dispatchers.IO) {
        _plugins.value.forEach { it.destroy() }
        val loaded = mutableListOf<LoadedPlugin>()
        // ... 加载逻辑不变 ...
        _loaded.set(true)
        _plugins.value = loaded
    }
}
```

### 修复 3: ViewModel 调用替换

所有 9 个 ViewModel 的 `init` 块中 `pluginManager.loadAllPlugins()` 替换为 `pluginManager.ensurePluginsLoaded()`。

显式的安装/卸载/更新操作仍然调用 `loadAllPlugins()`（它会重置并重新加载）。

## 影响范围

| 文件 | 变更 |
|---|---|
| `plugin/.../engine/JsEngine.kt` | `destroy()` 改 suspend |
| `plugin/.../manager/LoadedPlugin.kt` | `destroy()` 改 suspend |
| `plugin/.../manager/PluginManager.kt` | 新增 `ensurePluginsLoaded()`、`_loaded` 标志、destroy 调用适配 suspend |
| `feature/search/.../SearchViewModel.kt` | `loadAllPlugins()` → `ensurePluginsLoaded()` |
| `feature/home/.../TopListViewModel.kt` | 同上 |
| `feature/home/.../TopListDetailViewModel.kt` | 同上 |
| `feature/home/.../PluginSheetDetailViewModel.kt` | 同上 |
| `feature/home/.../MusicDetailViewModel.kt` | 同上 |
| `feature/home/.../AlbumDetailViewModel.kt` | 同上 |
| `feature/home/.../RecommendSheetsViewModel.kt` | 同上 |
| `feature/home/.../ArtistDetailViewModel.kt` | 同上 |
| `feature/settings/.../PluginListViewModel.kt` | 同上 |

## 不做的事

- 不引入新的 Initializer 类或 Application 级初始化
- 不改变插件安装/卸载/更新的 `loadAllPlugins()` 语义
- 不改变 `JsEngine.create()` 的调用方式（已在 JS 线程上）
