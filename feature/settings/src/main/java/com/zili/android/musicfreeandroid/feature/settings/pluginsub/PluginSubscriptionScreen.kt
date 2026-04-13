package com.zili.android.musicfreeandroid.feature.settings.pluginsub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.plugin.meta.SubscriptionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSubscriptionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSubscriptionViewModel = hiltViewModel(),
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var dialogName by remember { mutableStateOf("") }
    var dialogUrl by remember { mutableStateOf("") }

    fun openAddDialog() {
        editingIndex = -1
        dialogName = ""
        dialogUrl = ""
        showDialog = true
    }

    fun openEditDialog(index: Int, item: SubscriptionItem) {
        editingIndex = index
        dialogName = item.name
        dialogUrl = item.url
        showDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订阅设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "添加订阅")
            }
        },
        modifier = modifier,
    ) { padding ->
        if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无订阅源\n点击右下角添加",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
                verticalArrangement = Arrangement.spacedBy(rpx(8)),
                modifier = Modifier.padding(padding),
            ) {
                itemsIndexed(subscriptions) { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openEditDialog(index, item) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(rpx(16)),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = rpx(4)),
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("订阅URL", item.url))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = "复制URL",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        val isEditing = editingIndex >= 0
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (isEditing) "编辑订阅" else "添加订阅") },
            text = {
                Column {
                    OutlinedTextField(
                        value = dialogName,
                        onValueChange = { dialogName = it },
                        label = { Text("订阅名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(rpx(12)))
                    OutlinedTextField(
                        value = dialogUrl,
                        onValueChange = { dialogUrl = it },
                        label = { Text("订阅URL") },
                        placeholder = { Text("输入 .js 或 .json 地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isEditing) {
                            viewModel.updateSubscription(editingIndex, dialogName.trim(), dialogUrl.trim())
                        } else {
                            viewModel.addSubscription(dialogName.trim(), dialogUrl.trim())
                        }
                        showDialog = false
                    },
                    enabled = dialogName.isNotBlank() && dialogUrl.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (isEditing) {
                        TextButton(onClick = {
                            viewModel.removeSubscription(editingIndex)
                            showDialog = false
                        }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDialog = false }) { Text("取消") }
                }
            },
        )
    }
}
