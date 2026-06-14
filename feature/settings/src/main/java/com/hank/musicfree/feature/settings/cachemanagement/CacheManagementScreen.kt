package com.hank.musicfree.feature.settings.cachemanagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.logUiClick
import com.hank.musicfree.feature.settings.components.SettingSectionCard

@Composable
fun CacheManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CacheManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }

    MusicFreeScreenScaffold(
        title = "缓存管理",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.CacheManagementRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = rpx(24))
                .padding(top = rpx(24)),
            verticalArrangement = Arrangement.spacedBy(rpx(16)),
        ) {
            SettingSectionCard("指定歌曲") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(24), vertical = rpx(16)),
                    verticalArrangement = Arrangement.spacedBy(rpx(16)),
                ) {
                    OutlinedTextField(
                        value = state.platform,
                        onValueChange = viewModel::onPlatformChange,
                        label = { Text("平台") },
                        singleLine = true,
                        enabled = !state.isClearing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.itemId,
                        onValueChange = viewModel::onItemIdChange,
                        label = { Text("歌曲 ID") },
                        singleLine = true,
                        enabled = !state.isClearing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            logUiClick(
                                targetId = "settings.cache_management.clear_song",
                                screen = "cache_management",
                                targetLabel = "清理指定歌曲",
                                extra = mapOf(
                                    "platform" to state.platform.trim(),
                                    "itemId" to state.itemId.trim(),
                                ),
                            )
                            showConfirmDialog = true
                        },
                        enabled = state.canClear,
                    ) {
                        Text(if (state.isClearing) "清理中" else "清理指定歌曲")
                    }
                    state.message?.let { message ->
                        Text(
                            text = message,
                            fontSize = FontSizes.description,
                            color = MusicFreeTheme.colors.primary,
                        )
                    }
                    state.errorMessage?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            fontSize = FontSizes.description,
                            color = MusicFreeTheme.colors.danger,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(rpx(8)))
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("清理歌曲缓存") },
            text = { Text("将清理该歌曲的播放缓存和本地播放关联。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.clearSpecifiedSongCache()
                    },
                ) {
                    Text("清理")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
