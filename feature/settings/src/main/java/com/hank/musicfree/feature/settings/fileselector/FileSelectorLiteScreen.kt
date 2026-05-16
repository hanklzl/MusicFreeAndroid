package com.hank.musicfree.feature.settings.fileselector

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.storage.DocumentTreeStorageAccess
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold

@Composable
fun FileSelectorLiteScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FileSelectorLiteViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val previousUri = uiState.selectedDirectory?.uri
            runCatching {
                DocumentTreeStorageAccess.persistReadWritePermission(
                    contentResolver = context.contentResolver,
                    treeUri = uri,
                )
            }.onSuccess {
                if (previousUri != null && previousUri != uri) {
                    DocumentTreeStorageAccess.releaseReadWritePermission(
                        contentResolver = context.contentResolver,
                        treeUri = previousUri,
                    )
                }
                viewModel.onDirectorySelected(uri.toString())
                onBack()
            }
        }
    }

    MusicFreeScreenScaffold(
        title = "选择存储目录",
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = rpx(24)),
        ) {
            item {
                Spacer(modifier = Modifier.height(rpx(24)))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(rpx(16)),
                    colors = CardDefaults.cardColors(
                        containerColor = MusicFreeTheme.colors.card,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rpx(24), vertical = rpx(20)),
                    ) {
                        Text(
                            text = "当前目录",
                            fontSize = FontSizes.content,
                            color = MusicFreeTheme.colors.text,
                        )
                        Spacer(modifier = Modifier.height(rpx(8)))
                        Text(
                            text = fileSelectorDescription(uiState),
                            fontSize = FontSizes.description,
                            color = MusicFreeTheme.colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(rpx(16)))
                        Button(
                            onClick = {
                                openDocumentTreeLauncher.launch(uiState.selectedDirectory?.uri)
                            },
                        ) {
                            Text(text = if (uiState.isConfigured) "更换目录" else "选择目录")
                        }
                    }
                }
            }
        }
    }
}

private fun fileSelectorDescription(state: FileSelectorLiteUiState): String {
    return state.selectedDirectory?.let { "已配置目录：${it.displayName}" } ?: "未配置目录"
}
