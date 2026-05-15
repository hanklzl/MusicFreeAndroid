package com.zili.android.musicfreeandroid.feature.listenstats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.ListenStatsRepository
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DetailFilter
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DetailMode
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.ListenedSong
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.TimeScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.parseDetailMode
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.parseTimeScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.windowFor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ListenDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ListenStatsRepository,
) : ViewModel() {

    private val mode: DetailMode = parseDetailMode(
        savedStateHandle.get<String>("mode") ?: DetailMode.ALL_SONGS.name
    )
    private val initialScope: TimeScope = parseTimeScope(
        savedStateHandle.get<String>("scope") ?: TimeScope.WEEK.name
    )
    private val initialAnchorEpochDay: Long = savedStateHandle.get<Long>("anchorEpochDay") ?: -1L
    private val filterValue: String? = savedStateHandle.get<String?>("filterValue")

    private val scopeFlow = MutableStateFlow(initialScope)
    private val anchorFlow = MutableStateFlow(
        if (initialAnchorEpochDay >= 0) LocalDate.ofEpochDay(initialAnchorEpochDay) else LocalDate.now()
    )
    private val sortFlow = MutableStateFlow(defaultSortFor(mode))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemsFlow: StateFlow<List<ListenedSong>> = combine(scopeFlow, anchorFlow) { s, a -> s to a }
        .flatMapLatest { (s, a) -> repository.detail(DetailFilter(mode, filterValue), s, a) }
        .combine(sortFlow) { rows, sort -> applySort(rows, sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ListenDetailScreenState> = combine(
        scopeFlow, anchorFlow, sortFlow, itemsFlow,
    ) { sc, an, srt, items ->
        val window = windowFor(sc, an)
        ListenDetailScreenState(
            mode = mode,
            scope = sc,
            anchor = an,
            windowLabel = window.label,
            titleByMode = titleForMode(mode, filterValue),
            summary = summaryForMode(mode, items.size, filterValue, window.label),
            sort = srt,
            items = items,
            filterValue = filterValue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListenDetailScreenState())

    fun onScopeChange(s: TimeScope) { scopeFlow.value = s }
    fun onAnchorChange(d: LocalDate) { anchorFlow.value = d }
    fun onSortChange(s: DetailSort) { sortFlow.value = s }

    private fun applySort(rows: List<ListenedSong>, sort: DetailSort): List<ListenedSong> = when (sort) {
        DetailSort.PLAY_COUNT_DESC -> rows.sortedByDescending { it.playCount }
        DetailSort.TOTAL_SEC_DESC -> rows.sortedByDescending { it.totalSec }
        DetailSort.FIRST_SEEN_DESC -> rows.sortedByDescending { it.firstSeenMs }
        DetailSort.LAST_SEEN_DESC -> rows.sortedByDescending { it.lastSeenMs }
    }

    private fun defaultSortFor(m: DetailMode): DetailSort = when (m) {
        DetailMode.FIRST_SEEN -> DetailSort.FIRST_SEEN_DESC
        else -> DetailSort.PLAY_COUNT_DESC
    }

    private fun titleForMode(m: DetailMode, filterValue: String?): String = when (m) {
        DetailMode.ALL_SONGS -> "听过的歌曲"
        DetailMode.ALL_ARTISTS -> "听过的歌手"
        DetailMode.TOP_SONGS -> "Top 歌曲（全部）"
        DetailMode.TOP_ARTISTS -> "Top 歌手（全部）"
        DetailMode.FIRST_SEEN -> "新发现"
        DetailMode.BY_ARTIST -> filterValue ?: "歌手"
        DetailMode.BY_LANGUAGE -> filterValue ?: "语言"
        DetailMode.BY_GENRE -> filterValue ?: "风格"
    }

    private fun summaryForMode(m: DetailMode, count: Int, filterValue: String?, windowLabel: String): String = when (m) {
        DetailMode.ALL_SONGS -> "$windowLabel 听过的 $count 首歌"
        DetailMode.ALL_ARTISTS -> "$windowLabel 听过的 $count 位歌手"
        DetailMode.TOP_SONGS -> "$windowLabel 全部排行（共 $count 首）"
        DetailMode.TOP_ARTISTS -> "$windowLabel 全部排行（共 $count 位）"
        DetailMode.FIRST_SEEN -> "$windowLabel 首次听到的 $count 首"
        DetailMode.BY_ARTIST -> "${filterValue ?: ""} · $windowLabel 共 $count 首"
        DetailMode.BY_LANGUAGE -> "${filterValue ?: ""} · $windowLabel 共 $count 首"
        DetailMode.BY_GENRE -> "${filterValue ?: ""} · $windowLabel 共 $count 首"
    }
}
