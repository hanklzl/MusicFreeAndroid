package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListSource
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchMusicListSourceLoader @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
) {
    fun observe(source: SearchMusicListSource): Flow<List<MusicItem>> =
        when (source) {
            SearchMusicListSource.History -> playerController.playHistory
            is SearchMusicListSource.Playlist -> playlistRepository.observeMusicInPlaylist(source.playlistId)
        }
}
