package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

suspend fun runHomeDrawerNavigation(
    navigate: () -> Unit,
    closeDrawer: suspend () -> Unit,
) {
    navigate()
    runCatching {
        closeDrawer()
    }
}

data class HomeDrawerDestination(
    val title: String,
    val icon: ImageVector,
    val anchorTag: String,
    val navigate: () -> Unit,
)

fun buildHomeDrawerDestinations(
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
): List<HomeDrawerDestination> = listOf(
    HomeDrawerDestination(
        title = "基础设置",
        icon = Icons.Default.Settings,
        anchorTag = FidelityAnchors.Home.DrawerSettings,
        navigate = onNavigateToSettings,
    ),
    HomeDrawerDestination(
        title = "插件管理",
        icon = Icons.Default.Extension,
        anchorTag = FidelityAnchors.Home.DrawerPluginManagement,
        navigate = onNavigateToSettings,
    ),
    HomeDrawerDestination(
        title = "权限管理",
        icon = Icons.Default.Security,
        anchorTag = FidelityAnchors.Home.DrawerPermissions,
        navigate = onNavigateToPermissions,
    ),
)
