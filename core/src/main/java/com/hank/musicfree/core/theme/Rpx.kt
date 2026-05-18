package com.hank.musicfree.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Responsive pixel unit matching original MusicFree's rpx system.
 * Formula: rpx(value) = (value / 750) * min(screenWidth, screenHeight)
 * Based on 750px design width.
 */
@Composable
fun rpx(value: Int): Dp {
    val containerSize = LocalWindowInfo.current.containerSize
    val minEdge = min(containerSize.width, containerSize.height)
    return with(LocalDensity.current) {
        minEdge.toDp() * (value.toFloat() / 750f)
    }
}

@Composable
fun rpxSp(value: Int): TextUnit {
    val containerSize = LocalWindowInfo.current.containerSize
    val minEdge = min(containerSize.width, containerSize.height)
    val minEdgeDp = with(LocalDensity.current) { minEdge.toDp().value }
    return ((value.toFloat() / 750f) * minEdgeDp).sp
}
