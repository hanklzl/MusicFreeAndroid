package com.zili.android.musicfreeandroid.feature.settings.pluginlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.R as CoreR
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold
import com.zili.android.musicfreeandroid.feature.settings.components.SettingSectionCard
import com.zili.android.musicfreeandroid.feature.settings.components.SettingSwitchRow
import com.zili.android.musicfreeandroid.plugin.runtime.PluginState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PluginListScreen(
    onBack: () -> Unit,
    onNavigateToPluginSort: () -> Unit,
    onNavigateToPluginSubscription: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginListViewModel = hiltViewModel(),
) {
    val pluginItems by viewModel.pluginItems.collectAsStateWithLifecycle()
    val uiEntries by viewModel.uiEntries.collectAsStateWithLifecycle()
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()
    val userVariableSaveState by viewModel.userVariableSaveState.collectAsStateWithLifecycle()
    val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val lazyLoadPlugins by viewModel.lazyLoadPlugins.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val failedEntries = remember(uiEntries) {
        uiEntries.filter { it.state is PluginState.Failed }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showInstallUrlDialog by remember { mutableStateOf(false) }
    var showInstallLocalDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showUninstallAllConfirm by remember { mutableStateOf(false) }
    var failureDialog by remember { mutableStateOf<List<FailureDetail>?>(null) }
    var errorPanelTarget by remember { mutableStateOf<PluginUiEntry?>(null) }
    var alternativeTarget by remember { mutableStateOf<PluginUiItem?>(null) }
    var importMusicItemTarget by remember { mutableStateOf<PluginUiItem?>(null) }
    var importMusicSheetTarget by remember { mutableStateOf<PluginUiItem?>(null) }
    var userVariablesTarget by remember { mutableStateOf<PluginUiItem?>(null) }
    var activeUserVariableSaveRequestId by remember { mutableStateOf<Long?>(null) }
    var uninstallTarget by remember { mutableStateOf<PluginUiItem?>(null) }
    var descriptionTarget by remember { mutableStateOf<PluginUiItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userVariableSaveState, activeUserVariableSaveRequestId) {
        val requestId = activeUserVariableSaveRequestId ?: return@LaunchedEffect
        when (val state = userVariableSaveState) {
            is UserVariableSaveUiState.Success -> {
                if (state.requestId == requestId) {
                    userVariablesTarget = null
                    activeUserVariableSaveRequestId = null
                    viewModel.resetUserVariableSaveState()
                }
            }
            else -> Unit
        }
    }

    MusicFreeScreenScaffold(
        title = "插件管理",
        onBack = onBack,
        modifier = modifier,
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MusicFreeTheme.colors.appBarText,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("订阅设置") },
                        onClick = { showMenu = false; onNavigateToPluginSubscription() },
                    )
                    DropdownMenuItem(
                        text = { Text("排序") },
                        onClick = { showMenu = false; onNavigateToPluginSort() },
                    )
                    DropdownMenuItem(
                        text = { Text("卸载全部") },
                        onClick = { showMenu = false; showUninstallAllConfirm = true },
                    )
                }
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "安装插件")
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("从本地安装") },
                        onClick = { showFabMenu = false; showInstallLocalDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("从网络安装") },
                        onClick = { showFabMenu = false; showInstallUrlDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("更新全部插件") },
                        onClick = { showFabMenu = false; viewModel.updateAllPlugins() },
                    )
                    DropdownMenuItem(
                        text = { Text("更新订阅") },
                        onClick = { showFabMenu = false; viewModel.updateSubscriptions() },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (val state = operationState) {
                is PluginOperationUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is PluginOperationUiState.Success -> OperationMessage(message = state.message)
                is PluginOperationUiState.PartialFailure -> OperationMessage(
                    message = state.message,
                    isError = true,
                    onViewDetail = { failureDialog = state.failures },
                )
                is PluginOperationUiState.Failure -> OperationMessage(
                    message = state.message,
                    isError = true,
                    onViewDetail = state.failures.takeIf { it.isNotEmpty() }?.let { failures ->
                        { failureDialog = failures }
                    },
                )
                PluginOperationUiState.Idle -> {}
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
                verticalArrangement = Arrangement.spacedBy(rpx(16)),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("lazy_load_settings") {
                    SettingSectionCard(title = "运行选项") {
                        SettingSwitchRow(
                            title = "懒加载插件",
                            checked = lazyLoadPlugins,
                            enabled = true,
                            onCheckedChange = viewModel::setLazyLoadPlugins,
                        )
                    }
                }

                if (pluginItems.isEmpty() && failedEntries.isEmpty()) {
                    item("empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(rpx(240)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("暂无已安装插件", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                items(
                    items = failedEntries,
                    key = { "failed:${it.filePath ?: it.platform}" },
                ) { entry ->
                    FailedPluginCard(
                        entry = entry,
                        onShowError = { errorPanelTarget = entry },
                        onRetry = {
                            entry.filePath?.let { viewModel.retryEntry(it) }
                        },
                    )
                }
                items(pluginItems, key = { "mounted:${it.info.platform}" }) { item ->
                    PluginCard(
                        item = item,
                        onToggleEnabled = { enabled ->
                            viewModel.togglePluginEnabled(item.info.platform, enabled)
                        },
                        onUpdate = { viewModel.updatePlugin(item.info.platform) },
                        onShare = {
                            item.info.srcUrl?.let { url ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("插件URL", url))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUninstall = { uninstallTarget = item },
                        onSetAlternative = { alternativeTarget = item },
                        onImportMusicItem = { importMusicItemTarget = item },
                        onImportMusicSheet = { importMusicSheetTarget = item },
                        onEditUserVariables = {
                            activeUserVariableSaveRequestId = null
                            viewModel.resetUserVariableSaveState()
                            userVariablesTarget = item
                        },
                        onShowDescription = { descriptionTarget = item },
                    )
                }
            }
        }
    }

    if (showInstallUrlDialog) {
        InstallUrlDialog(
            onDismiss = { showInstallUrlDialog = false },
            onConfirm = { url ->
                showInstallUrlDialog = false
                viewModel.installFromUrl(url)
            },
        )
    }

    if (showInstallLocalDialog) {
        InstallLocalDialog(
            onDismiss = { showInstallLocalDialog = false },
            onConfirm = { path ->
                showInstallLocalDialog = false
                viewModel.installFromFile(path)
            },
            onOpenFileSelector = {
                showInstallLocalDialog = false
                onNavigateToFileSelector()
            },
        )
    }

    if (showUninstallAllConfirm) {
        AlertDialog(
            onDismissRequest = { showUninstallAllConfirm = false },
            title = { Text("确认") },
            text = { Text("确定要卸载所有插件吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showUninstallAllConfirm = false
                    viewModel.uninstallAllPlugins()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallAllConfirm = false }) { Text("取消") }
            },
        )
    }

    uninstallTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("确认") },
            text = { Text("确定要卸载「${target.info.platform}」插件吗？") },
            confirmButton = {
                TextButton(onClick = {
                    uninstallTarget = null
                    viewModel.uninstallPlugin(target.info.platform)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) { Text("取消") }
            },
        )
    }

    descriptionTarget?.let { target ->
        PluginDescriptionDialog(
            item = target,
            onDismiss = { descriptionTarget = null },
        )
    }

    alternativeTarget?.let { source ->
        AlternativePluginDialog(
            source = source,
            candidates = pluginItems.filter {
                it.enabled &&
                    it.info.platform != source.info.platform &&
                    "getMediaSource" in it.info.supportedMethods
            },
            onDismiss = { alternativeTarget = null },
            onConfirm = { targetPlatform ->
                alternativeTarget = null
                viewModel.setAlternativePlugin(source.info.platform, targetPlatform)
            },
        )
    }

    importMusicItemTarget?.let { target ->
        ImportUrlDialog(
            title = "导入单曲",
            onDismiss = { importMusicItemTarget = null },
            onConfirm = { url ->
                importMusicItemTarget = null
                viewModel.importMusicItem(target.info.platform, url)
            },
        )
    }

    importMusicSheetTarget?.let { target ->
        ImportUrlDialog(
            title = "导入歌单",
            onDismiss = { importMusicSheetTarget = null },
            onConfirm = { url ->
                importMusicSheetTarget = null
                viewModel.importMusicSheet(target.info.platform, url)
            },
        )
    }

    userVariablesTarget?.let { target ->
        val variablesFlow = remember(target.info.platform) {
            viewModel.userVariables(target.info.platform)
        }
        val savedVariables by variablesFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
        val requestId = activeUserVariableSaveRequestId
        val saving = userVariableSaveState is UserVariableSaveUiState.Loading &&
            (userVariableSaveState as UserVariableSaveUiState.Loading).requestId == requestId
        val errorMessage = (userVariableSaveState as? UserVariableSaveUiState.Failure)
            ?.takeIf { it.requestId == requestId }
            ?.message
        UserVariablesDialog(
            item = target,
            initialValues = savedVariables,
            saving = saving,
            errorMessage = errorMessage,
            onDismiss = {
                userVariablesTarget = null
                activeUserVariableSaveRequestId = null
                viewModel.resetUserVariableSaveState()
            },
            onConfirm = { values ->
                activeUserVariableSaveRequestId = viewModel.saveUserVariables(target.info.platform, values)
            },
        )
    }

    failureDialog?.let { failures ->
        FailureDetailDialog(
            failures = failures,
            onDismiss = { failureDialog = null },
        )
    }

    errorPanelTarget?.let { entry ->
        PluginErrorPanel(
            entry = entry,
            onDismiss = { errorPanelTarget = null },
            onRetry = {
                entry.filePath?.let { viewModel.retryEntry(it) }
            },
            onUninstall = {
                // Failed entries usually have no platform (parse/missing-platform
                // failed before metadata extraction). Use filePath fallback.
                viewModel.uninstallEntry(
                    filePath = entry.filePath,
                    platform = entry.platform.takeIf { it.isNotBlank() },
                )
            },
            onCopyError = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = buildString {
                    append(reasonLabel(entry.state))
                    appendLine()
                    append(entry.platform)
                    entry.filePath?.let { appendLine(); append(it) }
                    entry.detail?.takeIf { it.isNotBlank() }?.let {
                        appendLine()
                        append(it)
                    }
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("插件错误", text))
                Toast.makeText(context, "已复制错误信息到剪贴板", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (sheetState.visible) {
        ModalBottomSheet(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                viewModel.hideAddToPlaylistSheet()
            },
        ) {
            AddToPlaylistBottomSheetContent(
                playlists = playlists,
                onSelect = { playlist -> viewModel.addImportedItemsToPlaylist(playlist.id) },
                onCreateNew = { showCreatePlaylistDialog = true },
                folderPlusIcon = painterResource(id = CoreR.drawable.ic_folder_plus),
                favoriteCoverIcon = painterResource(id = CoreR.drawable.ic_playlist_favorite_cover),
            )
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                showCreatePlaylistDialog = false
                viewModel.createPlaylistAndImport(name)
            },
        )
    }
}

@Composable
private fun OperationMessage(
    message: String,
    isError: Boolean = false,
    onViewDetail: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (onViewDetail != null) {
            TextButton(onClick = onViewDetail) {
                Text("查看详情")
            }
        }
    }
}

@Composable
private fun FailureDetailDialog(
    failures: List<FailureDetail>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("失败详情") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(rpx(12)),
            ) {
                items(failures) { failure ->
                    Column {
                        failure.pluginName?.let { pluginName ->
                            Text(
                                text = pluginName,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            text = failure.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        failure.source?.let { source ->
                            Text(
                                text = source,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

/**
 * Status badge for one plugin row. Mounted shows a green check; Loading /
 * Initializing show a spinner; Failed shows a clickable red error icon that
 * opens [PluginErrorPanel] via [onShowError].
 *
 * Use stable contentDescription strings so the values survive R8 minification
 * and are stable for accessibility / instrumentation locators.
 */
@Composable
private fun PluginStatusBadge(
    state: PluginState,
    onShowError: () -> Unit,
) {
    Box(
        modifier = Modifier.width(rpx(48)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            PluginState.Mounted -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "已加载",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(rpx(28)),
            )
            PluginState.Initializing, PluginState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(rpx(28)),
                strokeWidth = rpx(3),
            )
            is PluginState.Failed -> IconButton(onClick = onShowError) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "加载失败",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(rpx(28)),
                )
            }
        }
    }
}

/**
 * Compact card for a Failed plugin entry. Surfaces the platform name (best
 * effort, derived from the file name if metadata extraction never succeeded),
 * the localized error reason, and a clickable status badge that opens the
 * [PluginErrorPanel].
 */
@Composable
private fun FailedPluginCard(
    entry: PluginUiEntry,
    onShowError: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowError),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rpx(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.platform,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = reasonLabel(entry.state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = rpx(4)),
                )
                entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = rpx(4)),
                    )
                }
                if (entry.filePath != null) {
                    Row(modifier = Modifier.padding(top = rpx(8))) {
                        AssistChip(onClick = onRetry, label = { Text("重试") })
                    }
                }
            }
            PluginStatusBadge(state = entry.state, onShowError = onShowError)
        }
    }
}

@Composable
private fun PluginCard(
    item: PluginUiItem,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onShare: () -> Unit,
    onUninstall: () -> Unit,
    onSetAlternative: () -> Unit,
    onImportMusicItem: () -> Unit,
    onImportMusicSheet: () -> Unit,
    onEditUserVariables: () -> Unit,
    onShowDescription: () -> Unit,
) {
    val alpha = if (item.enabled) 1f else 0.5f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        Column(modifier = Modifier.padding(rpx(16))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.info.platform,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Mounted entries always show the green check (this card is
                // only rendered for items already in pluginItems = Mounted).
                PluginStatusBadge(state = PluginState.Mounted, onShowError = {})
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            Text(
                text = buildString {
                    item.info.version?.let { append("v$it") }
                    item.info.author?.let {
                        if (isNotEmpty()) append(" · ")
                        append("作者: $it")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = rpx(4)),
            )

            item.alternativePlatform?.let { target ->
                Text(
                    text = if (item.alternativeInvalid) {
                        "音源重定向目标不可用：$target"
                    } else {
                        "该插件实际使用「$target」插件解析音乐的音源"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.alternativeInvalid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = rpx(6)),
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rpx(12)),
                horizontalArrangement = Arrangement.spacedBy(rpx(12)),
                verticalArrangement = Arrangement.spacedBy(rpx(8)),
            ) {
                if (item.canUpdate) {
                    AssistChip(onClick = onUpdate, label = { Text("更新") })
                    AssistChip(onClick = onShare, label = { Text("分享") })
                }
                AssistChip(
                    onClick = onUninstall,
                    label = { Text("卸载") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                    ),
                )
                AssistChip(onClick = onSetAlternative, label = { Text("音源重定向") })
                if (item.canImportMusicItem) {
                    AssistChip(onClick = onImportMusicItem, label = { Text("导入单曲") })
                }
                if (item.canImportMusicSheet) {
                    AssistChip(onClick = onImportMusicSheet, label = { Text("导入歌单") })
                }
                if (item.canEditUserVariables) {
                    AssistChip(onClick = onEditUserVariables, label = { Text("用户变量") })
                }
                if (!item.info.description.isNullOrBlank()) {
                    AssistChip(onClick = onShowDescription, label = { Text("说明") })
                }
            }
        }
    }
}

@Composable
private fun PluginDescriptionDialog(
    item: PluginUiItem,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("插件说明") },
        text = {
            Text(
                text = item.info.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun AlternativePluginDialog(
    source: PluginUiItem,
    candidates: List<PluginUiItem>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var selectedPlatform by remember(
        source.info.platform,
        source.alternativePlatform,
        source.alternativeInvalid,
    ) {
        mutableStateOf(source.alternativePlatform.takeUnless { source.alternativeInvalid })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音源重定向") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AlternativeOptionRow(
                    selected = selectedPlatform == null,
                    label = "无音源重定向",
                    onClick = { selectedPlatform = null },
                )
                candidates.forEach { candidate ->
                    AlternativeOptionRow(
                        selected = selectedPlatform == candidate.info.platform,
                        label = candidate.info.platform,
                        onClick = { selectedPlatform = candidate.info.platform },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedPlatform) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AlternativeOptionRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = rpx(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportUrlDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("链接") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun UserVariablesDialog(
    item: PluginUiItem,
    initialValues: Map<String, String>,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val variables = item.info.userVariables.filter { it.key.isNotBlank() }
    var edited by remember(item.info.platform, variables) { mutableStateOf(false) }
    var values by remember(item.info.platform, variables) {
        mutableStateOf(emptyMap<String, String>())
    }

    LaunchedEffect(item.info.platform, variables, initialValues) {
        if (!edited) {
            values = variables.associate { variable ->
                variable.key to initialValues[variable.key].orEmpty()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置用户变量") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(rpx(12)),
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (saving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                variables.forEach { variable ->
                    OutlinedTextField(
                        value = values[variable.key].orEmpty(),
                        onValueChange = { value ->
                            edited = true
                            values = values + (variable.key to value)
                        },
                        label = { Text(variable.name ?: variable.key) },
                        placeholder = variable.hint?.let { hint -> { Text(hint) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(values) },
                enabled = !saving,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InstallUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从网络安装") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("插件URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text("安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InstallLocalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onOpenFileSelector: () -> Unit,
) {
    var path by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从本地安装") },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("插件文件路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenFileSelector) {
                    Text("打开文件选择器")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(path) }, enabled = path.isNotBlank()) {
                Text("安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
