package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLyricsInteractionTest {

    private val lines = listOf(
        ParsedLyricLine(index = 0, timeMs = 1_000L, text = "A"),
        ParsedLyricLine(index = 1, timeMs = 2_000L, text = "B"),
        ParsedLyricLine(index = 2, timeMs = 4_000L, text = "C"),
    )

    @Test
    fun initialScrollFallsBackToFirstLineBeforeFirstTimestamp() {
        assertEquals(0, initialLyricScrollIndex(currentLineIndex = null, lineCount = 3))
    }

    @Test
    fun initialScrollUsesCurrentLineWhenPresent() {
        assertEquals(2, initialLyricScrollIndex(currentLineIndex = 2, lineCount = 3))
    }

    @Test
    fun initialScrollReturnsNullForEmptyLyrics() {
        assertNull(initialLyricScrollIndex(currentLineIndex = null, lineCount = 0))
    }

    @Test
    fun autoFollowRequiresPlaybackAndNoUserInteraction() {
        assertTrue(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = false,
                isUserScrolling = false,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = false,
                isProgrammaticScroll = false,
                isUserScrolling = false,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = true,
                isUserScrolling = false,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = false,
                isUserScrolling = true,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = false,
                isUserScrolling = false,
                seekOverlayVisible = true,
            ),
        )
    }

    @Test
    fun autoFollowTargetSkipsAlreadyFollowedLine() {
        assertFalse(
            shouldAutoFollowLyricTarget(
                currentLineIndex = 1,
                lastAutoFollowLineIndex = 1,
            ),
        )
        assertTrue(
            shouldAutoFollowLyricTarget(
                currentLineIndex = 1,
                lastAutoFollowLineIndex = 0,
            ),
        )
        assertTrue(
            shouldAutoFollowLyricTarget(
                currentLineIndex = 1,
                lastAutoFollowLineIndex = null,
            ),
        )
    }

    @Test
    fun centerVisibleLineChoosesItemClosestToViewportCenter() {
        val visible = listOf(
            VisibleLyricListItem(index = 0, offset = 0, size = 80),
            VisibleLyricListItem(index = 1, offset = 90, size = 80),
            VisibleLyricListItem(index = 2, offset = 180, size = 80),
        )

        assertEquals(
            lines[1],
            centerVisibleLyricLine(
                lines = lines,
                visibleItems = visible,
                viewportStartOffset = 0,
                viewportHeight = 260,
            ),
        )
    }

    @Test
    fun seekOverlayOnlyShowsForManualTimedLyricsWithTargetLine() {
        assertTrue(
            shouldShowSeekOverlay(
                isUserScrolling = true,
                isTimedDocument = true,
                targetLine = lines[0],
            ),
        )
        assertFalse(
            shouldShowSeekOverlay(
                isUserScrolling = false,
                isTimedDocument = true,
                targetLine = lines[0],
            ),
        )
        assertFalse(
            shouldShowSeekOverlay(
                isUserScrolling = true,
                isTimedDocument = false,
                targetLine = lines[0],
            ),
        )
        assertFalse(
            shouldShowSeekOverlay(
                isUserScrolling = true,
                isTimedDocument = true,
                targetLine = null,
            ),
        )
    }

    @Test
    fun overlayBlocksBlankTapToCover() {
        assertFalse(
            shouldHandleLyricBackTap(
                tapY = 8f,
                visibleItemBounds = emptyList(),
                dragSeekOverlayVisible = true,
            ),
        )
    }

    @Test
    fun blankTapOnlyTriggersOutsideVisibleLyricBounds() {
        val bounds = listOf(
            VisibleLyricItemBounds(top = 100, bottom = 180),
            VisibleLyricItemBounds(top = 220, bottom = 300),
        )

        assertFalse(shouldHandleLyricBackTap(120f, bounds, dragSeekOverlayVisible = false))
        assertTrue(shouldHandleLyricBackTap(40f, bounds, dragSeekOverlayVisible = false))
        assertTrue(shouldHandleLyricBackTap(340f, bounds, dragSeekOverlayVisible = false))
    }

    @Test
    fun centerScrollOffsetPlacesItemNearViewportCenter() {
        assertEquals(-90, centeredItemScrollOffset(viewportHeight = 260, itemHeight = 80))
        assertEquals(0, centeredItemScrollOffset(viewportHeight = 0, itemHeight = 80))
    }
}
