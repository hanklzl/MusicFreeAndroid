package com.hank.musicfree.player.listening

object ListenDimExtractor {

    private val LANG_MAP: Map<String, String> = mapOf(
        "国语" to "zh-CN", "华语" to "zh-CN", "中文" to "zh-CN",
        "mandarin" to "zh-CN", "zh" to "zh-CN", "zh-cn" to "zh-CN",
        "粤语" to "yue", "广东话" to "yue", "cantonese" to "yue",
        "英语" to "en", "english" to "en", "en" to "en",
        "日语" to "ja", "japanese" to "ja", "ja" to "ja",
        "韩语" to "ko", "korean" to "ko", "ko" to "ko",
    )

    private val GENRE_MAP: Map<String, String> = mapOf(
        "流行" to "pop", "华语流行" to "pop", "c-pop" to "pop", "pop" to "pop",
        "嘻哈" to "hip-hop", "rap" to "hip-hop", "hip hop" to "hip-hop", "hip-hop" to "hip-hop",
        "r&b" to "rnb", "节奏布鲁斯" to "rnb", "rnb" to "rnb",
        "摇滚" to "rock", "rock" to "rock", "金属" to "rock", "metal" to "rock",
        "民谣" to "folk", "folk" to "folk", "乡村" to "folk", "country" to "folk",
    )

    fun extract(raw: Map<String, Any?>?): Pair<String?, String?> {
        if (raw.isNullOrEmpty()) return null to null

        val lang = pickStringField(raw, "language", "lang")?.normLookup(LANG_MAP)

        val genreFromField = pickStringField(raw, "genre", "style", "category")?.normLookup(GENRE_MAP)
        val genreFromTags = if (genreFromField == null) pickFromTags(raw)?.normLookup(GENRE_MAP) else null

        return lang to (genreFromField ?: genreFromTags)
    }

    private fun pickStringField(raw: Map<String, Any?>, vararg keys: String): String? {
        for (k in keys) {
            val v = raw[k]
            if (v is String && v.trim().isNotEmpty()) return v
        }
        return null
    }

    private fun pickFromTags(raw: Map<String, Any?>): String? {
        for (key in listOf("tags", "tag")) {
            val v = raw[key] ?: continue
            if (v is List<*>) {
                for (item in v) {
                    if (item is String && item.trim().isNotEmpty() &&
                        GENRE_MAP.containsKey(item.lowercase().trim())
                    ) {
                        return item
                    }
                }
            }
        }
        return null
    }

    private fun String.normLookup(map: Map<String, String>): String? =
        map[this.lowercase().trim()]
}
