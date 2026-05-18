package com.hank.musicfree.feature.settings.traffic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.data.traffic.TrafficStatsRepository
import com.hank.musicfree.player.cache.MediaCacheStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrafficStatsViewModel @Inject constructor(
    private val repo: TrafficStatsRepository,
    private val cacheStore: MediaCacheStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialScope = runCatching {
        TrafficScope.valueOf(savedStateHandle.get<String>("scope") ?: "MONTH")
    }.getOrDefault(TrafficScope.MONTH)
    private val initialAnchor =
        (savedStateHandle.get<Long>("anchorEpochDay") ?: -1L).let { epochDay ->
            if (epochDay < 0) LocalDate.now() else LocalDate.ofEpochDay(epochDay)
        }

    private val tab = MutableStateFlow(initialScope)
    private val anchor = MutableStateFlow(initialAnchor)

    val uiState: StateFlow<TrafficUiState> =
        combine(tab, anchor) { t, a -> t to a }
            .flatMapLatest { (t, a) ->
                val normalized = t.normalize(a)
                val flow = when (t) {
                    TrafficScope.DAY -> repo.observeDaily(normalized)
                    TrafficScope.WEEK -> repo.observeWeekly(normalized)
                    TrafficScope.MONTH -> repo.observeMonthly(normalized)
                    TrafficScope.YEAR -> repo.observeYearly(normalized)
                    TrafficScope.TOTAL -> repo.observeTotal()
                }
                flow.map { it.toUi(scope = t, anchorLabel = formatAnchor(t, normalized)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrafficUiState.Loading)

    val cacheUsage: StateFlow<Long> = cacheStore.usedBytesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun selectTab(t: TrafficScope) { tab.value = t }
    fun shiftAnchor(direction: Int) { anchor.update { tab.value.shift(it, direction) } }
    fun clearAllRecords() = viewModelScope.launch { repo.clearAll() }
    fun clearMediaCache() = viewModelScope.launch { cacheStore.clear() }

    private fun formatAnchor(t: TrafficScope, d: LocalDate): String = when (t) {
        TrafficScope.DAY -> "${d.year} 年 ${d.monthValue} 月 ${d.dayOfMonth} 日"
        TrafficScope.WEEK -> "${d.year} 年 第 ${d.dayOfYear / 7 + 1} 周"
        TrafficScope.MONTH -> "${d.year} 年 ${d.monthValue} 月"
        TrafficScope.YEAR -> "${d.year} 年"
        TrafficScope.TOTAL -> "累计"
    }
}
