package com.zili.android.musicfreeandroid.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.zili.android.musicfreeandroid.core.theme.rpx

@Composable
internal fun Modifier.homeInteractionStyle(
    onClick: () -> Unit,
): Modifier = clip(RoundedCornerShape(rpx(18)))
    .defaultMinSize(minHeight = rpx(96))
    .clickable(onClick = onClick)
