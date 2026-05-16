package com.zili.android.musicfreeandroid.core.theme.runtime

import com.zili.android.musicfreeandroid.core.theme.MusicFreeColors

/**
 * 12 colour keys exposed to the custom-theme UI. Names match
 * [MusicFreeColors] fields one-to-one so the persisted JSON map is
 * forward-compatible if more keys are added later. Order matches the RN
 * `setCustomTheme/body.tsx` grid for visual parity.
 */
val CONFIGURABLE_COLOR_KEYS: List<String> = listOf(
    "primary",
    "text",
    "appBar",
    "appBarText",
    "musicBar",
    "musicBarText",
    "pageBackground",
    "backdrop",
    "card",
    "placeholder",
    "tabBar",
    "notification",
)

/**
 * Overlay [overrides] (hex strings keyed by [CONFIGURABLE_COLOR_KEYS] names)
 * onto [base]. Unknown keys are ignored; malformed hex falls back to the
 * base value so a single bad entry can never produce a broken palette.
 */
fun applyOverrides(base: MusicFreeColors, overrides: Map<String, String>): MusicFreeColors {
    if (overrides.isEmpty()) return base
    return base.copy(
        primary = overrides["primary"]?.let(::parseHexColor) ?: base.primary,
        text = overrides["text"]?.let(::parseHexColor) ?: base.text,
        appBar = overrides["appBar"]?.let(::parseHexColor) ?: base.appBar,
        appBarText = overrides["appBarText"]?.let(::parseHexColor) ?: base.appBarText,
        musicBar = overrides["musicBar"]?.let(::parseHexColor) ?: base.musicBar,
        musicBarText = overrides["musicBarText"]?.let(::parseHexColor) ?: base.musicBarText,
        pageBackground = overrides["pageBackground"]?.let(::parseHexColor) ?: base.pageBackground,
        backdrop = overrides["backdrop"]?.let(::parseHexColor) ?: base.backdrop,
        card = overrides["card"]?.let(::parseHexColor) ?: base.card,
        placeholder = overrides["placeholder"]?.let(::parseHexColor) ?: base.placeholder,
        tabBar = overrides["tabBar"]?.let(::parseHexColor) ?: base.tabBar,
        notification = overrides["notification"]?.let(::parseHexColor) ?: base.notification,
    )
}
