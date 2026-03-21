package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListSource
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchMusicListSourceLoader @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
) {
    fun observe(route: SearchMusicListRoute): Flow<List<MusicItem>> = observe(route.toSource())

    fun observe(source: SearchMusicListSource): Flow<List<MusicItem>> =
        when (source) {
            SearchMusicListSource.History -> playerController.playHistory
            is SearchMusicListSource.Playlist -> playlistRepository.observeMusicInPlaylist(source.playlistId)
        }

    private fun SearchMusicListRoute.toSource(): SearchMusicListSource =
        when (sourceType) {
            SearchMusicListRoute.SOURCE_TYPE_HISTORY -> SearchMusicListSource.History
            SearchMusicListRoute.SOURCE_TYPE_PLAYLIST -> SearchMusicListSource.Playlist(
                playlistId = requireNotNull(playlistId),
            )
            else -> error("Unsupported search music list source type: $sourceType")
        }
}
