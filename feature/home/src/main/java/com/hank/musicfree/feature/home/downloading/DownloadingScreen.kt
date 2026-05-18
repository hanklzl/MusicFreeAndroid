package com.hank.musicfree.feature.home.downloading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.downloader.model.DownloadFailReason
import com.hank.musicfree.downloader.model.DownloadStatus
import com.hank.musicfree.downloader.model.DownloadTaskUi
import java.util.Locale

@Composable
fun DownloadingScreen(
    onBack: () -> Unit,
    viewModel: DownloadingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    MusicFreeScreenScaffold(
        title = "下载",
        onBack = onBack,
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("全部重试失败项") },
                    onClick = { viewModel.retryAllFailed(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("清空失败项") },
                    onClick = { viewModel.clearFailed(); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("取消所有进行中") },
                    onClick = { viewModel.cancelAllInflight(); menuOpen = false },
                )
            }
        },
    ) { padding ->
        if (state.active.isEmpty() && state.failed.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无下载任务")
                    Text(
                        "在歌曲长按菜单或歌曲详情页可触发下载",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (state.active.isNotEmpty()) {
                    item { SectionHeader("进行中（${state.active.size}）") }
                    items(state.active, key = { "active-${it.key.value}" }) { task ->
                        ActiveRow(task, onCancel = { viewModel.cancel(task.key) })
                    }
                }
                if (state.failed.isNotEmpty()) {
                    item { SectionHeader("失败（${state.failed.size}）") }
                    items(state.failed, key = { "failed-${it.key.value}" }) { task ->
                        FailedRow(task, onRetry = { viewModel.retry(task.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun ActiveRow(task: DownloadTaskUi, onCancel: () -> Unit) {
    val pct = task.totalBytes?.takeIf { it > 0 }?.let {
        ((task.downloadedBytes ?: 0L) * 100 / it).toInt()
    } ?: 0
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (task.status) {
                        DownloadStatus.PENDING -> "等待中"
                        DownloadStatus.PREPARING -> "准备中"
                        DownloadStatus.DOWNLOADING -> "下载中  ${formatSize(task.downloadedBytes)} / ${formatSize(task.totalBytes)}"
                        DownloadStatus.FAILED -> "失败"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "取消")
            }
        }
        if (task.status == DownloadStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FailedRow(task: DownloadTaskUi, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when (task.errorReason) {
                    DownloadFailReason.FailToFetchSource -> "下载失败：无法获取源"
                    DownloadFailReason.NoWritePermission -> "下载失败：没有写入权限"
                    DownloadFailReason.NotAllowToDownloadInCellular -> "已暂停：未允许移动网络下载"
                    DownloadFailReason.NetworkOffline -> "无网络"
                    else -> "下载失败：未知原因"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = "重试")
        }
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "-"
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.ROOT, "%.1fMB", mb)
}
