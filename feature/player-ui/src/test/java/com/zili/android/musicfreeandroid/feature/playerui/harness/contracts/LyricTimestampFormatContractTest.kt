package com.zili.android.musicfreeandroid.feature.playerui.harness.contracts

import com.zili.android.musicfreeandroid.core.lyric.LyricParser
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards INC-2026-0017. See docs/dev-harness/player/rules.md#rule-lyric-parser-supports-second-only-timestamp.
 *
 * Asserts that LyricParser accepts the full three-tier LRC timestamp set that
 * RN's `timeReg=/\[[\d:.]+\]/g` matches: `[hh:mm:ss(.ff)?]`, `[mm:ss(.ff)?]`,
 * and the second-only `[s(.ff)?]`. Any regex or split-arity tightening that
 * drops second-only timestamps will trip this contract and force an update of
 * INC-2026-0017 (and the rule it guards).
 */
class LyricTimestampFormatContractTest {

    private val ruleAnchor =
        "docs/dev-harness/player/rules.md#rule-lyric-parser-supports-second-only-timestamp"

    @Test
    fun parsesAllSupportedTimestampFormats() {
        val raw = "[5.5]Five and a half\n" +
            "[00:01.00]One second\n" +
            "[01:00:02.00]Hour\n" +
            "[10]Ten"

        val doc = LyricParser.parse(
            musicId = "x",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = raw),
            source = LyricSourceInfo.Plugin("demo"),
        )

        val failureContext = "INC-2026-0017 contract violated. See $ruleAnchor."

        assertTrue(
            "$failureContext doc.isTimed must be true for raw containing supported timestamps; got isTimed=${doc.isTimed}.",
            doc.isTimed,
        )

        assertEquals(
            "$failureContext expected 4 parsed lines (one per golden timestamp), got ${doc.lines.size}.",
            4,
            doc.lines.size,
        )

        val expectedTimes = listOf(1_000L, 5_500L, 10_000L, 3_602_000L)
        val actualTimes = doc.lines.map { it.timeMs }
        assertEquals(
            "$failureContext lines must be sorted ascending and cover all three RN-compatible formats; got $actualTimes.",
            expectedTimes,
            actualTimes,
        )

        doc.lines.forEach { line ->
            assertTrue(
                "$failureContext rendered text must not retain raw '[' tokens; line=${line.text}.",
                !line.text.contains('['),
            )
        }
    }
}
