package com.hank.musicfree.feature.settings.setcustomtheme

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.theme.DarkMusicFreeColors
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.core.theme.runtime.ThemeRepository
import com.hank.musicfree.core.theme.runtime.ThemeUiState
import com.hank.musicfree.core.theme.runtime.grayRate
import com.hank.musicfree.core.theme.runtime.toHexString
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SetCustomThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeRepository: ThemeRepository,
    private val loader: ImageAndPaletteLoader = DefaultImageAndPaletteLoader,
) : ViewModel() {

    val state: StateFlow<ThemeUiState> = themeRepository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ThemeUiState(
            selected = SelectedTheme.CUSTOM,
            effectiveColors = DarkMusicFreeColors,
            background = null,
            followSystem = false,
            isLoading = true,
        ),
    )

    private val _activeColorKey = MutableStateFlow<String?>(null)
    val activeColorKey: StateFlow<String?> = _activeColorKey.asStateFlow()

    fun openColorPicker(key: String) {
        _activeColorKey.value = key
    }

    fun dismissColorPicker() {
        _activeColorKey.value = null
    }

    fun onColorConfirmed(key: String, hex: String) {
        viewModelScope.launch {
            themeRepository.patchCustomColors(mapOf(key to hex))
            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "theme_custom_color_patch",
                fields = mapOf("key" to key, "hex" to hex),
            )
            _activeColorKey.value = null
        }
    }

    fun onBlurChanged(blur: Float) {
        viewModelScope.launch {
            themeRepository.setBackground(url = null, blur = blur, opacity = null)
        }
    }

    fun onOpacityChanged(opacity: Float) {
        viewModelScope.launch {
            themeRepository.setBackground(url = null, blur = null, opacity = opacity)
        }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val started = System.nanoTime()
            val copied = loader.copyImageToInternal(context, uri) ?: return@launch
            val palette = loader.extractPalette(context, copied) ?: return@launch
            val derived = deriveCustomColors(palette)
            themeRepository.replaceCustomColors(derived)
            themeRepository.setBackground(url = copied.toString(), blur = null, opacity = null)
            themeRepository.selectTheme(SelectedTheme.CUSTOM)
            val durationMs = (System.nanoTime() - started) / 1_000_000
            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "theme_image_palette_extracted",
                fields = mapOf(
                    "durationMs" to durationMs,
                    "primaryHex" to palette.primary.toHexString(),
                    "grayRate" to grayRate(palette.primary),
                ),
            )
        }
    }
}
