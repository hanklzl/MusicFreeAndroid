package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModel

@Composable
fun MiniPlayer(
    onNavigateToPlayer: () -> Unit,
    onNavigateToQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()

    if (!state.hasMedia) return

    MiniPlayerContent(
        uiModel = MiniPlayerUiModel(
            coverUri = state.currentItem?.artwork,
            title = state.currentItem?.title.orEmpty(),
            subtitle = state.currentItem?.artist.orEmpty(),
            isPlaying = state.isPlaying,
            showQueueButton = true,
        ),
        onOpenPlayer = onNavigateToPlayer,
        onTogglePlayPause = viewModel::togglePlayPause,
        onOpenQueue = onNavigateToQueue,
        modifier = modifier,
    )
}
