package com.zili.android.musicfreeandroid.feature.home.playlistimport

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.R as CoreR
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent
import com.zili.android.musicfreeandroid.feature.home.playlist.CreatePlaylistDialog
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun PlaylistImportRoute(
    viewModel: PlaylistImportViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val importState by viewModel.importState.collectAsState()
    val sheetState by viewModel.sheetState.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events
            .onEach { event ->
                when (event) {
                    is PlaylistImportEvent.Toast -> showToast(context, event.message)
                }
            }
            .launchIn(this)
    }

    PlaylistImportHost(
        modifier = modifier,
        importState = importState,
        sheetVisible = sheetState.visible,
        playlists = playlists,
        onSelectPlugin = viewModel::selectPlugin,
        onSubmit = viewModel::submitUrl,
        onDismiss = viewModel::dismissImportFlow,
        onConfirmFound = viewModel::confirmFoundItems,
        onSelectTarget = viewModel::addImportedItemsToPlaylist,
        onCreateTarget = viewModel::createPlaylistAndImport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportHost(
    modifier: Modifier = Modifier,
    importState: PlaylistImportState,
    sheetVisible: Boolean,
    playlists: List<Playlist>,
    onSelectPlugin: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmFound: () -> Unit,
    onSelectTarget: (String) -> Unit,
    onCreateTarget: (String) -> Unit,
) {
    var inputUrl by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    when (importState) {
        is PlaylistImportState.Idle,
        is PlaylistImportState.Completed,
        is PlaylistImportState.Error -> Unit

        is PlaylistImportState.LoadingPlugins ->
            AlertDialog(
                modifier = modifier,
                onDismissRequest = onDismiss,
                title = { Text("导入歌单") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                },
                confirmButton = {},
                dismissButton = {},
            )

        is PlaylistImportState.ChoosePlugin ->
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                modifier = modifier.testTag("PlaylistImport_PluginSheet"),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "导入歌单",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                    if (importState.plugins.isEmpty()) {
                        Text(
                            text = "暂无支持导入歌单的插件",
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("PlaylistImport_NoPlugin"),
                        )
                    } else {
                        LazyColumn {
                            items(importState.plugins) { plugin ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPlugin(plugin.platform) }
                                        .padding(16.dp)
                                        .testTag("PlaylistImport_Plugin_${plugin.platform}"),
                                ) {
                                    Text(plugin.name)
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

        is PlaylistImportState.InputUrl -> {
            LaunchedEffect(importState.plugin.platform) {
                inputUrl = ""
            }

            AlertDialog(
                modifier = modifier,
                onDismissRequest = onDismiss,
                title = { Text("导入歌单") },
                text = {
                    Column(modifier = Modifier.width(320.dp)) {
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { value ->
                                inputUrl = value.take(1000)
                            },
                            label = { Text("输入目标歌单") },
                            placeholder = { Text("输入目标歌单") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("PlaylistImport_Input"),
                        )
                        importState.errorMessage?.let { message ->
                            Text(message, modifier = Modifier.padding(top = 6.dp))
                        }
                        importState.plugin.hints.forEach { hint ->
                            Text(hint, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSubmit(inputUrl) }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                },
            )
        }

        is PlaylistImportState.Parsing ->
            AlertDialog(
                modifier = modifier,
                onDismissRequest = onDismiss,
                title = { Text("导入歌单") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("正在导入中...")
                        CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
                    }
                },
                confirmButton = {},
                dismissButton = {},
            )

        is PlaylistImportState.ConfirmFound ->
            AlertDialog(
                modifier = modifier,
                onDismissRequest = onDismiss,
                title = { Text("准备导入") },
                text = {
                    Text("发现 ${importState.items.size} 首歌曲! 现在开始导入吗?")
                },
                confirmButton = {
                    TextButton(onClick = onConfirmFound) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                },
            )

        is PlaylistImportState.ChooseTarget -> Unit
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("PlaylistImport_TargetSheet"),
        ) {
            AddToPlaylistBottomSheetContent(
                playlists = playlists,
                onSelect = { playlist -> onSelectTarget(playlist.id) },
                onCreateNew = { showCreatePlaylistDialog = true },
                folderPlusIcon = painterResource(id = CoreR.drawable.ic_folder_plus),
                favoriteCoverIcon = painterResource(id = CoreR.drawable.ic_playlist_favorite_cover),
            )
        }
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name ->
                    onCreateTarget(name)
                    showCreatePlaylistDialog = false
                },
            )
        }
    }
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
