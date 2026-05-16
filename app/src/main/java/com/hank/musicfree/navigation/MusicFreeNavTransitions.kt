package com.hank.musicfree.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

internal const val MusicFreeScreenTransitionDurationMillis = 400

private val musicFreeScreenTransitionSpec = tween<IntOffset>(
    durationMillis = MusicFreeScreenTransitionDurationMillis,
)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreeEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = musicFreeScreenTransitionSpec,
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreeExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = musicFreeScreenTransitionSpec,
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreePopEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = musicFreeScreenTransitionSpec,
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.musicFreePopExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = musicFreeScreenTransitionSpec,
    )
