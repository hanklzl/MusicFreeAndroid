package com.hank.musicfree.feature.listenstats

import com.hank.musicfree.data.repository.listenstats.model.ListenStatsSnapshot
import com.hank.musicfree.data.repository.listenstats.model.TimeScope
import com.hank.musicfree.data.repository.listenstats.model.emptySnapshot
import java.time.LocalDate

data class ListenStatsScreenState(
    val scope: TimeScope = TimeScope.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val windowLabel: String = "",
    val scopeLabel: String = "本周累计",
    val firstEventDate: LocalDate? = null,
    val snapshot: ListenStatsSnapshot = emptySnapshot(),
    val showClearDialog: Boolean = false,
    val clearingInProgress: Boolean = false,
)
