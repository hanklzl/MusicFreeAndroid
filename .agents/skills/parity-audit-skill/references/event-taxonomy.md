# Event Taxonomy v1

所有 parity diff 仅针对规范化事件。事件 JSON 行格式：

```json
{"kind": "<kind>", "ts_ms": <int>, "waypoint": "<id>", "side": "rn|android", "fields": {...}}
```

`waypoint` 由 `PARITY_MARK BEGIN <id>` / `PARITY_MARK END <id>` 锚点切分。

## 6 类事件

| kind | fields | 触发点 |
|---|---|---|
| `nav.enter` | `route`, `params_hash` | 进入页面 |
| `nav.leave` | `route` | 离开页面 |
| `plugin.method_called` | `plugin_id`, `method`, `args_hash` | PluginApi 14 个方法 |
| `plugin.method_returned` | `plugin_id`, `method`, `ok`, `result_summary`, `duration_ms` | 同上对应 |
| `net.request` | `url_template`, `method` | OkHttp / fetch 发起 |
| `net.response` | `url_template`, `method`, `status`, `duration_ms` | 同上回包 |
| `play.state_changed` | `from`, `to`, `track_id_hash` | ExoPlayer / RN trackPlayer |
| `error` | `domain`, `code`, `message_hash` | 异常 / Toast / 业务降级 |

## 字段哈希化

- `args_hash` / `params_hash` / `track_id_hash` / `message_hash`：用 `sha1(<canonical_string>)[:8]`
- `url_template`：把 query/path 里的随机或时间相关参数替换为占位（`?q=*` / `&_t=*`）

## Android 侧来源

`logging` 模块的 `ParityEventSink` 把 `MfLog.info/error/...` 调用按 taxonomy 映射为 `Log.println(Log.INFO, "PARITY_EVT", json)`。

## RN 侧来源

- 默认模式：`references/rn-logcat-parser.json` 用正则匹配 RN 自有 console.log
- patched 模式（v3）：RN 注入 `parityLogger.ts` 输出 `[PARITY_EVT]` 前缀
