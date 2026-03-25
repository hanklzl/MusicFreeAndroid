package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.data.repository.StarredSheetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeSheetsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val starredSheetRepository: StarredSheetRepository,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(HomeSheetTab.Mine)

    val uiState: StateFlow<HomeSheetsUiState> = combine(
        playlistRepository.observeAllPlaylists(),
        starredSheetRepository.observeAll(),
        selectedTab,
    ) { playlists, starredSheets, tab ->
        val mineRows = playlists.map { playlist ->
            HomeSheetUiModel.fromPlaylist(playlist = playlist, musicCount = 0)
        }
        val starredRows = starredSheets.map { sheet ->
            HomeSheetUiModel.fromStarredSheet(sheet)
        }
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
}
