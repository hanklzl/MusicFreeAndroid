package com.zili.android.musicfreeandroid.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.feature.search.SearchScreen

fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(
            onBack = onBack,
            onNavigateToPlayer = onNavigateToPlayer,
        )
    }
}
