package com.zili.android.musicfreeandroid.feature.home.local

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.permissions.requiredAudioPermission
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeStatusBarChrome
import com.zili.android.musicfreeandroid.feature.home.HomeUiState
import com.zili.android.musicfreeandroid.feature.home.HomeViewModel

@Composable
fun LocalScreen(
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = remember { requiredAudioPermission() }

    // TODO(Task 27): track addToPlaylistItem for AddToPlaylistBottomSheet
    var hasAudioPermission by remember { mutableStateOf<Boolean?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.scanLocalMusic()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            hasAudioPermission = true
            viewModel.scanLocalMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // TODO(Task 27): wire AddToPlaylistBottomSheet (core/ui/AddToPlaylistBottomSheetContent)
    // addToPlaylistItem?.let { item -> AddToPlaylistBottomSheet(...) }

    val localUiState = when (hasAudioPermission) {
        false -> LocalMusicUiState.Error("未授予音频读取权限，请授权后重试")
        else -> uiState.toLocalMusicUiState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.LocalRoot)
            .semantics { testTagsAsResourceId = true },
    ) {
        MusicFreeStatusBarChrome(color = MusicFreeTheme.colors.pageBackground)
        LocalMusicContent(
            uiState = localUiState,
            onItemClick = { item, items ->
                viewModel.playItem(item, items)
                onNavigateToPlayer()
            },
            onItemLongClick = { _ -> /* TODO(Task 27): show AddToPlaylistBottomSheet */ },
            onRetry = {
                if (hasAudioPermission == false) {
                    permissionLauncher.launch(permission)
                } else {
                    viewModel.scanLocalMusic()
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun HomeUiState.toLocalMusicUiState(): LocalMusicUiState = when (this) {
    HomeUiState.Loading -> LocalMusicUiState.Loading
    is HomeUiState.Success -> LocalMusicUiState.Success(musicItems)
    is HomeUiState.Error -> LocalMusicUiState.Error(message)
}
