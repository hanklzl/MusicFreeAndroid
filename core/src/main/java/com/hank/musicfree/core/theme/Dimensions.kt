package com.hank.musicfree.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Design tokens from original MusicFree src/constants/uiConst.ts and src/constants/commonConst.ts.
 * All values use rpx() for responsive sizing.
 */
object FontSizes {
    val tag: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(20)
    val description: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(22)
    val subTitle: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(26)
    val content: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(28)
    val title: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(32)
    val appBar: TextUnit @Composable @ReadOnlyComposable get() = rpxSp(36)
}

object IconSizes {
    val small: Dp @Composable @ReadOnlyComposable get() = rpx(30)
    val light: Dp @Composable @ReadOnlyComposable get() = rpx(36)
    val normal: Dp @Composable @ReadOnlyComposable get() = rpx(42)
    val big: Dp @Composable @ReadOnlyComposable get() = rpx(60)
    val large: Dp @Composable @ReadOnlyComposable get() = rpx(72)
}

object AnimationDurations {
    const val FAST = 150
    const val NORMAL = 250
    const val SLOW = 500
}
