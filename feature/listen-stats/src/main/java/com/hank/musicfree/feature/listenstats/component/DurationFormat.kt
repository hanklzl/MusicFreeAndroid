package com.hank.musicfree.feature.listenstats.component

/**
 * 把秒数格式化为中文听歌时长，统一供足迹图表的长按气泡复用。
 *
 * - >= 1 小时：`"1小时23分钟"`，整点时省略分钟（`"2小时"`）
 * - >= 1 分钟：`"23分钟"`
 * - 否则：`"45秒"`（兜底不足 1 分钟的天 / 小时，避免显示「0分钟」）
 */
fun formatListenDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return when {
        hours > 0 -> if (minutes > 0) "${hours}小时${minutes}分钟" else "${hours}小时"
        minutes > 0 -> "${minutes}分钟"
        else -> "${safe}秒"
    }
}
