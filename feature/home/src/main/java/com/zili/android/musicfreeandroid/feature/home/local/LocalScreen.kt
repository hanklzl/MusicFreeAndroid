package com.zili.android.musicfreeandroid.feature.home.local

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.permissions.requiredAudioPermission
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.HomeViewModel
import com.zili.android.musicfreeandroid.feature.home.playlist.AddToPlaylistDialog
import com.zili.android.musicfreeandroid.feature.home.playlist.PlaylistViewModel

@Composable
fun LocalScreen(
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var addToPlaylistItem by remember { mutableStateOf<MusicItem?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.scanLocalMusic()
    }

    LaunchedEffect(Unit) {
        val permission = requiredAudioPermission()
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLocalMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    addToPlaylistItem?.let { item ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylistItem = null },
            onSelect = { playlist ->
                viewModel.addToPlaylist(playlist.id, item)
            },
        )
    }

    LocalMusicContent(
        uiState = uiState,
        onItemClick = { item, items ->
            viewModel.playItem(item, items)
            onNavigateToPlayer()
        },
        onItemLongClick = { item -> addToPlaylistItem = item },
        onRetry = viewModel::scanLocalMusic,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.LocalRoot),
    )
}
