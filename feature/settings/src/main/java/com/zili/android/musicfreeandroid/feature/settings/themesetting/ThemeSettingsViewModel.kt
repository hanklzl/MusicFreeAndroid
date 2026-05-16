package com.zili.android.musicfreeandroid.feature.settings.themesetting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.theme.DarkMusicFreeColors
import com.zili.android.musicfreeandroid.core.theme.runtime.SelectedTheme
import com.zili.android.musicfreeandroid.core.theme.runtime.ThemeRepository
import com.zili.android.musicfreeandroid.core.theme.runtime.ThemeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {
    val state: StateFlow<ThemeUiState> = themeRepository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ThemeUiState(
            selected = SelectedTheme.P_DARK,
            effectiveColors = DarkMusicFreeColors,
            background = null,
            followSystem = false,
            isLoading = true,
        ),
    )

    fun onFollowSystemToggle(enabled: Boolean, systemDark: Boolean) {
        viewModelScope.launch { themeRepository.setFollowSystem(enabled, systemDark) }
    }

    fun onSelectTheme(theme: SelectedTheme) {
        viewModelScope.launch { themeRepository.selectTheme(theme) }
    }
}
