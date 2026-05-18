package com.hank.musicfree.feature.home.artistdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.navigation.ArtistDetailRoute
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.feature.home.artistdetail.navigation.ArtistDetailSeedStore
import com.hank.musicfree.feature.home.runtime.DetailRouteTypes
import com.hank.musicfree.feature.home.runtime.DetailSessionEntry
import com.hank.musicfree.feature.home.runtime.DetailSessionHeader
import com.hank.musicfree.feature.home.runtime.DetailSessionRequest
import com.hank.musicfree.feature.home.runtime.DetailSessionStore
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.ArtistItemBase
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val mediaSourceResolver: MediaSourceResolver,
    private val appPreferences: AppPreferences,
    private val detailSessionStore: DetailSessionStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ArtistDetailRoute>()
    private val initialArtistSeed: ArtistItemBase by lazy { resolveInitialArtistSeed() }
    private val detailKey = RuntimeStoreKey.detail(
        DetailRouteTypes.ARTIST,
        route.pluginPlatform,
        route.artistId,
    ).value
    private val detailRequest: DetailSessionRequest by lazy {
        DetailSessionRequest(
            key = detailKey,
            routeType = DetailRouteTypes.ARTIST,
            platform = route.pluginPlatform,
            itemId = route.artistId,
            seed = DetailSessionHeader.Artist(initialArtistSeed),
            fallbackTitle = route.name,
        )
    }

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            detailSessionStore.state
                .map { it.sessions[detailKey].toArtistUiState(route.name) }
                .collect { _uiState.value = it }
        }
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
            loadInitial()
        }
    }

    fun retry() {
        viewModelScope.launch {
            loadInitial(forceRefresh = true)
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            detailSessionStore.loadMore(detailKey)
        }
    }

    suspend fun playAt(index: Int): Boolean {
        val list = _uiState.value.musicList
        if (index !in list.indices) {
            return false
        }

        val clicked = list[index]
        val resolved = if (clicked.url.isNullOrBlank()) {
            mediaSourceResolver.resolve(clicked)?.item ?: return false
        } else {
            clicked
        }

        val queue = list.toMutableList()
        queue[index] = resolved
        when (appPreferences.clickMusicInAlbum.first()) {
            AlbumMusicClickAction.PlayMusic -> playerController.playItem(resolved)
            AlbumMusicClickAction.PlayAlbum -> playerController.playQueue(queue, index)
        }
        return true
    }

    private suspend fun loadInitial(forceRefresh: Boolean = false) {
        detailSessionStore.loadInitial(detailRequest, forceRefresh = forceRefresh)
    }

    private fun resolveInitialArtistSeed(): ArtistItemBase {
        ArtistDetailSeedStore.take(route.seedToken)?.let { return it }

        val raw = mutableMapOf<String, Any?>(
            "id" to route.artistId,
            "platform" to route.pluginPlatform,
            "name" to route.name,
        )
        route.avatar?.let { raw["avatar"] = it }
        route.description?.let { raw["description"] = it }
        route.fans?.let { raw["fans"] = it }
        route.worksNum?.let { raw["worksNum"] = it }

        return ArtistItemBase(
            id = route.artistId,
            platform = route.pluginPlatform,
            name = route.name,
            avatar = route.avatar,
            fans = route.fans,
            description = route.description,
            worksNum = route.worksNum,
            raw = raw,
        )
    }
}

private fun DetailSessionEntry?.toArtistUiState(fallbackTitle: String): ArtistDetailUiState {
    if (this == null) return ArtistDetailUiState(loading = true)
    return ArtistDetailUiState(
        title = header.title ?: fallbackTitle,
        loading = loading,
        musicList = items,
        isEnd = isEnd,
        loadingMore = loadingMore,
        errorMessage = errorMessage,
    )
}
