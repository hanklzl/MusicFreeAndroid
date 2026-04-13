package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModel
import com.zili.android.musicfreeandroid.player.model.PlayerState

internal fun PlayerState.toMiniPlayerUiModel(): MiniPlayerUiModel = MiniPlayerUiModel(
    coverUri = currentItem?.artwork,
    title = currentItem?.title ?: "",
    artist = currentItem?.artist ?: "",
    isPlaying = isPlaying,
    progress = if (duration > 0L) position.toFloat() / duration else 0f,
    hasPrev = true,
    hasNext = true,
    prevTitle = null,
    nextTitle = null,
)

@Composable
fun MiniPlayer(
    onNavigateToPlayer: () -> Unit,
    onNavigateToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()

    if (!state.hasMedia) return

    MiniPlayerContent(
        uiModel = state.toMiniPlayerUiModel(),
        onOpenPlayer = onNavigateToPlayer,
        onTogglePlayPause = viewModel::togglePlayPause,
        onOpenQueue = onNavigateToQueue ?: {},
        onSkipNext = {},
        onSkipPrev = {},
        modifier = modifier,
    )
}
