package com.zili.android.musicfreeandroid.data.repository.theme

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * JSON codec for the persisted custom colour map. Stored as a top-level
 * `Map<String, String>` (key = [com.zili.android.musicfreeandroid.core.theme.runtime.CONFIGURABLE_COLOR_KEYS],
 * value = `#AARRGGBB` hex). Decoding never throws — malformed input falls back
 * to `emptyMap()` so a single corrupted preference can't brick the theme layer.
 */
internal object ThemeColorsJson {

    private val json = Json { ignoreUnknownKeys = true }

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(map: Map<String, String>): String = json.encodeToString(serializer, map)

    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse { emptyMap() }
    }
}
