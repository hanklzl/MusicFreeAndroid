package com.hank.musicfree.core.model

/** Verbose label, used in selection sheets. RN parity: `qualityText` from `utils/qualities.ts`. */
fun PlayQuality.fullLabel(): String = when (this) {
    PlayQuality.LOW -> "低音质"
    PlayQuality.STANDARD -> "标准音质"
    PlayQuality.HIGH -> "高音质"
    PlayQuality.SUPER -> "超高音质"
}

/** Compact label, used in narrow UI such as the player operations bar. */
fun PlayQuality.shortLabel(): String = when (this) {
    PlayQuality.LOW -> "低音质"
    PlayQuality.STANDARD -> "标准"
    PlayQuality.HIGH -> "高音质"
    PlayQuality.SUPER -> "超高"
}
