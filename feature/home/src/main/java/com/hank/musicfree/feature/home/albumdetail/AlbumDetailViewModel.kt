package com.hank.musicfree.feature.home.albumdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.navigation.AlbumDetailRoute
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.feature.home.albumdetail.navigation.AlbumDetailSeedStore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.StarredSheetRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.home.runtime.DetailRouteTypes
import com.hank.musicfree.feature.home.runtime.DetailSessionEntry
import com.hank.musicfree.feature.home.runtime.DetailSessionHeader
import com.hank.musicfree.feature.home.runtime.DetailSessionRequest
import com.hank.musicfree.feature.home.runtime.DetailSessionStore
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
    private val mediaSourceResolver: MediaSourceResolver,
    private val starredSheetRepository: StarredSheetRepository,
    private val detailSessionStore: DetailSessionStore,
) : ViewModel() {

    private val route = savedStateHandle.toAlbumDetailRoute()
    private val initialAlbumSeed: AlbumItemBase by lazy { resolveInitialAlbumSeed() }
    private val detailKey = RuntimeStoreKey.detail(
        DetailRouteTypes.ALBUM,
        route.pluginPlatform,
        route.albumId,
    ).value
    private val detailRequest: DetailSessionRequest by lazy {
        DetailSessionRequest(
            key = detailKey,
            routeType = DetailRouteTypes.ALBUM,
            platform = route.pluginPlatform,
            itemId = route.albumId,
            seed = DetailSessionHeader.Album(initialAlbumSeed),
            fallbackTitle = route.title ?: "专辑详情",
        )
    }

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    val isAlbumStarred: StateFlow<Boolean> = starredSheetRepository
        .observeIsStarred(id = route.albumId, platform = route.pluginPlatform)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleAlbumStarred() {
        val seed = currentAlbum() ?: initialAlbumSeed
        val starred = seed.toStarredSheet()
        val wasStarred = isAlbumStarred.value
        viewModelScope.launch {
            starredSheetRepository.toggle(starred)
            com.hank.musicfree.logging.MfLog.detail(
                category = com.hank.musicfree.logging.LogCategory.APP,
                event = if (wasStarred) "starred_removed" else "starred_added",
                fields = mapOf(
                    "kind" to com.hank.musicfree.core.model.StarredKind.ALBUM,
                    "platform" to starred.platform,
                    "id" to starred.id,
                    "title" to starred.title,
                    "source" to "detail_album",
                ),
            )
        }
    }

    init {
        viewModelScope.launch {
            detailSessionStore.state
                .map { it.sessions[detailKey].toAlbumUiState(initialAlbumSeed, route.title ?: "专辑详情") }
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

    fun playAll() {
        val list = _uiState.value.musicList
        if (list.isEmpty()) {
            return
        }
        viewModelScope.launch {
            playAt(0)
        }
    }

    private fun resolveInitialAlbumSeed(): AlbumItemBase {
        AlbumDetailSeedStore.take(route.seedToken)?.let { return it }

        val raw = mutableMapOf<String, Any?>(
            "id" to route.albumId,
            "platform" to route.pluginPlatform,
        )
        route.title?.let { raw["title"] = it }
        route.artist?.let { raw["artist"] = it }
        route.artwork?.let { raw["artwork"] = it }
        route.date?.let { raw["date"] = it }
        route.description?.let { raw["description"] = it }
        route.worksNum?.let { raw["worksNum"] = it }

        return AlbumItemBase(
            id = route.albumId,
            platform = route.pluginPlatform,
            title = route.title,
            date = route.date,
            artist = route.artist,
            description = route.description,
            artwork = route.artwork,
            worksNum = route.worksNum,
            raw = raw,
        )
    }

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }

    private fun currentAlbum(): AlbumItemBase? =
        (detailSessionStore.session(detailKey)?.header as? DetailSessionHeader.Album)?.item
}

private fun DetailSessionEntry?.toAlbumUiState(seed: AlbumItemBase, fallbackTitle: String): AlbumDetailUiState {
    if (this == null) return AlbumDetailUiState(loading = true)
    val album = (header as? DetailSessionHeader.Album)?.item ?: seed
    return AlbumDetailUiState(
        title = album.title ?: fallbackTitle,
        albumItem = album,
        loading = loading,
        musicList = items,
        isEnd = isEnd,
        loadingMore = loadingMore,
        errorMessage = errorMessage,
    )
}

private fun SavedStateHandle.toAlbumDetailRoute(): AlbumDetailRoute {
    val pluginPlatform = get<String>("pluginPlatform")
    val albumId = get<String>("albumId")
    if (pluginPlatform != null && albumId != null) {
        return AlbumDetailRoute(
            pluginPlatform = pluginPlatform,
            albumId = albumId,
            title = get("title"),
            artist = get("artist"),
            artwork = get("artwork"),
            date = get("date"),
            description = get("description"),
            worksNum = get("worksNum"),
            seedToken = get("seedToken"),
        )
    }
    return toRoute()
}
