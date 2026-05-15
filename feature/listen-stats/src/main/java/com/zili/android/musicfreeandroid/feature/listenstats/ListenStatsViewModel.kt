package com.zili.android.musicfreeandroid.feature.listenstats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.ListenStatsRepository
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.ListenStatsSnapshot
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.TimeScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.emptySnapshot
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.parseTimeScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.windowFor
import com.zili.android.musicfreeandroid.player.controller.PlayerController
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
import kotlinx.coroutines.launch

@HiltViewModel
class ListenStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ListenStatsRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    // Read route args directly to stay testable with plain SavedStateHandle (avoids Android Bundle)
    private val scopeFlow = MutableStateFlow(
        parseTimeScope(savedStateHandle.get<String>("scope") ?: "WEEK")
    )
    private val anchorFlow = MutableStateFlow(
        (savedStateHandle.get<Long>("anchorEpochDay") ?: -1L).let { epochDay ->
            if (epochDay >= 0) LocalDate.ofEpochDay(epochDay) else LocalDate.now()
        }
    )
    private val showClearDialog = MutableStateFlow(false)
    private val clearingInProgress = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val snapshotFlow: StateFlow<ListenStatsSnapshot> =
        combine(scopeFlow, anchorFlow) { scope, anchor -> scope to anchor }
            .flatMapLatest { (scope, anchor) -> repository.statsForWindow(scope, anchor) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySnapshot())

    private val firstEventDateFlow: StateFlow<LocalDate?> = repository.firstEventDate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<ListenStatsScreenState> = combine(
        scopeFlow, anchorFlow, snapshotFlow, firstEventDateFlow,
        showClearDialog, clearingInProgress,
    ) { fields ->
        @Suppress("UNCHECKED_CAST")
        val scope = fields[0] as TimeScope
        @Suppress("UNCHECKED_CAST")
        val anchor = fields[1] as LocalDate
        @Suppress("UNCHECKED_CAST")
        val snap = fields[2] as ListenStatsSnapshot
        @Suppress("UNCHECKED_CAST")
        val firstDate = fields[3] as LocalDate?
        @Suppress("UNCHECKED_CAST")
        val showDialog = fields[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val clearing = fields[5] as Boolean
        val window = windowFor(scope, anchor, firstEventDate = firstDate)
        ListenStatsScreenState(
            scope = scope,
            anchor = anchor,
            windowLabel = window.label,
            scopeLabel = scopeLabel(scope),
            firstEventDate = firstDate,
            snapshot = snap,
            showClearDialog = showDialog,
            clearingInProgress = clearing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListenStatsScreenState())

    fun onScopeChange(scope: TimeScope) { scopeFlow.value = scope }
    fun onAnchorChange(anchor: LocalDate) { anchorFlow.value = anchor }
    fun onPagerPrev() { anchorFlow.value = shiftAnchor(scopeFlow.value, anchorFlow.value, -1) }
    fun onPagerNext() { anchorFlow.value = shiftAnchor(scopeFlow.value, anchorFlow.value, +1) }

    fun onClearRequested() { showClearDialog.value = true }
    fun onClearDismissed() { showClearDialog.value = false }
    fun onClearConfirmed() {
        showClearDialog.value = false
        clearingInProgress.value = true
        viewModelScope.launch {
            runCatching {
                playerController.flushListenTrackerForClear()
                repository.clearAll()
            }
            clearingInProgress.value = false
        }
    }

    private fun shiftAnchor(scope: TimeScope, anchor: LocalDate, delta: Int): LocalDate = when (scope) {
        TimeScope.DAY -> anchor.plusDays(delta.toLong())
        TimeScope.WEEK -> anchor.plusWeeks(delta.toLong())
        TimeScope.MONTH -> anchor.plusMonths(delta.toLong())
        TimeScope.YEAR -> anchor.plusYears(delta.toLong())
        TimeScope.ALL_TIME -> anchor
    }

    private fun scopeLabel(scope: TimeScope): String = when (scope) {
        TimeScope.DAY -> "今日累计"
        TimeScope.WEEK -> "本周累计"
        TimeScope.MONTH -> "本月累计"
        TimeScope.YEAR -> "本年累计"
        TimeScope.ALL_TIME -> "累计听歌"
    }
}
