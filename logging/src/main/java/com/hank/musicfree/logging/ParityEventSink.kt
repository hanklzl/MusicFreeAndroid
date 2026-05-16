package com.hank.musicfree.logging

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * 把 MfLog 业务事件按 parity event taxonomy 映射为单行 JSON,
 * 经 emitter 输出(默认 emitter = android.util.Log.println(INFO, TAG, json))。
 *
 * Spec: docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md §4.1
 *
 * 实现说明:
 * - 设计 spec 中给出的参考实现使用 `org.json.JSONObject`,但该类在纯 JVM
 *   unit test 中是 Android stub(运行时抛 "Stub!"),会迫使本模块测试依赖
 *   Robolectric。本仓库 logging 模块的现有 JSON 序列化(`LogEventFormatter`)
 *   已统一使用 `kotlinx.serialization.json`,这里沿用同一栈以避免引入新的
 *   测试基建。对外输出仍是单行合法 JSON 字符串,语义与 spec 一致。
 */
class ParityEventSink(
    private val emitter: (String, String) -> Unit,
) {
    /**
     * 业务事件入口。若 event 名不在 taxonomy 中,直接忽略(不输出)。
     */
    fun emit(event: String, fields: Map<String, Any?>) {
        val kind = mapEventName(event) ?: return
        val canonical = canonicalize(fields)
        val payload = mutableMapOf<String, JsonElement>(
            "kind" to JsonPrimitive(kind),
            "ts_ms" to JsonPrimitive(System.currentTimeMillis()),
        )
        for ((k, v) in canonical) {
            payload[k] = v.toJsonValue()
        }
        emitter(TAG, JsonObject(payload).toString())
    }

    private fun mapEventName(event: String): String? = when (event) {
        "nav_enter" -> "nav.enter"
        "nav_leave" -> "nav.leave"
        "plugin_method_called" -> "plugin.method_called"
        "plugin_method_returned" -> "plugin.method_returned"
        "net_request" -> "net.request"
        "net_response" -> "net.response"
        "play_state_changed" -> "play.state_changed"
        "error" -> "error"
        else -> null
    }

    /**
     * 字段归一化:
     * - 敏感/高基数字段哈希化:`args` -> `args_hash`、`params` -> `params_hash`、
     *   `track_id` -> `track_id_hash`、`message` -> `message_hash`
     * - `url` 归一为 `url_template`,query/path 中随机或时间相关部分替换为 `*`
     * - 其它字段原样保留
     */
    private fun canonicalize(fields: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in fields) {
            if (v == null) continue
            when (k) {
                "args" -> out["args_hash"] = sha1Eight(v.toString())
                "params" -> out["params_hash"] = sha1Eight(v.toString())
                "track_id" -> out["track_id_hash"] = sha1Eight(v.toString())
                "message" -> out["message_hash"] = sha1Eight(v.toString())
                "url" -> out["url_template"] = normalizeUrlTemplate(v.toString())
                else -> out[k] = v
            }
        }
        return out
    }

    private fun sha1Eight(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun normalizeUrlTemplate(url: String): String {
        // 把 query/path 里随机或时间相关参数替换为占位
        return url.replace(Regex("(\\?|&)([^=&]+)=[^&]*")) { mr ->
            "${mr.groupValues[1]}${mr.groupValues[2]}=*"
        }
    }

    private fun Any?.toJsonValue(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Iterable<*> -> JsonArray(map { it.toJsonValue() })
        is Map<*, *> -> JsonObject(
            entries.associate { (key, value) -> key.toString() to value.toJsonValue() },
        )
        else -> JsonPrimitive(toString())
    }

    companion object {
        const val TAG = "PARITY_EVT"
    }
}
