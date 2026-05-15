package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem

internal object ManualLyricAssociationParser {
    private val jsonStringRegexTemplate = """"%s"\s*:\s*"((?:\\.|[^"\\])*)""""
    private val jsonNumberRegexTemplate = """"%s"\s*:\s*(\d+)"""

    fun parse(input: String, fallback: MusicItem? = null): MusicItem? {
        val raw = input.trim()
        if (raw.isBlank()) return null
        return if (raw.startsWith("{")) {
            parseJsonLike(raw, fallback)
        } else {
            parseMediaKey(raw, fallback)
        }
    }

    private fun parseMediaKey(raw: String, fallback: MusicItem?): MusicItem? {
        val separatorIndex = raw.indexOf('@')
        if (separatorIndex <= 0 || separatorIndex == raw.lastIndex) return null
        val platform = raw.take(separatorIndex).trim()
        val id = raw.drop(separatorIndex + 1).trim()
        if (platform.isBlank() || id.isBlank()) return null
        return buildMusicItem(
            platform = platform,
            id = id,
            fallback = fallback,
            title = null,
            artist = null,
            album = null,
            duration = null,
            url = null,
            artwork = null,
        )
    }

    private fun parseJsonLike(raw: String, fallback: MusicItem?): MusicItem? {
        val platform = raw.jsonString("platform") ?: return null
        val id = raw.jsonString("id") ?: return null
        return buildMusicItem(
            platform = platform,
            id = id,
            fallback = fallback,
            title = raw.jsonString("title"),
            artist = raw.jsonString("artist"),
            album = raw.jsonString("album"),
            duration = raw.jsonLong("duration"),
            url = raw.jsonString("url"),
            artwork = raw.jsonString("artwork"),
        )
    }

    private fun buildMusicItem(
        platform: String,
        id: String,
        fallback: MusicItem?,
        title: String?,
        artist: String?,
        album: String?,
        duration: Long?,
        url: String?,
        artwork: String?,
    ): MusicItem? {
        if (platform.isBlank() || id.isBlank()) return null
        return MusicItem(
            id = id,
            platform = platform,
            title = title?.takeIf { it.isNotBlank() } ?: fallback?.title ?: id,
            artist = artist?.takeIf { it.isNotBlank() } ?: fallback?.artist ?: "",
            album = album ?: fallback?.album,
            duration = duration ?: fallback?.duration ?: 0L,
            url = url ?: fallback?.url,
            artwork = artwork ?: fallback?.artwork,
            qualities = fallback?.qualities,
        )
    }

    private fun String.jsonString(key: String): String? {
        val pattern = jsonStringRegexTemplate.format(Regex.escape(key)).toRegex()
        return pattern.find(this)?.groupValues?.getOrNull(1)?.jsonUnescape()
    }

    private fun String.jsonLong(key: String): Long? {
        val pattern = jsonNumberRegexTemplate.format(Regex.escape(key)).toRegex()
        return pattern.find(this)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun String.jsonUnescape(): String = replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\/", "/")
}
