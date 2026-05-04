package com.zili.android.musicfreeandroid.core.lyric

import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import kotlin.math.roundToLong

object LyricParser {
    private val tagRegex = Regex("\\[([^\\]]+)]")

    fun parse(
        musicId: String,
        musicPlatform: String,
        payload: RawLyricPayload,
        source: LyricSourceInfo,
    ): LyricDocument {
        val primaryRaw = payload.rawLrc?.takeIf { it.isNotBlank() }
        val parsedPrimary = primaryRaw?.let(::parseLrc)
        val baseLines = parsedPrimary?.lines
            ?: parsePlainText(payload.rawLrcTxt)

        val translationByTime = payload.translation
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseLrc)
            ?.lines
            ?.associateBy({ it.timeMs }, { it.text })
            .orEmpty()

        val mergedLines = baseLines.map { line ->
            line.copy(translation = translationByTime[line.timeMs])
        }

        return LyricDocument(
            musicId = musicId,
            musicPlatform = musicPlatform,
            lines = mergedLines,
            metaOffsetMs = parsedPrimary?.metaOffsetMs ?: 0L,
            isTimed = parsedPrimary?.isTimed ?: false,
            source = source,
            rawLrc = payload.rawLrc,
            rawLrcTxt = payload.rawLrcTxt,
            translationRaw = payload.translation,
        )
    }

    private fun parseLrc(raw: String): ParsedLrc {
        val parsedLines = mutableListOf<TimedText>()
        var metaOffsetMs = 0L

        raw.lineSequence().forEach { line ->
            val matches = tagRegex.findAll(line).toList()
            val timestamps = matches.mapNotNull { parseTimestampMs(it.groupValues[1]) }

            matches.firstOrNull { it.groupValues[1].startsWith("offset:", ignoreCase = true) }
                ?.let { metaOffsetMs = parseOffsetMs(it.groupValues[1]) ?: metaOffsetMs }

            if (timestamps.isNotEmpty()) {
                val text = tagRegex.replace(line, "").trim()
                if (text.isNotBlank()) {
                    timestamps.forEach { timeMs ->
                        parsedLines += TimedText(timeMs = timeMs, text = text)
                    }
                }
            }
        }

        val sortedLines = parsedLines
            .sortedBy { it.timeMs }
            .mapIndexed { index, line ->
                ParsedLyricLine(index = index, timeMs = line.timeMs, text = line.text)
            }

        return ParsedLrc(
            lines = sortedLines.ifEmpty { parsePlainText(tagRegex.replace(raw, "")) },
            metaOffsetMs = metaOffsetMs,
            isTimed = sortedLines.isNotEmpty(),
        )
    }

    private fun parsePlainText(raw: String?): List<ParsedLyricLine> = raw
        ?.takeIf { it.isNotBlank() }
        ?.trim()
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.mapIndexed { index, text ->
            ParsedLyricLine(index = index, timeMs = 0L, text = text)
        }
        ?.toList()
        .orEmpty()

    private fun parseOffsetMs(tag: String): Long? {
        val parts = tag.split(':', limit = 2)
        if (parts.size != 2 || !parts[0].equals("offset", ignoreCase = true)) return null
        return parts[1].trim().toLongOrNull()
    }

    private fun parseTimestampMs(tag: String): Long? {
        val parts = tag.split(':')
        if (parts.size !in 2..3) return null

        var totalSeconds = 0.0
        parts.forEach { part ->
            val number = part.toDoubleOrNull() ?: return null
            totalSeconds = totalSeconds * 60 + number
        }

        return (totalSeconds * 1_000).roundToLong()
    }

    private data class TimedText(
        val timeMs: Long,
        val text: String,
    )

    private data class ParsedLrc(
        val lines: List<ParsedLyricLine>,
        val metaOffsetMs: Long,
        val isTimed: Boolean,
    )
}
