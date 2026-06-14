package com.hank.musicfree.feature.settings.cachemanagement.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.CacheManagementRoute
import com.hank.musicfree.feature.settings.cachemanagement.CacheManagementScreen

fun NavGraphBuilder.cacheManagementScreen(
    onBack: () -> Unit,
) {
    composable<CacheManagementRoute> {
        CacheManagementScreen(onBack = onBack)
    }
}
