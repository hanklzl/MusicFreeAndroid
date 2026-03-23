package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val storageAccessState by viewModel.storageAccessState.collectAsStateWithLifecycle()
    var showInstallDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontSize = FontSizes.appBar,
                        color = MusicFreeTheme.colors.appBarText,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MusicFreeTheme.colors.appBarText,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MusicFreeTheme.colors.appBar,
                ),
            )
        },
        containerColor = MusicFreeTheme.colors.pageBackground,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = rpx(24)),
        ) {
            item {
                Spacer(modifier = Modifier.height(rpx(24)))
                SettingsEntryCard(
                    title = "权限管理",
                    description = "管理悬浮窗和存储/音频读取权限",
                    actionText = "进入",
                    onClick = onNavigateToPermissions,
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "默认订阅导入",
                    description = "导入真实订阅中的插件列表，用于验证搜索与播放链路",
                    actionText = "导入",
                    enabled = installState !is InstallState.Loading,
                    onClick = viewModel::installDefaultSubscription,
                )
                InstallStateSummary(
                    installState = installState,
                    modifier = Modifier.padding(top = rpx(12)),
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "存储目录",
                    description = storageDirectoryDescription(storageAccessState),
                    actionText = if (storageAccessState.isConfigured) "更换" else "选择",
                    onClick = onNavigateToFileSelector,
                )
            }

            // Section header: 插件管理
            item {
                Spacer(modifier = Modifier.height(rpx(24)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "插件管理",
                        fontSize = FontSizes.title,
                        color = MusicFreeTheme.colors.text,
                    )
                    IconButton(onClick = { showInstallDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加插件",
                            tint = MusicFreeTheme.colors.primary,
                        )
                    }
                }
            }

            // Empty state
            if (plugins.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = rpx(48)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无已安装插件",
                            fontSize = FontSizes.content,
                            color = MusicFreeTheme.colors.textSecondary,
                        )
                    }
                }
            }

            // Plugin list
            items(plugins, key = { it.platform }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    onDelete = { viewModel.uninstallPlugin(plugin.platform) },
                )
                Spacer(modifier = Modifier.height(rpx(16)))
            }
        }
    }

    if (showInstallDialog) {
        InstallPluginDialog(
            installState = installState,
            onInstall = { url -> viewModel.installFromUrl(url) },
            onDismiss = {
                showInstallDialog = false
                viewModel.resetInstallState()
            },
        )
    }
}

private fun storageDirectoryDescription(state: StorageAccessState): String {
    val prefix = if (state.isConfigured) {
        "已配置目录：${state.selectedDirectory?.displayName}"
    } else {
        "未配置目录"
    }
    return "$prefix，用于后续下载、备份和本地导入能力。"
}

@Composable
private fun InstallStateSummary(
    installState: InstallState,
    modifier: Modifier = Modifier,
) {
    when (installState) {
        is InstallState.Loading -> {
            LinearProgressIndicator(
                modifier = modifier.fillMaxWidth(),
                color = MusicFreeTheme.colors.primary,
            )
        }
        is InstallState.Error -> {
            Text(
                text = installState.message,
                modifier = modifier,
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.danger,
            )
        }
        is InstallState.Success -> {
            Text(
                text = installState.message,
                modifier = modifier,
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.success,
            )
        }
        InstallState.Idle -> Unit
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    description: String,
    actionText: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rpx(16)),
        colors = CardDefaults.cardColors(
            containerColor = MusicFreeTheme.colors.card,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rpx(24), vertical = rpx(20)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
                Spacer(modifier = Modifier.height(rpx(6)))
                Text(
                    text = description,
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
            TextButton(onClick = onClick, enabled = enabled) {
                Text(text = actionText)
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginInfo,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rpx(16)),
        colors = CardDefaults.cardColors(
            containerColor = MusicFreeTheme.colors.card,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rpx(24), vertical = rpx(20)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.platform,
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
                Spacer(modifier = Modifier.height(rpx(8)))
                val subtitle = buildString {
                    plugin.author?.let { append(it) }
                    plugin.version?.let {
                        if (isNotEmpty()) append(" · ")
                        append("v$it")
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = FontSizes.description,
                        color = MusicFreeTheme.colors.textSecondary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除插件",
                    tint = MusicFreeTheme.colors.danger,
                )
            }
        }
    }
}

@Composable
private fun InstallPluginDialog(
    installState: InstallState,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "安装插件",
                fontSize = FontSizes.title,
                color = MusicFreeTheme.colors.text,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("插件 URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = installState !is InstallState.Loading,
                )
                Spacer(modifier = Modifier.height(8.dp))
                InstallStateSummary(installState = installState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInstall(url) },
                enabled = installState !is InstallState.Loading,
            ) {
                Text(
                    text = "安装",
                    color = MusicFreeTheme.colors.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
        },
    )
}
