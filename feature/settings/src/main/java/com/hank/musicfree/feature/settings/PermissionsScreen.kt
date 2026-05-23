package com.hank.musicfree.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.hank.musicfree.core.permissions.requiredAudioPermission
import com.hank.musicfree.core.permissions.requiredNotificationPermission
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.logUiClick

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val storagePermission = requiredAudioPermission()
    val notificationPermission = requiredNotificationPermission()

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.updateUiState(readPermissionsUiState(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.updateUiState(readPermissionsUiState(context))
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.updateUiState(readPermissionsUiState(context))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateUiState(readPermissionsUiState(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    MusicFreeScreenScaffold(
        title = "权限管理",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.PermissionsRoot)
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
            PermissionRowCard(
                title = "悬浮窗权限",
                statusText = permissionStatusText(uiState.overlayGranted),
                actionText = if (uiState.overlayGranted) "已授权" else "去设置",
                actionEnabled = !uiState.overlayGranted,
                onAction = {
                    logUiClick("settings.permissions.overlay", "permissions", "悬浮窗权限")
                    openOverlaySettings(context)
                },
            )
            PermissionRowCard(
                title = "存储/音频读取权限",
                statusText = permissionStatusText(uiState.storageAudioGranted),
                actionText = if (uiState.storageAudioGranted) "已授权" else "请求权限",
                actionEnabled = !uiState.storageAudioGranted,
                onAction = {
                    logUiClick("settings.permissions.storage_audio", "permissions", "存储/音频读取权限")
                    storagePermissionLauncher.launch(storagePermission)
                },
            )
            PermissionRowCard(
                title = "通知权限",
                statusText = permissionStatusText(uiState.notificationGranted),
                actionText = if (uiState.notificationGranted) "已授权" else "请求权限",
                actionEnabled = !uiState.notificationGranted && notificationPermission != null,
                onAction = {
                    logUiClick("settings.permissions.notification", "permissions", "通知权限")
                    notificationPermission?.let(notificationPermissionLauncher::launch)
                },
            )
        }
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    statusText: String,
    actionText: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
                Text(
                    text = statusText,
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(rpx(16)))
            Button(
                onClick = onAction,
                enabled = actionEnabled,
            ) {
                Text(text = actionText)
            }
        }
    }
}

private fun permissionStatusText(granted: Boolean): String {
    return if (granted) "已授权" else "未授权"
}
