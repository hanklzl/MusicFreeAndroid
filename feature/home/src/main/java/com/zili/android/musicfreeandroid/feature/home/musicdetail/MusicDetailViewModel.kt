package com.zili.android.musicfreeandroid.feature.home.musicdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MusicDetailRoute>()

    private val _uiState = MutableStateFlow(MusicDetailUiState())
    val uiState: StateFlow<MusicDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginManager.loadAllPlugins()
            load()
        }
    }

    fun retry() {
        viewModelScope.launch {
            load()
        }
    }

    private suspend fun load() {
        val base = baseMusicItem()
        _uiState.value = MusicDetailUiState(loading = true, musicItem = base)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            _uiState.value = MusicDetailUiState(
                loading = false,
                musicItem = base,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        runCatching {
            val fullMusic = plugin.getMusicInfo(base) ?: base
            val lyric = plugin.getLyric(fullMusic)
            val comments = plugin.getMusicComments(fullMusic, page = 1)

            val albumPreview = fullMusic.album
                ?.takeIf { it.isNotBlank() }
                ?.let { albumTitle ->
                    val albumSeed = AlbumItemBase(
                        id = albumTitle,
                        platform = fullMusic.platform,
                        title = albumTitle,
                        date = null,
                        artist = fullMusic.artist,
                        description = null,
                        artwork = fullMusic.artwork,
                        worksNum = null,
                        raw = mapOf(
                            "id" to albumTitle,
                            "platform" to fullMusic.platform,
                            "title" to albumTitle,
                            "artist" to fullMusic.artist,
                        ),
                    )
                    plugin.getAlbumInfo(albumSeed, page = 1)
                }

            val artistPreview = fullMusic.artist
                .takeIf { it.isNotBlank() }
                ?.let { artistName ->
                    val artistSeed = ArtistItemBase(
                        id = artistName,
                        platform = fullMusic.platform,
                        name = artistName,
                        avatar = fullMusic.artwork,
                        fans = null,
                        description = null,
                        worksNum = null,
                        raw = mapOf(
                            "id" to artistName,
                            "platform" to fullMusic.platform,
                            "name" to artistName,
                        ),
                    )
                    plugin.getArtistWorks(
                        artistItem = artistSeed,
                        page = 1,
                        type = "music",
                    )
                }

            MusicDetailUiState(
                loading = false,
                musicItem = fullMusic,
                lyricLines = lyric?.lines ?: emptyList(),
                comments = comments?.data ?: emptyList(),
                commentsIsEnd = comments?.isEnd ?: true,
                albumPreviewCount = albumPreview?.musicList?.size,
                artistPreviewCount = artistPreview?.musicList?.size,
                errorMessage = null,
            )
        }.onSuccess { state ->
            _uiState.value = state
        }.onFailure { e ->
            _uiState.value = MusicDetailUiState(
                loading = false,
                musicItem = base,
                errorMessage = e.message ?: "加载歌曲详情失败",
            )
        }
    }

    private fun baseMusicItem(): MusicItem {
        return MusicItem(
            id = route.musicId,
            platform = route.pluginPlatform,
            title = route.title,
            artist = route.artist,
            album = route.album,
            duration = route.durationMs,
            url = null,
            artwork = route.artwork,
            qualities = null,
        )
    }
}
