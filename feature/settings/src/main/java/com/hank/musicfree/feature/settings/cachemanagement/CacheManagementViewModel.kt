package com.hank.musicfree.feature.settings.cachemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.feature.settings.SettingsCacheCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CacheManagementUiState(
    val platform: String = "",
    val itemId: String = "",
    val isClearing: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val canClear: Boolean
        get() = platform.isNotBlank() && itemId.isNotBlank() && !isClearing
}

@HiltViewModel
class CacheManagementViewModel @Inject constructor(
    private val cacheCleaner: SettingsCacheCleaner,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CacheManagementUiState())
    val uiState: StateFlow<CacheManagementUiState> = _uiState.asStateFlow()

    fun onPlatformChange(value: String) {
        _uiState.update {
            it.copy(
                platform = value,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun onItemIdChange(value: String) {
        _uiState.update {
            it.copy(
                itemId = value,
                message = null,
                errorMessage = null,
            )
        }
    }

    fun clearSpecifiedSongCache() {
        val snapshot = _uiState.value
        val platform = snapshot.platform.trim()
        val itemId = snapshot.itemId.trim()
        if (platform.isEmpty() || itemId.isEmpty() || snapshot.isClearing) {
            _uiState.update { it.copy(errorMessage = "请填写平台和歌曲 ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    platform = platform,
                    itemId = itemId,
                    isClearing = true,
                    message = null,
                    errorMessage = null,
                )
            }
            try {
                val result = cacheCleaner.clearSongPlaybackCache(platform, itemId)
                val associationText = if (result.localAssociationCleared) {
                    "，已解除本地播放关联"
                } else {
                    ""
                }
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        message = "已清理 ${result.platform} / ${result.itemId}$associationText",
                    )
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isClearing = false) }
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        errorMessage = "清理失败：${error.message ?: "未知错误"}",
                    )
                }
            }
        }
    }
}
