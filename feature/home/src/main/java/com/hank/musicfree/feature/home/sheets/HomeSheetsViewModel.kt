package com.hank.musicfree.feature.home.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.data.repository.StarredSheetRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeSheetsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val starredSheetRepository: StarredSheetRepository,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(HomeSheetTab.Mine)

    private val mineRows = playlistRepository.observeAllPlaylists().map { playlists ->
        playlists.map { playlist ->
            HomeSheetUiModel.fromPlaylist(
                playlist = playlist,
                musicCount = playlistRepository.countMusicInPlaylist(playlist.id),
            )
        }
    }

    private val starredRows = starredSheetRepository.observeAll().map { sheets ->
        sheets.map(HomeSheetUiModel::fromStarredSheet)
    }

    val uiState: StateFlow<HomeSheetsUiState> = combine(
        mineRows,
        starredRows,
        selectedTab,
    ) { mineRows, starredRows, tab ->
        HomeSheetsUiState(
            selectedTab = tab,
            mineCount = mineRows.size,
            starredCount = starredRows.size,
            items = if (tab == HomeSheetTab.Mine) mineRows else starredRows,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeSheetsUiState(),
    )

    fun selectTab(tab: HomeSheetTab) {
        selectedTab.value = tab
    }

    fun unstar(item: HomeSheetUiModel) {
        val platform = item.platform ?: return
        viewModelScope.launch {
            starredSheetRepository.deleteByIdAndPlatform(id = item.id, platform = platform)
            MfLog.detail(
                category = LogCategory.APP,
                event = "starred_removed",
                fields = mapOf(
                    "kind" to item.kind,
                    "platform" to platform,
                    "id" to item.id,
                    "title" to item.title,
                    "source" to "home_starred_trash",
                ),
            )
        }
    }
}
