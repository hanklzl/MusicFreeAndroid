package com.zili.android.musicfreeandroid.feature.playerui.lyrics

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.theme.rpxSp
import kotlin.math.abs
import kotlin.math.max

@Composable
fun PlayerLyricsContent(
    state: PlayerLyricsUiState,
    durationMs: Long,
    onBackToCover: () -> Unit,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val document = state.document
    val loadState = state.loadState
    val currentLineIndex = state.currentLineIndex

    val draggingLine by remember(document) {
        derivedStateOf {
            if (!listState.isScrollInProgress) return@derivedStateOf null
            if (document == null) return@derivedStateOf null

            val items = listState.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) return@derivedStateOf null

            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportHeight = listState.layoutInfo.viewportSize.height
            if (viewportHeight <= 0) return@derivedStateOf null

            val centerLineY = viewportStart + viewportHeight / 2f
            val centerItemIndex = items.minByOrNull {
                val itemCenterY = it.offset + (it.size / 2f)
                abs(itemCenterY - centerLineY)
            }?.index ?: return@derivedStateOf null

            document.lines.getOrNull(centerItemIndex)
        }
    }

    LaunchedEffect(currentLineIndex, state.document) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        val currentLineIndexFinal = currentLineIndex ?: return@LaunchedEffect
        val doc = loadState as? LyricLoadState.Ready ?: return@LaunchedEffect
        if (currentLineIndexFinal !in doc.document.lines.indices) return@LaunchedEffect
        listState.scrollToItem(currentLineIndexFinal)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBackToCover,
            ),
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
                CenterText("暂无歌词")
            }

            is LyricLoadState.Error -> {
                CenterText("${load.message}")
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
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (draggingLine != null && listState.isScrollInProgress) {
                        DragSeekOverlay(
                            line = draggingLine!!,
                            durationMs = durationMs,
                            onSeekToLine = onSeekToLine,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
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
            val color = if (isCurrentLine) {
                MusicFreeTheme.colors.primary
            } else {
                Color.White.copy(alpha = 0.65f)
            }

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
    val lineColor = Color.White.copy(alpha = 0.8f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = lineColor,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = rpx(16)),
        ) {
            Text(
                text = formatMsToMinuteSecond(line.timeMs, durationMs),
                color = Color.White,
                fontSize = FontSizes.description,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(rpx(22)),
                    )
                    .padding(horizontal = rpx(14), vertical = rpx(8)),
            )

            IconButton(
                onClick = { onSeekToLine(line.timeMs) },
                modifier = Modifier.size(rpx(52)),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play),
                    contentDescription = "播放该行",
                    tint = Color.White,
                    modifier = Modifier.size(rpx(32)),
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = lineColor,
        )
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
