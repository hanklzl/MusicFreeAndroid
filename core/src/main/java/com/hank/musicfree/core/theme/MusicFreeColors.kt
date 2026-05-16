package com.hank.musicfree.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MusicFreeColors(
    val text: Color,
    val textSecondary: Color,
    val primary: Color,
    val background: Color,
    val pageBackground: Color,
    val shadow: Color,
    val appBar: Color,
    val appBarText: Color,
    val musicBar: Color,
    val musicBarText: Color,
    val divider: Color,
    val listActive: Color,
    val mask: Color,
    val backdrop: Color,
    val tabBar: Color,
    val placeholder: Color,
    val success: Color,
    val danger: Color,
    val info: Color,
    val card: Color,
    val notification: Color,
)

// Exact hex values from original MusicFree src/core/theme.ts
val LightMusicFreeColors = MusicFreeColors(
    text = Color(0xFF333333),
    textSecondary = Color(0xB3333333),       // #333333 alpha 0.7
    primary = Color(0xFFF17D34),
    background = Color.Transparent,
    pageBackground = Color(0xFFFAFAFA),
    shadow = Color(0xFF000000),
    appBar = Color(0xFFF17D34),
    appBarText = Color(0xFFFEFEFE),
    musicBar = Color(0xFFF2F2F2),
    musicBarText = Color(0xFF333333),
    divider = Color(0x1A000000),             // rgba(0,0,0,0.1)
    listActive = Color(0x1A000000),          // rgba(0,0,0,0.1)
    mask = Color(0x33333333),                // rgba(51,51,51,0.2)
    backdrop = Color(0xFFF0F0F0),
    tabBar = Color(0xFFF0F0F0),
    placeholder = Color(0xFFEAEAEA),
    success = Color(0xFF08A34C),
    danger = Color(0xFFFC5F5F),
    info = Color(0xFF0A95C8),
    card = Color(0x88E2E2E2),                // #e2e2e288
    notification = Color(0xFFF0F0F0),
)

val DarkMusicFreeColors = MusicFreeColors(
    text = Color(0xFFFCFCFC),
    textSecondary = Color(0xB3FCFCFC),       // #fcfcfc alpha 0.7
    primary = Color(0xFF3FA3B5),
    background = Color.Transparent,
    pageBackground = Color(0xFF202020),
    shadow = Color(0xFF999999),
    appBar = Color(0xFF262626),
    appBarText = Color(0xFFFCFCFC),
    musicBar = Color(0xFF262626),
    musicBarText = Color(0xFFFCFCFC),
    divider = Color(0x1AFFFFFF),             // rgba(255,255,255,0.1)
    listActive = Color(0x1AFFFFFF),          // rgba(255,255,255,0.1)
    mask = Color(0xCC212121),                // rgba(33,33,33,0.8)
    backdrop = Color(0xFF303030),
    tabBar = Color(0xFF303030),
    placeholder = Color(0xFF424242),
    success = Color(0xFF08A34C),
    danger = Color(0xFFFC5F5F),
    info = Color(0xFF0A95C8),
    card = Color(0x88333333),                // #33333388
    notification = Color(0xFF303030),
)

val LocalMusicFreeColors = staticCompositionLocalOf { LightMusicFreeColors }
