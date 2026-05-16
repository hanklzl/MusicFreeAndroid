package com.hank.musicfree.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.SearchRoute
import com.hank.musicfree.feature.search.SearchScreen
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.ArtistItemBase
import com.hank.musicfree.plugin.api.MusicSheetItemBase

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
