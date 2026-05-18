package com.hank.musicfree.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Design tokens from original MusicFree src/constants/uiConst.ts and src/constants/commonConst.ts.
 * All values use rpx() for responsive sizing.
 */
object FontSizes {
    val tag: TextUnit @Composable get() = rpxSp(20)
    val description: TextUnit @Composable get() = rpxSp(22)
    val subTitle: TextUnit @Composable get() = rpxSp(26)
    val content: TextUnit @Composable get() = rpxSp(28)
    val title: TextUnit @Composable get() = rpxSp(32)
    val appBar: TextUnit @Composable get() = rpxSp(36)
}

object IconSizes {
    val small: Dp @Composable get() = rpx(30)
    val light: Dp @Composable get() = rpx(36)
    val normal: Dp @Composable get() = rpx(42)
    val big: Dp @Composable get() = rpx(60)
    val large: Dp @Composable get() = rpx(72)
}

object AnimationDurations {
    const val FAST = 150
    const val NORMAL = 250
    const val SLOW = 500
}
