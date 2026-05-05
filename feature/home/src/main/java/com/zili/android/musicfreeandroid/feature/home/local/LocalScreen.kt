package com.zili.android.musicfreeandroid.feature.home.local

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
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
    onNavigateToDownloading: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeCount by viewModel.downloadActiveCount.collectAsStateWithLifecycle()
    val downloadedKeys by viewModel.downloadedKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = remember { requiredAudioPermission() }

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
        LocalHeaderRow(
            activeCount = activeCount,
            onNavigateToDownloading = onNavigateToDownloading,
        )
        LocalMusicContent(
            uiState = localUiState,
            downloadedKeys = downloadedKeys.map { it.value }.toSet(),
            onItemClick = { item, items ->
                viewModel.playItem(item, items)
                onNavigateToPlayer()
            },
            onItemLongClick = { _ -> /* TODO(Task 22): show MusicItemOptionsSheet */ },
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

@Composable
private fun LocalHeaderRow(
    activeCount: Int,
    onNavigateToDownloading: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        BadgedBox(
            badge = {
                if (activeCount > 0) {
                    Badge { Text(activeCount.toString()) }
                }
            },
        ) {
            IconButton(onClick = onNavigateToDownloading) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "下载",
                    tint = MusicFreeTheme.colors.text,
                )
            }
        }
    }
}

private fun HomeUiState.toLocalMusicUiState(): LocalMusicUiState = when (this) {
    HomeUiState.Loading -> LocalMusicUiState.Loading
    is HomeUiState.Success -> LocalMusicUiState.Success(musicItems)
    is HomeUiState.Error -> LocalMusicUiState.Error(message)
}
