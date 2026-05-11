package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayQueueSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val uiModel by viewModel.queueUiModel.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PlayQueueSheetContent(
            uiModel = uiModel,
            onPlayIndex = viewModel::playQueueIndex,
            onRemove = viewModel::removeFromQueue,
            onClear = viewModel::clearQueue,
            onCyclePlaybackMode = viewModel::cyclePlaybackMode,
        )
    }
}
