package com.zili.android.musicfreeandroid.feature.home.musicdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MusicDetailRoute>()

    private val _uiState = MutableStateFlow(MusicDetailUiState())
    val uiState: StateFlow<MusicDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
            load()
        }
    }

    fun retry() {
        viewModelScope.launch {
            load()
        }
    }

    fun download(quality: PlayQuality) {
        val item = _uiState.value.musicItem ?: return
        downloader.enqueue(listOf(item), quality)
    }

    suspend fun preferredDownloadQuality(): PlayQuality = appPreferences.defaultDownloadQuality.first()

    private suspend fun load() {
        val base = MusicDetailSeedResolver.baseMusicItem(route)
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

            val albumPreview = MusicDetailSeedResolver.albumPreviewSeed(fullMusic)?.let { albumSeed ->
                plugin.getAlbumInfo(albumSeed, page = 1)
            }

            val artistPreview = MusicDetailSeedResolver.artistPreviewSeed(fullMusic)?.let { artistSeed ->
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

}
