package com.zili.android.musicfreeandroid.feature.listenstats

import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DetailMode
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.ListenedSong
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.TimeScope
import java.time.LocalDate

enum class DetailSort { PLAY_COUNT_DESC, TOTAL_SEC_DESC, FIRST_SEEN_DESC, LAST_SEEN_DESC }

data class ListenDetailScreenState(
    val mode: DetailMode = DetailMode.ALL_SONGS,
    val scope: TimeScope = TimeScope.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val windowLabel: String = "",
    val titleByMode: String = "",
    val summary: String = "",
    val sort: DetailSort = DetailSort.PLAY_COUNT_DESC,
    val items: List<ListenedSong> = emptyList(),
    val filterValue: String? = null,
)
