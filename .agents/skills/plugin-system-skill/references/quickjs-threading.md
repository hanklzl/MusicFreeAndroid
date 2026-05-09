# QuickJS Threading

约束（rule-quickjs-single-thread / INC-2026-0009）：

- `Context` 与 `JsBridge` 实例 NOT 线程安全。
- 所有 `Context.evaluate(...)` / `JsBridge.invoke(...)` 必须在 owning dispatcher（`quickJsDispatcher`，单线程 `CoroutineDispatcher`）上执行。
- 跨线程入口必须 `withContext(quickJsDispatcher) { ... }`。

guard 当前 manual：harness-curator-skill 巡检会显式列 INC-2026-0009 提醒人工复核；再现一次跨线程崩溃即升级为 contract-test。

实现入口：`:plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/`。
