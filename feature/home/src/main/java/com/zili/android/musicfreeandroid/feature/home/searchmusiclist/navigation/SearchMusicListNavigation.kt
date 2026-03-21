package com.zili.android.musicfreeandroid.feature.home.searchmusiclist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.feature.home.searchmusiclist.SearchMusicListScreen

fun NavGraphBuilder.searchMusicListScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    composable<SearchMusicListRoute> {
        SearchMusicListScreen(
            onBack = onBack,
            onNavigateToPlayer = onNavigateToPlayer,
        )
    }
}
