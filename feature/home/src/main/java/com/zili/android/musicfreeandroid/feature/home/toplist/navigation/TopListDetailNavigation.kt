package com.zili.android.musicfreeandroid.feature.home.toplist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.feature.home.toplist.TopListDetailScreen

fun NavGraphBuilder.topListDetailScreen(
    onBack: () -> Unit,
    onOpenMusicDetail: (MusicItem) -> Unit,
) {
    composable<TopListDetailRoute> {
        TopListDetailScreen(
            onBack = onBack,
            onOpenMusicDetail = onOpenMusicDetail,
        )
    }
}
