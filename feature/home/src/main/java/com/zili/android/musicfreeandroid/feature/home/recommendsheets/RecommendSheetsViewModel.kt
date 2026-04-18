package com.zili.android.musicfreeandroid.feature.home.recommendsheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecommendSheetsViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    companion object {
        private const val DEFAULT_TAG_ID = "__default__"
    }

    val availablePlugins: StateFlow<List<PluginInfo>> = pluginManager.plugins
        .map { list -> list.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin.asStateFlow()

    private val _uiState = MutableStateFlow(RecommendSheetsUiState(loading = true))
    val uiState: StateFlow<RecommendSheetsUiState> = _uiState.asStateFlow()

    private var page: Int = 0

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
        }
        viewModelScope.launch {
            availablePlugins.collect { plugins ->
                if (_selectedPlugin.value == null && plugins.isNotEmpty()) {
                    selectPlugin(plugins.first().platform)
                }
            }
        }
    }

    fun selectPlugin(platform: String) {
        _selectedPlugin.value = platform
        viewModelScope.launch {
            loadTagsAndFirstPage(platform)
        }
    }

    fun selectTag(tagId: String) {
        val platform = _selectedPlugin.value ?: return
        val tag = _uiState.value.tags.firstOrNull { it.id == tagId } ?: return
        if (_uiState.value.selectedTagId == tag.id && _uiState.value.sheets.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            page = 0
            _uiState.value = _uiState.value.copy(
                selectedTagId = tag.id,
                sheets = emptyList(),
                loading = true,
                loadingMore = false,
                isEnd = false,
                errorMessage = null,
            )
            loadSheets(platform = platform, tag = tag, reset = true)
        }
    }

    fun refresh() {
        val platform = _selectedPlugin.value ?: return
        val tag = currentTag() ?: defaultTag()
        viewModelScope.launch {
            page = 0
            _uiState.value = _uiState.value.copy(
                selectedTagId = tag.id,
                sheets = emptyList(),
                loading = true,
                loadingMore = false,
                isEnd = false,
                errorMessage = null,
            )
            loadSheets(platform = platform, tag = tag, reset = true)
        }
    }

    fun loadMore() {
        val platform = _selectedPlugin.value ?: return
        val state = _uiState.value
        val tag = currentTag() ?: defaultTag()
        if (state.loading || state.loadingMore || state.isEnd) {
            return
        }
        viewModelScope.launch {
            loadSheets(platform = platform, tag = tag, reset = false)
        }
    }

    private suspend fun loadTagsAndFirstPage(platform: String) {
        val plugin = pluginManager.getPlugin(platform)
        if (plugin == null) {
            _uiState.value = RecommendSheetsUiState(
                loading = false,
                errorMessage = "插件不存在：$platform",
            )
            return
        }

        _uiState.value = RecommendSheetsUiState(loading = true)
        val tagsResult = runCatching { plugin.getRecommendSheetTags() }.getOrNull()

        val tags = buildTags(tagsResult?.pinned.orEmpty(), tagsResult?.data.orEmpty())
        val selected = tags.firstOrNull() ?: defaultTag()

        page = 0
        _uiState.value = RecommendSheetsUiState(
            tags = tags,
            selectedTagId = selected.id,
            loading = true,
        )
        loadSheets(platform = platform, tag = selected, reset = true)
    }

    private suspend fun loadSheets(
        platform: String,
        tag: RecommendTag,
        reset: Boolean,
    ) {
        val plugin = pluginManager.getPlugin(platform)
        if (plugin == null) {
            _uiState.value = _uiState.value.copy(
                loading = false,
                loadingMore = false,
                errorMessage = "插件不存在：$platform",
            )
            return
        }

        val nextPage = if (reset) 1 else page + 1
        _uiState.value = _uiState.value.copy(
            loading = reset,
            loadingMore = !reset,
            errorMessage = null,
        )

        runCatching {
            plugin.getRecommendSheetsByTag(tag.payload, nextPage)
        }.onSuccess { result ->
            if (result == null) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    loadingMore = false,
                    errorMessage = "加载推荐歌单失败",
                )
                return@onSuccess
            }
            page = nextPage
            val incoming = result.data.map { item ->
                if (item.platform.isBlank()) item.copy(platform = platform) else item
            }
            val merged = if (reset) incoming else _uiState.value.sheets + incoming
            _uiState.value = _uiState.value.copy(
                sheets = merged,
                loading = false,
                loadingMore = false,
                isEnd = result.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            _uiState.value = _uiState.value.copy(
                loading = false,
                loadingMore = false,
                errorMessage = e.message ?: "加载推荐歌单失败",
            )
        }
    }

    private fun buildTags(
        pinned: List<MusicSheetItemBase>,
        groups: List<com.zili.android.musicfreeandroid.plugin.api.MusicSheetGroupItem>,
    ): List<RecommendTag> {
        val map = linkedMapOf<String, RecommendTag>()
        map[DEFAULT_TAG_ID] = defaultTag()

        fun add(item: MusicSheetItemBase) {
            val id = item.id.ifBlank { return }
            if (map.containsKey(id)) return
            val payload = item.raw + mapOf("id" to item.id, "title" to item.title)
            map[id] = RecommendTag(
                id = id,
                title = item.title ?: id,
                payload = payload,
            )
        }

        pinned.forEach(::add)
        groups.flatMap { it.data }.forEach(::add)
        return map.values.toList()
    }

    private fun currentTag(): RecommendTag? {
        val state = _uiState.value
        return state.tags.firstOrNull { it.id == state.selectedTagId }
    }

    private fun defaultTag(): RecommendTag = RecommendTag(
        id = DEFAULT_TAG_ID,
        title = "默认",
        payload = mapOf("id" to ""),
    )
}
