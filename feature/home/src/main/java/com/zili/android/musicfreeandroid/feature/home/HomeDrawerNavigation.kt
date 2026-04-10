package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

@Suppress("TooGenericExceptionCaught")
suspend fun runHomeDrawerNavigation(
    navigate: () -> Unit,
    closeDrawer: suspend () -> Unit,
) {
    navigate()
    runCatching {
        closeDrawer()
    }
}

sealed interface HomeDrawerAction {
    data object OpenSettings : HomeDrawerAction
    data object OpenPluginManagement : HomeDrawerAction
    data object OpenPermissions : HomeDrawerAction
}

data class HomeDrawerEntryUiModel(
    val title: String,
    val icon: ImageVector,
    val anchorTag: String,
    val action: HomeDrawerAction,
)

data class HomeDrawerUiModel(
    val title: String,
    val entries: List<HomeDrawerEntryUiModel>,
)

fun buildHomeDrawerUiModel(): HomeDrawerUiModel = HomeDrawerUiModel(
    title = "更多功能",
    entries = listOf(
        HomeDrawerEntryUiModel(
            title = "基础设置",
            icon = Icons.Default.Settings,
            anchorTag = FidelityAnchors.Home.DrawerSettings,
            action = HomeDrawerAction.OpenSettings,
        ),
        HomeDrawerEntryUiModel(
            title = "插件管理",
            icon = Icons.Default.Extension,
            anchorTag = FidelityAnchors.Home.DrawerPluginManagement,
            action = HomeDrawerAction.OpenPluginManagement,
        ),
        HomeDrawerEntryUiModel(
            title = "权限管理",
            icon = Icons.Default.Security,
            anchorTag = FidelityAnchors.Home.DrawerPermissions,
            action = HomeDrawerAction.OpenPermissions,
        ),
    ),
)
