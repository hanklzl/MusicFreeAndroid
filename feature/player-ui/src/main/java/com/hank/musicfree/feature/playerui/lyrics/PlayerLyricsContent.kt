package com.hank.musicfree.feature.playerui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.R
import com.hank.musicfree.core.model.ParsedLyricLine
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.theme.rpxSp
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun PlayerLyricsContent(
    state: PlayerLyricsUiState,
    durationMs: Long,
    isPlaying: Boolean,
    onBackToCover: () -> Unit,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val document = state.document
    val loadState = state.loadState
    val currentLineIndex = state.currentLineIndex
    val latestCurrentLineIndex by rememberUpdatedState(currentLineIndex)
    val lines = document?.lines.orEmpty()
    val isTimedDocument = document?.isTimed == true && lines.isNotEmpty()
    var isUserScrollingLyrics by remember(document) { mutableStateOf(false) }
    var dragSeekLine by remember(document) { mutableStateOf(state.manualSeekPreviewLine) }
    var showDragSeekOverlay by remember(document) {
        mutableStateOf(state.manualSeekPreviewLine != null)
    }
    var programmaticScrollCount by remember(document) { mutableIntStateOf(0) }
    val isProgrammaticScroll by remember { derivedStateOf { programmaticScrollCount > 0 } }
    var hasInitialPositioned by remember(document, state.fontSizeLevel, state.userOffsetMs) {
        mutableStateOf(false)
    }
    var lastAutoFollowLineIndex by remember(document, state.fontSizeLevel, state.userOffsetMs) {
        mutableStateOf<Int?>(null)
    }

    val centerVisibleLine by remember(
        document,
        listState.layoutInfo.visibleItemsInfo,
        listState.layoutInfo.viewportStartOffset,
        listState.layoutInfo.viewportSize.height,
    ) {
        derivedStateOf {
            centerVisibleLyricLine(
                lines = lines,
                visibleItems = listState.layoutInfo.visibleItemsInfo.map {
                    VisibleLyricListItem(
                        index = it.index,
                        offset = it.offset,
                        size = it.size,
                    )
                },
                viewportStartOffset = listState.layoutInfo.viewportStartOffset,
                viewportHeight = listState.layoutInfo.viewportSize.height,
            )
        }
    }

    val nestedScrollConnection = remember(document, isTimedDocument, lines.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || !isTimedDocument) {
                    return Offset.Zero
                }
                lastAutoFollowLineIndex = null
                isUserScrollingLyrics = true
                dragSeekLine = centerVisibleLine
                showDragSeekOverlay = shouldShowSeekOverlay(
                    isUserScrolling = true,
                    isTimedDocument = isTimedDocument,
                    targetLine = centerVisibleLine,
                )
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || !isTimedDocument) {
                    return Offset.Zero
                }
                lastAutoFollowLineIndex = null
                isUserScrollingLyrics = true
                dragSeekLine = centerVisibleLine
                showDragSeekOverlay = shouldShowSeekOverlay(
                    isUserScrolling = true,
                    isTimedDocument = isTimedDocument,
                    targetLine = centerVisibleLine,
                )
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isUserScrollingLyrics = false
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            isUserScrollingLyrics = false
        }
    }

    LaunchedEffect(state.manualSeekPreviewLine) {
        dragSeekLine = state.manualSeekPreviewLine
        showDragSeekOverlay = state.manualSeekPreviewLine != null
    }

    LaunchedEffect(showDragSeekOverlay, isUserScrollingLyrics, state.manualSeekPreviewLine) {
        if (!isUserScrollingLyrics && showDragSeekOverlay && state.manualSeekPreviewLine == null) {
            delay(2_000L)
            if (!isUserScrollingLyrics) {
                showDragSeekOverlay = false
                dragSeekLine = null
            }
        }
    }

    LaunchedEffect(document, state.fontSizeLevel, state.userOffsetMs) {
        val doc = document ?: return@LaunchedEffect
        val targetIndex = initialLyricScrollIndex(latestCurrentLineIndex, doc.lines.size)
            ?: return@LaunchedEffect
        delay(120L)
        scrollToLyricIndex(
            listState = listState,
            index = targetIndex,
            animated = false,
            onIncrementProgrammaticScroll = {
                programmaticScrollCount++
            },
            onDecrementProgrammaticScroll = {
                programmaticScrollCount = max(0, programmaticScrollCount - 1)
            },
        )
        hasInitialPositioned = true
        lastAutoFollowLineIndex = targetIndex
    }

    LaunchedEffect(
        currentLineIndex,
        state.document,
        state.fontSizeLevel,
        state.userOffsetMs,
        isPlaying,
        isUserScrollingLyrics,
        showDragSeekOverlay,
        hasInitialPositioned,
    ) {
        if (!hasInitialPositioned) {
            return@LaunchedEffect
        }
        if (!shouldAutoFollowLyricLine(
                isPlaying = isPlaying,
                isProgrammaticScroll = isProgrammaticScroll,
                isUserScrolling = isUserScrollingLyrics,
                seekOverlayVisible = showDragSeekOverlay,
            )
        ) {
            return@LaunchedEffect
        }

        val currentLineIndexFinal = currentLineIndex ?: return@LaunchedEffect
        val doc = loadState as? LyricLoadState.Ready ?: return@LaunchedEffect
        if (!doc.document.isTimed) return@LaunchedEffect
        if (currentLineIndexFinal !in doc.document.lines.indices) return@LaunchedEffect
        if (
            !shouldAutoFollowLyricTarget(
                currentLineIndex = currentLineIndexFinal,
                lastAutoFollowLineIndex = lastAutoFollowLineIndex,
            )
        ) {
            return@LaunchedEffect
        }

        scrollToLyricIndex(
            listState = listState,
            index = currentLineIndexFinal,
            animated = true,
            onIncrementProgrammaticScroll = {
                programmaticScrollCount++
            },
            onDecrementProgrammaticScroll = {
                programmaticScrollCount = max(0, programmaticScrollCount - 1)
            },
        )
        lastAutoFollowLineIndex = currentLineIndexFinal
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(PlayerLyricsContentTestTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !showDragSeekOverlay,
                onClick = onBackToCover,
            )
            .semantics {
                contentDescription = "返回封面"
                role = Role.Button
                if (!showDragSeekOverlay) {
                    onClick(label = "返回封面") {
                        onBackToCover()
                        true
                    }
                }
            },
    ) {
        when (val load = loadState) {
            LyricLoadState.NoTrack -> {
                CenterText("暂无播放歌曲")
            }

            is LyricLoadState.Loading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is LyricLoadState.NoLyric -> {
                NoLyricContent()
            }

            is LyricLoadState.Error -> {
                CenterText("${load.message.ifBlank { "歌词加载失败" }}\n重试\n搜索歌词")
            }

            is LyricLoadState.Ready -> {
                if (load.document.lines.isEmpty()) {
                    CenterText("暂无歌词")
                } else {
                    LyricsList(
                        load.document.lines,
                        listState = listState,
                        currentLineIndex = currentLineIndex,
                        showTranslation = state.showTranslation,
                        fontSizeLevel = state.fontSizeLevel,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                    )

                    if (dragSeekLine != null && showDragSeekOverlay) {
                        DragSeekOverlay(
                            line = dragSeekLine!!,
                            durationMs = durationMs,
                            onSeekToLine = {
                                onSeekToLine(it)
                                showDragSeekOverlay = false
                                dragSeekLine = null
                                isUserScrollingLyrics = false
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

private suspend fun scrollToLyricIndex(
    listState: LazyListState,
    index: Int,
    animated: Boolean,
    onIncrementProgrammaticScroll: () -> Unit,
    onDecrementProgrammaticScroll: () -> Unit,
) {
    val itemHeight = listState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == index }
        ?.size
        ?: listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size
        ?: 0
    val viewportHeight = listState.layoutInfo.viewportSize.height
    val centeredOffset = centeredItemScrollOffset(
        viewportHeight = viewportHeight,
        itemHeight = itemHeight,
    )

    onIncrementProgrammaticScroll()
    try {
        if (animated) {
            listState.animateScrollToItem(index, centeredOffset)
        } else {
            listState.scrollToItem(index, centeredOffset)
        }
    } finally {
        onDecrementProgrammaticScroll()
    }
}

@Composable
private fun LyricsList(
    lines: List<ParsedLyricLine>,
    listState: LazyListState,
    currentLineIndex: Int?,
    showTranslation: Boolean,
    fontSizeLevel: Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = rpx(220)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(
            items = lines,
            key = { line -> line.index },
        ) { line ->
            val isCurrentLine = line.index == currentLineIndex
            val primaryText = line.text.ifBlank { "..." }
            val targetColor = if (isCurrentLine) {
                MusicFreeTheme.colors.primary
            } else {
                Color.White.copy(alpha = 0.65f)
            }
            val color by animateColorAsState(
                targetValue = targetColor,
                label = "LyricLineColor",
            )

            Text(
                text = buildString {
                    append(primaryText)
                    if (showTranslation && !line.translation.isNullOrBlank()) {
                        append('\n')
                        append(line.translation)
                    }
                },
                color = color,
                fontSize = lyricFontSize(fontSizeLevel),
                fontWeight = if (isCurrentLine) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(64), vertical = rpx(20)),
            )
        }
    }
}

@Composable
private fun DragSeekOverlay(
    line: ParsedLyricLine,
    durationMs: Long,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(92))
            .padding(horizontal = rpx(18))
            .testTag(PlayerLyricsSeekOverlayTestTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatMsToMinuteSecond(line.timeMs, durationMs),
            color = Color(0xFFDDDDDD),
            fontSize = FontSizes.description,
            modifier = Modifier
                .padding(end = rpx(14))
                .background(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(rpx(22)),
                )
                .padding(horizontal = rpx(14), vertical = rpx(8)),
        )

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.4f),
        )

        IconButton(
            onClick = { onSeekToLine(line.timeMs) },
            modifier = Modifier
                .size(rpx(64))
                .padding(start = rpx(14))
                .testTag(PlayerLyricsSeekButtonTestTag),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_play),
                contentDescription = "播放该行",
                tint = Color.White,
                modifier = Modifier.size(rpx(32)),
            )
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = FontSizes.title,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoLyricContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "暂无歌词",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = FontSizes.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(PlayerLyricsNoLyricTextTestTag),
        )
        Text(
            text = "搜索歌词",
            color = Color(0xFF66EEFF),
            fontSize = FontSizes.title,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = rpx(14))
                .testTag(PlayerLyricsSearchTextTestTag)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
    }
}

@Composable
private fun lyricFontSize(level: Int) = when (level.coerceIn(0, 3)) {
    0 -> rpxSp(24)
    1 -> rpxSp(30)
    2 -> rpxSp(36)
    else -> rpxSp(42)
}

private fun formatMsToMinuteSecond(timeMs: Long, durationMs: Long): String {
    val durationFloor = max(0L, durationMs)
    val clamped = if (durationFloor > 0L) {
        timeMs.coerceIn(0L, durationFloor)
    } else {
        max(0L, timeMs)
    }
    val minutes = clamped / 60_000
    val seconds = (clamped / 1000) % 60
    return "%d:%02d".format(minutes, seconds)
}
