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
import com.hank.musicfree.feature.home.albumdetail.navigation.AlbumDetailSeedStore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.StarredSheetRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val route = savedStateHandle.toAlbumDetailRoute()
    private val initialAlbumSeed: AlbumItemBase by lazy { resolveInitialAlbumSeed() }

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var page = 0
    private var currentAlbum: AlbumItemBase? = null

    val isAlbumStarred: StateFlow<Boolean> = starredSheetRepository
        .observeIsStarred(id = route.albumId, platform = route.pluginPlatform)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleAlbumStarred() {
        val seed = currentAlbum ?: initialAlbumSeed
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
            pluginManager.ensurePluginsLoaded()
            loadInitial()
        }
    }

    fun retry() {
        viewModelScope.launch {
            loadInitial()
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val album = currentAlbum
        if (state.loading || state.loadingMore || state.isEnd || album == null) {
            return
        }

        val plugin = pluginManager.getPlugin(route.pluginPlatform) ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true, errorMessage = null)
            runCatching {
                plugin.getAlbumInfo(album, page + 1)
            }.onSuccess { detail ->
                if (detail == null) {
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        errorMessage = "加载专辑失败",
                    )
                    return@onSuccess
                }
                page += 1
                val resolvedAlbum = detail.albumItem ?: album
                currentAlbum = resolvedAlbum
                _uiState.value = _uiState.value.copy(
                    title = resolvedAlbum.title ?: _uiState.value.title,
                    albumItem = resolvedAlbum,
                    musicList = _uiState.value.musicList + detail.musicList,
                    isEnd = detail.isEnd,
                    loadingMore = false,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    errorMessage = e.message ?: "加载专辑失败",
                )
            }
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

    private suspend fun loadInitial() {
        _uiState.value = AlbumDetailUiState(loading = true)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            _uiState.value = AlbumDetailUiState(
                loading = false,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        val seed = initialAlbumSeed
        runCatching {
            plugin.getAlbumInfo(seed, page = 1)
        }.onSuccess { detail ->
            if (detail == null) {
                _uiState.value = AlbumDetailUiState(
                    loading = false,
                    errorMessage = "加载专辑失败",
                )
                return@onSuccess
            }
            page = 1
            val resolvedAlbum = detail.albumItem ?: seed
            currentAlbum = resolvedAlbum
            _uiState.value = AlbumDetailUiState(
                title = resolvedAlbum.title ?: route.title ?: "专辑详情",
                albumItem = resolvedAlbum,
                loading = false,
                musicList = detail.musicList,
                isEnd = detail.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            _uiState.value = AlbumDetailUiState(
                loading = false,
                errorMessage = e.message ?: "加载专辑失败",
            )
        }
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
