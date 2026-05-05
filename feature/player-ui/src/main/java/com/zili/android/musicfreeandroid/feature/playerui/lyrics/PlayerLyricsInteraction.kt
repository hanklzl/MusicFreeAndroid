package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import kotlin.math.abs

internal const val PlayerLyricsContentTestTag = "player.lyrics.content"
internal const val PlayerLyricsNoLyricTextTestTag = "player.lyrics.noLyric"
internal const val PlayerLyricsSearchTextTestTag = "player.lyrics.search"
internal const val PlayerLyricsSeekOverlayTestTag = "player.lyrics.seekOverlay"
internal const val PlayerLyricsSeekButtonTestTag = "player.lyrics.seekButton"

internal data class VisibleLyricItemBounds(
    val top: Int,
    val bottom: Int,
)

internal data class VisibleLyricListItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

internal fun initialLyricScrollIndex(
    currentLineIndex: Int?,
    lineCount: Int,
): Int? {
    if (lineCount <= 0) return null
    return currentLineIndex
        ?.takeIf { it in 0 until lineCount }
        ?: 0
}

internal fun shouldAutoFollowLyricLine(
    isPlaying: Boolean,
    isProgrammaticScroll: Boolean,
    isUserScrolling: Boolean,
    seekOverlayVisible: Boolean,
): Boolean =
    isPlaying &&
        !isProgrammaticScroll &&
        !isUserScrolling &&
        !seekOverlayVisible

internal fun centerVisibleLyricLine(
    lines: List<ParsedLyricLine>,
    visibleItems: List<VisibleLyricListItem>,
    viewportStartOffset: Int,
    viewportHeight: Int,
): ParsedLyricLine? {
    if (lines.isEmpty() || visibleItems.isEmpty() || viewportHeight <= 0) return null
    val centerY = viewportStartOffset + viewportHeight / 2f
    val centerItemIndex = visibleItems.minByOrNull { item ->
        val itemCenterY = item.offset + item.size / 2f
        abs(itemCenterY - centerY)
    }?.index ?: return null
    return lines.getOrNull(centerItemIndex)
}

internal fun shouldShowSeekOverlay(
    isUserScrolling: Boolean,
    isTimedDocument: Boolean,
    targetLine: ParsedLyricLine?,
): Boolean = isUserScrolling && isTimedDocument && targetLine != null

internal fun shouldHandleLyricBackTap(
    tapY: Float,
    visibleItemBounds: List<VisibleLyricItemBounds>,
    dragSeekOverlayVisible: Boolean,
): Boolean {
    if (dragSeekOverlayVisible) return false
    return visibleItemBounds.none { bounds ->
        tapY >= bounds.top && tapY <= bounds.bottom
    }
}

internal fun centeredItemScrollOffset(
    viewportHeight: Int,
    itemHeight: Int,
): Int {
    if (viewportHeight <= 0 || itemHeight <= 0) return 0
    return -((viewportHeight - itemHeight) / 2)
}
