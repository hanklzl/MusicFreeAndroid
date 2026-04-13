package com.zili.android.musicfreeandroid.feature.settings.pluginlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.rpx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginListScreen(
    onBack: () -> Unit,
    onNavigateToPluginSort: () -> Unit,
    onNavigateToPluginSubscription: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginListViewModel = hiltViewModel(),
) {
    val pluginItems by viewModel.pluginItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showInstallUrlDialog by remember { mutableStateOf(false) }
    var showInstallLocalDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showUninstallAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
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
            )
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
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (val state = installState) {
                is InstallState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is InstallState.Success -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(8)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is InstallState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(8)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                InstallState.Idle -> {}
            }

            if (pluginItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无已安装插件", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
                    verticalArrangement = Arrangement.spacedBy(rpx(16)),
                ) {
                    items(pluginItems, key = { it.info.platform }) { item ->
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
                            onUninstall = { viewModel.uninstallPlugin(item.info.platform) },
                        )
                    }
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
}

@Composable
private fun PluginCard(
    item: PluginUiItem,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onShare: () -> Unit,
    onUninstall: () -> Unit,
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

            Row(
                modifier = Modifier.padding(top = rpx(12)),
                horizontalArrangement = Arrangement.spacedBy(rpx(12)),
            ) {
                if (item.info.srcUrl != null) {
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
            }
        }
    }
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
