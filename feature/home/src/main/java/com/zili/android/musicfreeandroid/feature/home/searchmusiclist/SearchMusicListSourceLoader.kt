package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.feature.home.collection.CollectionSource
import com.zili.android.musicfreeandroid.feature.home.collection.toCollectionSource
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SearchMusicListSourceLoader @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
    private val musicRepository: MusicRepository,
) {
    fun observe(route: SearchMusicListRoute): Flow<List<MusicItem>> = observe(route.toCollectionSource())

    fun observe(source: CollectionSource): Flow<List<MusicItem>> =
        when (source) {
            CollectionSource.History -> playerController.playHistory
            CollectionSource.LocalLibrary -> musicRepository.observeLocalLibrary()
            is CollectionSource.Playlist -> playlistRepository.observeMusicInPlaylist(source.playlistId)
            is CollectionSource.Transient -> flowOf(emptyList())
        }
}
