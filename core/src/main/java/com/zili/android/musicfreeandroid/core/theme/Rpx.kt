package com.zili.android.musicfreeandroid.core.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Responsive pixel unit matching original MusicFree's rpx system.
 * Formula: rpx(value) = (value / 750) * min(screenWidth, screenHeight)
 * Based on 750px design width.
 */
@Composable
@ReadOnlyComposable
fun rpx(value: Int): Dp {
    val config = LocalConfiguration.current
    val minEdge = min(config.screenWidthDp, config.screenHeightDp)
    return ((value.toFloat() / 750f) * minEdge).dp
}

@Composable
@ReadOnlyComposable
fun rpxSp(value: Int): TextUnit {
    val config = LocalConfiguration.current
    val minEdge = min(config.screenWidthDp, config.screenHeightDp)
    return ((value.toFloat() / 750f) * minEdge).sp
}
