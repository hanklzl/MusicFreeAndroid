package com.zili.android.musicfreeandroid.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.feature.search.SearchScreen
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onOpenAlbumDetail: (AlbumItemBase) -> Unit,
    onOpenArtistDetail: (ArtistItemBase) -> Unit,
    onOpenSheetDetail: (MusicSheetItemBase) -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(
            onBack = onBack,
            onOpenAlbumDetail = onOpenAlbumDetail,
            onOpenArtistDetail = onOpenArtistDetail,
            onOpenSheetDetail = onOpenSheetDetail,
        )
    }
}
