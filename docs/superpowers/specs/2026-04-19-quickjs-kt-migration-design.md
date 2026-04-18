# QuickJS 引擎迁移：quickjs-wrapper-android → quickjs-kt

**日期**: 2026-04-19
**状态**: 当前规范
**范围**: `:plugin` 模块内部重构，对外接口不变

## 背景与动机

当前插件系统使用 `wang.harlon.quickjs:wrapper-android:3.2.3`，存在以下问题：

1. **嵌套数组转换崩溃**：`JSObject.toMap()` 遇到嵌套数组时抛出 `UnsupportedOperationException`，导致 `getTopLists` 等返回数组的 API 全部失败
2. **Promise 处理脆弱**：`evaluateAsync()` 依赖全局变量 hack（`globalThis.__asyncResult`），不支持嵌套 Promise
3. **AxiosShim 阻塞 JS 线程**：HTTP 请求使用同步 `OkHttp.execute()`，JS 线程在网络 I/O 期间完全阻塞
4. **cheerio 缺失**：`RequireShim` 未注册 cheerio 模块，依赖 cheerio 的插件（元力MG、元力KG、bilibili 等）搜索/解析全部失败
5. **getMediaSource 无 fallback**：插件返回 null 时直接失败，未对齐 RN 原版的 qualities fallback 逻辑

迁移到 `io.github.dokar3:quickjs-kt:1.0.5` 可以从根本上解决问题 1-3，同时顺带修复 4-5。

## 迁移范围

限定在 `:plugin` 模块内部，**上层 feature 模块零改动**。

| 文件 | 改动类型 |
|------|---------|
| `JsEngine.kt` | 完全重写 |
| `AxiosShim.kt` | 完全重写 |
| `RequireShim.kt` | 适配重写 |
| `LoadedPlugin.kt` | 大幅简化 |
| `PluginManager.kt` | 少量适配 |
| `plugin/build.gradle.kts` | 依赖替换 |
| `libs.versions.toml` | 版本声明替换 |

新增文件：

| 文件 | 说明 |
|------|------|
| `plugin/src/main/assets/jslibs/cheerio.min.js` | cheerio standalone browser bundle |

## 详细设计

### 1. JsEngine 重写

#### 线程模型

- 移除手动的 `Executors.newSingleThreadExecutor` + `asCoroutineDispatcher`
- 改为创建 `QuickJs` 时传入 `Dispatchers.IO.limitedParallelism(1)` 或让 quickjs-kt 自行管理线程
- `runOnJsThread` 包装器移除——quickjs-kt 的 `evaluate()` 是 suspend 函数，内部保证线程亲和性

#### 生命周期

- `create()` → `QuickJs.create()`
- `destroy()` → `quickJs.close()`
- `destroyOnJsThread()` 移除——不再需要区分线程上下文

#### 核心 API 映射

| 能力 | 现在 (quickjs-wrapper) | 迁移后 (quickjs-kt) |
|------|------------------------|---------------------|
| 同步求值 | `ctx.evaluate(code)` 返回 `Object` | `quickJs.evaluate<T>(code)` 带泛型 |
| 异步求值 | 自建 `evaluateAsync()`，全局变量 hack | `quickJs.evaluate<T>(asyncExpr)` 原生 Promise 解析 |
| JS→Kotlin | `ctx.parse()` → `toMap()`/`toArray()` (嵌套数组 bug) | `evaluate()` 返回 `JsObject`（实现 `Map<String, Any?>`），嵌套数组自动转 `List` |
| Kotlin→JS | `setGlobalMap()` 手动递归构建 JSObject | `evaluate()` 配合 `JSON.parse()` 或 `define()` DSL |
| 函数绑定 | `JSCallFunction` 回调 | `function()` / `asyncFunction()` DSL |

#### 移除的方法

- `evaluateAsync()` — quickjs-kt 原生处理 Promise
- `jsObjectToMap()` / `jsArrayToList()` — JsObject 已实现 Map 接口
- `setGlobalMap()` — 改为 JSON.parse 注入
- `mapToJsObject()` / `listToJsArray()` / `setJsProperty()` / `toJsValue()` — 不再需要手动构建 JS 对象

### 2. AxiosShim 重写——异步非阻塞

#### 核心改动

当前：`JSCallFunction` 同步回调 + `OkHttp.execute()` 阻塞 JS 线程。

迁移后：用 `asyncFunction()` 绑定，block 内调用 OkHttp 的 suspend 扩展，JS 线程在网络 I/O 期间释放。

```
JS: const resp = await axios.get(url, config)
      ↓
Kotlin: asyncFunction("get") { args ->
            val resp = httpClient.newCall(request).await()  // suspend
            mapOf("status" to resp.code, "data" to body)
        }
```

#### OkHttp suspend 扩展

用 `suspendCancellableCoroutine` 包装 `enqueue()`：

```kotlin
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
    })
    cont.invokeOnCancellation { cancel() }
}
```

#### 返回值构建

- 移除 `context.createNewJSObject()` 手动构建
- 直接返回 `mapOf("status" to code, "data" to parsedBody)`，quickjs-kt 自动转换

#### config 参数读取

- 移除 `JSObject.getProperty()` + `getNames()` 遍历
- `JsObject` 实现了 `Map`，直接用 `config["headers"]`、`config["params"]`

#### 保持不变的逻辑

- URL query params 拼接
- Header 注入
- gzip/deflate 解压
- form-urlencoded 编码
- Content-Type 推断
- 错误响应格式

### 3. RequireShim 适配

#### 模块注册

CommonJS wrapper 脚本内容不变，仅 evaluate API 从 `QuickJSContext.evaluate()` 改为 `quickJs.evaluate()`。

#### require 函数绑定

从 `JSCallFunction` 回调改为 `function("__require")` DSL。

#### 新增 cheerio

```kotlin
private val moduleAssetPaths = linkedMapOf(
    "cheerio" to "jslibs/cheerio.min.js",  // 新增
    "crypto-js" to "jslibs/crypto-js.js",
    "qs" to "jslibs/qs.js",
    "he" to "jslibs/he.js",
    "dayjs" to "jslibs/dayjs.min.js",
    "big-integer" to "jslibs/BigInteger.min.js",
)
```

cheerio 使用 standalone browser bundle（纯 JS，无 native 依赖），可直接在 QuickJS 中运行。

### 4. LoadedPlugin 简化

#### 移除的中间层

- `parseJsonToAny()` — 不再需要（JsObject 已是 Map）
- `parseJsonToMap()` — 不再需要
- JS 侧 `JSON.stringify(r)` — 不再需要序列化往返

#### 14 个 API 方法统一模式

迁移前：
```kotlin
engine.runOnJsThread {
    val asyncExpr = "async function() { var r = await __plugin.search(...); return JSON.stringify(r); }()"
    val jsonStr = engine.evaluateAsync(asyncExpr)
    val parsed = parseJsonToMap(jsonStr)
    JsBridge.parseSearchResult(parsed)
}
```

迁移后：
```kotlin
val result = engine.evaluate<JsObject>(
    "await __plugin.search('${escapeJsString(query)}', $page, '${escapeJsString(type)}')"
)
if (result == null) return SearchResult(isEnd = true, data = emptyList())
JsBridge.parseSearchResult(result)  // JsObject 直接当 Map 传入
```

#### getMediaSource fallback

对齐 RN 原版，当插件返回 null 时尝试从 musicItem.qualities 获取 URL：

```kotlin
if (result == null) {
    val fallbackUrl = musicItem.qualities?.get(quality)?.url
    return fallbackUrl?.let { MediaSourceResult(url = it) }
}
```

#### JsBridge 无改动

所有 `parseXxx` 方法的签名接受 `Map<String, Any?>`，`JsObject` 实现了这个接口，完全兼容。

### 5. PluginManager 适配

- `loadPluginFromFile` 中引擎创建改为 `JsEngine.create()` 返回已初始化实例
- `AxiosShim.register()` 和 `RequireShim.register()` 签名适配新 API
- `runOnJsThread` 包装去掉
- `extractPluginInfo` 中 `engine.evaluate()` 调用不变
- 其余逻辑（文件读取、plugin 包装、userVariables 注入）保持不变

### 6. 依赖变更

```toml
# libs.versions.toml

# 移除
quickjsWrapper = "3.2.3"
quickjs-wrapper-android = { group = "wang.harlon.quickjs", name = "wrapper-android", version.ref = "quickjsWrapper" }

# 新增
quickjsKt = "1.0.5"
quickjs-kt = { group = "io.github.dokar3", name = "quickjs-kt-android", version.ref = "quickjsKt" }
```

```kotlin
// plugin/build.gradle.kts
// 移除
implementation(libs.quickjs.wrapper.android)
// 新增
implementation(libs.quickjs.kt)
```

## 风险与缓解

| 风险 | 级别 | 缓解措施 |
|------|------|----------|
| CommonJS require 兼容 | 高 | RequireShim 逻辑复用，全量 6 个插件回归测试 |
| QuickJS 底层版本差异 | 中 | 关注 ES 特性支持差异，插件端到端验证 |
| cheerio bundle 体积 | 低 | minified ~80KB，可接受 |
| asyncFunction 行为差异 | 中 | AxiosShim 需完整测试 GET/POST/error/redirect 路径 |

## 验收标准

1. 6 个已安装插件全部成功加载（PluginManager 日志无 ERROR）
2. `getTopLists` 在元力KW 上不再崩溃，返回有效数据
3. `search` 在元力MG 上不再因 cheerio 缺失而失败
4. `getMediaSource` 在元力WY 上有 fallback 兜底
5. 编译通过，无 quickjs-wrapper-android 残留引用
6. 端到端链路验证：插件安装 → 搜索 → 播放 → 歌词
