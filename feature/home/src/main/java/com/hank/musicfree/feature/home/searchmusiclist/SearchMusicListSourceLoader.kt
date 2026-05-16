package com.hank.musicfree.feature.home.searchmusiclist

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.navigation.SearchMusicListRoute
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.feature.home.collection.CollectionSource
import com.hank.musicfree.feature.home.collection.toCollectionSource
import com.hank.musicfree.player.controller.PlayerController
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
