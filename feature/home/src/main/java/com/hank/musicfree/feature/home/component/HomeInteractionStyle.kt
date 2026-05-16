package com.hank.musicfree.feature.home.component

import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.AnimationDurations
import com.hank.musicfree.core.theme.rpx

@Immutable
data class HomeInteractionStyle(
    val pressedScale: Float = 0.97f,
    val pressedAlpha: Float = 0.72f,
)

@Composable
internal fun rememberHomeInteractionSource(): MutableInteractionSource = remember {
    MutableInteractionSource()
}

@Composable
internal fun Modifier.homeInteractionStyle(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource = rememberHomeInteractionSource(),
    style: HomeInteractionStyle = HomeInteractionStyle(),
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(rpx(18)),
    minWidth: Dp? = null,
    minHeight: Dp? = rpx(96),
    role: Role? = null,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (enabled && isPressed) style.pressedScale else 1f,
        animationSpec = tween(durationMillis = AnimationDurations.FAST),
        label = "homeInteractionScale",
    )
    val pressedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (enabled && isPressed) style.pressedAlpha else 1f,
        animationSpec = tween(durationMillis = AnimationDurations.FAST),
        label = "homeInteractionAlpha",
    )

    var modifier = this
        .clip(shape)
        .graphicsLayer {
            scaleX = pressedScale
            scaleY = pressedScale
            alpha = pressedAlpha
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )

    if (role != null) {
        modifier = modifier.semantics { this[SemanticsProperties.Role] = role }
    }

    return if (minWidth != null || minHeight != null) {
        modifier.requiredSizeIn(
            minWidth = minWidth ?: 0.dp,
            minHeight = minHeight ?: 0.dp,
        )
    } else {
        modifier
    }
}

@Composable
internal fun Modifier.homeIconButtonInteractionStyle(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource = rememberHomeInteractionSource(),
    style: HomeInteractionStyle = HomeInteractionStyle(),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
): Modifier = homeInteractionStyle(
    onClick = onClick,
    interactionSource = interactionSource,
    style = style,
    enabled = enabled,
    shape = shape,
    minWidth = 48.dp,
    minHeight = 48.dp,
    role = Role.Button,
)
