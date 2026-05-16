package com.hank.musicfree.core.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

enum class HorizontalSwipeDirection {
    Previous,
    Next,
}

fun Modifier.horizontalSwipeNavigation(
    enabled: Boolean = true,
    onSwipe: (HorizontalSwipeDirection) -> Unit,
): Modifier = composed {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    if (!enabled) {
        Modifier
    } else {
        Modifier.pointerInput(enabled) {
            var dragDistancePx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragDistancePx = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    dragDistancePx += dragAmount
                    change.consume()
                },
                onDragCancel = { dragDistancePx = 0f },
                onDragEnd = {
                    resolveHorizontalSwipeDirection(
                        dragDistancePx = dragDistancePx,
                        thresholdPx = size.width * HorizontalSwipeThresholdFraction,
                    )?.let(currentOnSwipe)
                    dragDistancePx = 0f
                },
            )
        }
    }
}

fun Modifier.horizontalTabSwipe(
    selectedIndex: Int,
    pageCount: Int,
    enabled: Boolean = true,
    onSelectIndex: (Int) -> Unit,
): Modifier {
    if (!enabled || pageCount <= 1 || selectedIndex !in 0 until pageCount) return this
    return horizontalSwipeNavigation { direction ->
        val target = resolveHorizontalSwipeTarget(
            selectedIndex = selectedIndex,
            pageCount = pageCount,
            direction = direction,
        )
        if (target != selectedIndex) onSelectIndex(target)
    }
}

internal fun resolveHorizontalSwipeTarget(
    selectedIndex: Int,
    pageCount: Int,
    dragDistancePx: Float,
    thresholdPx: Float,
): Int {
    if (pageCount <= 1 || selectedIndex !in 0 until pageCount) return selectedIndex
    return when (resolveHorizontalSwipeDirection(dragDistancePx, thresholdPx)) {
        HorizontalSwipeDirection.Previous -> resolveHorizontalSwipeTarget(
            selectedIndex = selectedIndex,
            pageCount = pageCount,
            direction = HorizontalSwipeDirection.Previous,
        )
        HorizontalSwipeDirection.Next -> resolveHorizontalSwipeTarget(
            selectedIndex = selectedIndex,
            pageCount = pageCount,
            direction = HorizontalSwipeDirection.Next,
        )
        null -> selectedIndex
    }
}

private fun resolveHorizontalSwipeTarget(
    selectedIndex: Int,
    pageCount: Int,
    direction: HorizontalSwipeDirection,
): Int =
    when (direction) {
        HorizontalSwipeDirection.Previous -> (selectedIndex - 1).coerceAtLeast(0)
        HorizontalSwipeDirection.Next -> (selectedIndex + 1).coerceAtMost(pageCount - 1)
    }

private fun resolveHorizontalSwipeDirection(
    dragDistancePx: Float,
    thresholdPx: Float,
): HorizontalSwipeDirection? {
    val threshold = thresholdPx.coerceAtLeast(0f)
    return when {
        dragDistancePx > threshold -> HorizontalSwipeDirection.Previous
        dragDistancePx < -threshold -> HorizontalSwipeDirection.Next
        else -> null
    }
}

private const val HorizontalSwipeThresholdFraction = 0.18f
