package com.hank.musicfree.core.ui

import androidx.compose.ui.graphics.Color

val FavoriteIconStarredTint: Color = Color(0xFFE31639)

fun favoriteIconTint(
    starred: Boolean,
    inactiveTint: Color,
): Color = if (starred) FavoriteIconStarredTint else inactiveTint
