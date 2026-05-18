package com.hank.musicfree.core.runtime

import java.util.Locale

/**
 * 运行时状态 key 的结构化封装。此处实现的是本分支首次持久化版本的 key 编码规则：
 * 每个 key 段只做 UTF-8 字节级的百分号编码（%XX），用于避免不安全字符导致的碰撞，
 * 在未见历史持久化数据的场景下作为首个持久化边界策略。后续若出现跨版本兼容需求再引入迁移机制。
 */
@JvmInline
value class RuntimeStoreKey(val value: String) {
    companion object {
        fun singleton(namespace: String): RuntimeStoreKey =
            RuntimeStoreKey("${namespace.clean()}:current")

        fun search(mediaType: String, platform: String, queryHash: String): RuntimeStoreKey =
            RuntimeStoreKey("search:${mediaType.clean()}:${platform.clean()}:${queryHash.clean()}")

        fun detail(type: String, platform: String, id: String): RuntimeStoreKey =
            RuntimeStoreKey("detail:${type.clean()}:${platform.clean()}:${id.clean()}")

        fun routeSeed(target: String, platform: String, id: String): RuntimeStoreKey =
            RuntimeStoreKey("route_seed:${target.clean()}:${platform.clean()}:${id.clean()}")

        fun plugin(platform: String): RuntimeStoreKey =
            RuntimeStoreKey("plugin:${platform.clean()}")

        fun download(taskId: String): RuntimeStoreKey =
            RuntimeStoreKey("download:${taskId.clean()}")
    }
}

private fun String.clean(): String =
    trim().encodeForRuntimeKeySegment().ifEmpty { SEGMENT_UNKNOWN }

private const val SEGMENT_UNKNOWN = "unknown"

private fun String.encodeForRuntimeKeySegment(): String {
    if (isEmpty()) return ""

    val bytes = toByteArray(Charsets.UTF_8)
    val encoded = StringBuilder(bytes.size)
    for (byte in bytes) {
        val b = byte.toInt() and 0xFF
        when (b) {
            in 48..57, // 0-9
            in 65..90, // A-Z
            in 97..122, // a-z
            46, // .
            95, // _
            45, // -
            -> encoded.append(b.toChar())

            else -> encoded.append('%').append(b.toString(16).uppercase(Locale.US).padStart(2, '0'))
        }
    }
    return encoded.toString()
}
