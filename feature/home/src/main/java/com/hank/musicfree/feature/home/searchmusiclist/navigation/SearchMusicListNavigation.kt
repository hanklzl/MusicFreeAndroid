package com.hank.musicfree.feature.home.searchmusiclist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.SearchMusicListRoute
import com.hank.musicfree.feature.home.searchmusiclist.SearchMusicListScreen

fun NavGraphBuilder.searchMusicListScreen(
    onBack: () -> Unit,
) {
    composable<SearchMusicListRoute> {
        SearchMusicListScreen(
            onBack = onBack,
        )
    }
}
