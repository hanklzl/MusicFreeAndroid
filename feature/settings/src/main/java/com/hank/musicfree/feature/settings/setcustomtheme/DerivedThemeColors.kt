package com.hank.musicfree.feature.settings.setcustomtheme

import com.hank.musicfree.core.theme.runtime.darken
import com.hank.musicfree.core.theme.runtime.grayRate
import com.hank.musicfree.core.theme.runtime.saturate
import com.hank.musicfree.core.theme.runtime.toHexString
import kotlin.math.abs

private const val CARD_OVERLAY_HEX: String = "#33000000"

// Mirrors RN `setCustomTheme/body.tsx` (lines 65-108): grayRate buckets pick a
// different transform on the dominant colour. The else branch saturates instead
// of darkening because near-grey primaries get washed out by darken alone.
fun deriveCustomColors(palette: PaletteColors): Map<String, String> {
    val primary = palette.primary
    val gray = grayRate(primary)
    val primaryHex = primary.toHexString()
    return when {
        gray < -0.4f -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.darken(gray * 5f).toHexString(),
            "musicBar" to primaryHex,
            "card" to CARD_OVERLAY_HEX,
            "tabBar" to primary.copy(alpha = 0.2f).toHexString(),
        )
        gray > 0.4f -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.darken(gray * 5f).toHexString(),
            "musicBar" to primaryHex,
            "card" to CARD_OVERLAY_HEX,
        )
        else -> mapOf(
            "appBar" to primaryHex,
            "primary" to primary.saturate(abs(gray) * 2f + 2f).toHexString(),
            "musicBar" to primaryHex,
            "card" to CARD_OVERLAY_HEX,
        )
    }
}
