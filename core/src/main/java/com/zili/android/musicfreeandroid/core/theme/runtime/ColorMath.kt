package com.zili.android.musicfreeandroid.core.theme.runtime

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin colour helpers that mirror RN `MusicFree/src/utils/colorUtil.ts`.
 * Kept dependency-free (Compose [Color] + stdlib only) so they remain trivially
 * unit-testable on the JVM without Android stubs.
 */

/**
 * Bias of red vs green vs blue, replicating the RN `colorUtil.grayRate`
 * formula `(r - g) / 255 + (g - b) / 255`. Range roughly (-2, +2).
 */
fun grayRate(color: Color): Float {
    val r = (color.red * 255f).roundToInt()
    val g = (color.green * 255f).roundToInt()
    val b = (color.blue * 255f).roundToInt()
    return (r - g) / 255f + (g - b) / 255f
}

/**
 * Darken by [amount] (clamped to [-1, 1]). Positive = darker, negative = lighter.
 * Operates in HSL space, matching how RN's `color` JS library scales lightness.
 */
fun Color.darken(amount: Float): Color {
    val delta = amount.coerceIn(-1f, 1f)
    val hsl = rgbToHsl(red, green, blue)
    val newL = (hsl[2] - delta).coerceIn(0f, 1f)
    val (r, g, b) = hslToRgb(hsl[0], hsl[1], newL)
    return Color(r, g, b, alpha)
}

/**
 * Increase saturation by [amount]. Negative reduces saturation. Mirrors RN
 * `color.saturate(value)` semantics.
 */
fun Color.saturate(amount: Float): Color {
    val hsl = rgbToHsl(red, green, blue)
    val newS = (hsl[1] + amount).coerceIn(0f, 1f)
    val (r, g, b) = hslToRgb(hsl[0], newS, hsl[2])
    return Color(r, g, b, alpha)
}

/** "#AARRGGBB" upper-case, always 9 chars. */
fun Color.toHexString(): String {
    val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return buildString(9) {
        append('#')
        append(byteHex(a))
        append(byteHex(r))
        append(byteHex(g))
        append(byteHex(b))
    }
}

/**
 * Accepts `#RGB`, `#RRGGBB`, `#AARRGGBB` (case-insensitive). Returns null on
 * malformed input rather than throwing — call sites need a fall-through path.
 */
fun parseHexColor(hex: String): Color? {
    val trimmed = hex.trim()
    if (trimmed.isEmpty() || trimmed[0] != '#') return null
    val body = trimmed.substring(1)
    if (body.any { !it.isHexDigit() }) return null
    return when (body.length) {
        3 -> {
            val r = body[0].hexExpand()
            val g = body[1].hexExpand()
            val b = body[2].hexExpand()
            Color(r, g, b, 255)
        }
        6 -> {
            val r = body.substring(0, 2).toInt(16)
            val g = body.substring(2, 4).toInt(16)
            val b = body.substring(4, 6).toInt(16)
            Color(r, g, b, 255)
        }
        8 -> {
            val a = body.substring(0, 2).toInt(16)
            val r = body.substring(2, 4).toInt(16)
            val g = body.substring(4, 6).toInt(16)
            val b = body.substring(6, 8).toInt(16)
            Color(r, g, b, a)
        }
        else -> null
    }
}

// region internal

private fun byteHex(v: Int): String {
    val s = v.toString(16).uppercase()
    return if (s.length == 1) "0$s" else s
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun Char.hexExpand(): Int {
    val n = digitToInt(16)
    return (n shl 4) or n
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Color(r: Int, g: Int, b: Int, a: Int): Color =
    Color(r / 255f, g / 255f, b / 255f, a / 255f)

/** Returns [h, s, l] each in `[0, 1]` (hue normalised to fraction of 360). */
private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    if (mx == mn) return floatArrayOf(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return floatArrayOf(h, s, l)
}

/** Inverse of [rgbToHsl]; returns floats in `[0, 1]`. */
private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    if (s == 0f) return floatArrayOf(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val r = hueToRgb(p, q, h + 1f / 3f)
    val g = hueToRgb(p, q, h)
    val b = hueToRgb(p, q, h - 1f / 3f)
    return floatArrayOf(r, g, b)
}

private fun hueToRgb(p: Float, q: Float, tIn: Float): Float {
    var t = tIn
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}

// endregion
