package com.zili.android.musicfreeandroid.feature.home.local

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.permissions.requiredAudioPermission
import com.zili.android.musicfreeandroid.core.storage.DocumentTreeStorageAccess
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.DownloadQualityDialog
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold
import com.zili.android.musicfreeandroid.core.ui.MusicItemOptionsSheet

@Composable
fun LocalScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: () -> Unit,
    onNavigateToMusicListEditor: () -> Unit,
    onNavigateToDownloading: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalMusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeCount by viewModel.downloadActiveCount.collectAsStateWithLifecycle()
    val downloadedKeys by viewModel.downloadedKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = remember { requiredAudioPermission() }
    var optionsItem by remember { mutableStateOf<MusicItem?>(null) }
    var qualityFor by remember { mutableStateOf<MusicItem?>(null) }
    val defaultQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle(initialValue = PlayQuality.STANDARD)

    var hasAudioPermission by remember { mutableStateOf<Boolean?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingDirectoryScanRequest by remember { mutableStateOf(false) }
    var suppressNextPermissionGrantedScan by remember { mutableStateOf(false) }

    fun isAudioPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    lateinit var openDocumentTreeLauncher: ManagedActivityResultLauncher<Uri?, Uri?>

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            when {
                pendingDirectoryScanRequest -> {
                    pendingDirectoryScanRequest = false
                    openDocumentTreeLauncher.launch(null)
                }
                suppressNextPermissionGrantedScan -> {
                    suppressNextPermissionGrantedScan = false
                }
                else -> viewModel.scanLocalMusic()
            }
        } else {
            pendingDirectoryScanRequest = false
            suppressNextPermissionGrantedScan = false
        }
    }

    openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            if (isAudioPermissionGranted()) {
                hasAudioPermission = true
                runCatching {
                    DocumentTreeStorageAccess.persistReadWritePermission(
                        contentResolver = context.contentResolver,
                        treeUri = uri,
                    )
                }.onSuccess {
                    val treeUri = uri.toString()
                    viewModel.setStorageDirectoryUri(treeUri)
                    viewModel.scanLocalMusic(treeUri)
                }
            } else {
                hasAudioPermission = false
                suppressNextPermissionGrantedScan = true
                permissionLauncher.launch(permission)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isAudioPermissionGranted()) {
            hasAudioPermission = true
            viewModel.scanLocalMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    val localUiState = when (hasAudioPermission) {
        false -> LocalMusicUiState.Error("未授予音频读取权限，请授权后重试")
        else -> uiState
    }

    optionsItem?.let { item ->
        MusicItemOptionsSheet(
            item = item,
            onDismiss = { optionsItem = null },
            onDownload = { qualityFor = it; optionsItem = null },
        )
    }
    qualityFor?.let { item ->
        DownloadQualityDialog(
            initial = defaultQuality,
            onDismiss = { qualityFor = null },
            onConfirm = { q -> viewModel.download(item, q); qualityFor = null },
        )
    }

    MusicFreeScreenScaffold(
        title = "本地音乐",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.LocalRoot)
            .semantics { testTagsAsResourceId = true },
        actions = {
            IconButton(onClick = onNavigateToSearchMusicList) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = MusicFreeTheme.colors.appBarText,
                )
            }
            BadgedBox(
                badge = {
                    if (activeCount > 0) {
                        Badge { Text(activeCount.toString()) }
                    }
                },
            ) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MusicFreeTheme.colors.appBarText,
                    )
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("扫描本地音乐") },
                    onClick = {
                        menuExpanded = false
                        if (isAudioPermissionGranted()) {
                            hasAudioPermission = true
                            openDocumentTreeLauncher.launch(null)
                        } else {
                            hasAudioPermission = false
                            pendingDirectoryScanRequest = true
                            permissionLauncher.launch(permission)
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("批量编辑") },
                    onClick = {
                        menuExpanded = false
                        onNavigateToMusicListEditor()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (activeCount > 0) "下载列表（$activeCount）" else "下载列表") },
                    onClick = {
                        menuExpanded = false
                        onNavigateToDownloading()
                    },
                )
            }
        },
    ) { innerPadding ->
        LocalMusicContent(
            uiState = localUiState,
            downloadedKeys = downloadedKeys.map { it.value }.toSet(),
            onItemClick = { item, items ->
                viewModel.playItem(item, items)
                onNavigateToPlayer()
            },
            onItemLongClick = { item -> optionsItem = item },
            onRetry = {
                if (hasAudioPermission == false) {
                    permissionLauncher.launch(permission)
                } else {
                    viewModel.scanLocalMusic()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
