package com.hank.musicfree.feature.listenstats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.data.repository.listenstats.model.TimeScope
import com.hank.musicfree.feature.listenstats.component.ClearStatsDialog
import com.hank.musicfree.feature.listenstats.component.DailyBarsCard
import com.hank.musicfree.feature.listenstats.component.GenreCard
import com.hank.musicfree.feature.listenstats.component.HeatmapCard
import com.hank.musicfree.feature.listenstats.component.HeroTotalDurationCard
import com.hank.musicfree.feature.listenstats.component.HourCard
import com.hank.musicfree.feature.listenstats.component.LanguageCard
import com.hank.musicfree.feature.listenstats.component.MoreMenu
import com.hank.musicfree.feature.listenstats.component.OnboardingHint
import com.hank.musicfree.feature.listenstats.component.SecondaryKpiRow
import com.hank.musicfree.feature.listenstats.component.StreakDiscoveryRow
import com.hank.musicfree.feature.listenstats.component.TimeScopePager
import com.hank.musicfree.feature.listenstats.component.TimeScopeSegmented
import com.hank.musicfree.feature.listenstats.component.TopArtistsCard
import com.hank.musicfree.feature.listenstats.component.TopSongsCard
import java.time.LocalDate

@Composable
fun ListenStatsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (mode: String, scope: String, anchorEpochDay: Long, filterValue: String?) -> Unit,
    viewModel: ListenStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatelessListenStatsScaffold(
        state = state,
        onBack = onBack,
        onScopeChange = viewModel::onScopeChange,
        onAnchorPrev = viewModel::onPagerPrev,
        onAnchorNext = viewModel::onPagerNext,
        onNavigateToDetail = onNavigateToDetail,
        onClearRequested = viewModel::onClearRequested,
        onClearConfirmed = viewModel::onClearConfirmed,
        onClearDismissed = viewModel::onClearDismissed,
        onBarClick = { epochDay ->
            viewModel.onScopeChange(TimeScope.DAY)
            viewModel.onAnchorChange(LocalDate.ofEpochDay(epochDay))
        },
        onHeatmapClick = { epochDay ->
            viewModel.onScopeChange(TimeScope.DAY)
            viewModel.onAnchorChange(LocalDate.ofEpochDay(epochDay))
        },
    )
    if (state.showClearDialog) {
        ClearStatsDialog(
            onConfirm = viewModel::onClearConfirmed,
            onDismiss = viewModel::onClearDismissed,
        )
    }
}

@Composable
internal fun StatelessListenStatsScaffold(
    state: ListenStatsScreenState,
    onBack: () -> Unit,
    onScopeChange: (TimeScope) -> Unit,
    onAnchorPrev: () -> Unit,
    onAnchorNext: () -> Unit,
    onNavigateToDetail: (mode: String, scope: String, anchorEpochDay: Long, filterValue: String?) -> Unit,
    onClearRequested: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onClearConfirmed: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onClearDismissed: () -> Unit,
    onBarClick: (Long) -> Unit,
    onHeatmapClick: (Long) -> Unit,
) {
    MusicFreeScreenScaffold(
        title = "听歌足迹",
        onBack = onBack,
        actions = { MoreMenu(onClear = onClearRequested) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OnboardingHint(state.firstEventDate) }
            item { HeroTotalDurationCard(state.snapshot.totalSeconds, state.scopeLabel) }
            item { TimeScopeSegmented(state.scope, onScopeChange) }
            item { TimeScopePager(state.windowLabel, onAnchorPrev, onAnchorNext) }
            item {
                SecondaryKpiRow(
                    distinctSongs = state.snapshot.distinctSongs,
                    distinctArtists = state.snapshot.distinctArtists,
                    onSongsClick = { onNavigateToDetail("ALL_SONGS", state.scope.name, state.anchor.toEpochDay(), null) },
                    onArtistsClick = { onNavigateToDetail("ALL_ARTISTS", state.scope.name, state.anchor.toEpochDay(), null) },
                )
            }
            item { DailyBarsCard(daily = state.snapshot.dailyBuckets, onBarClick = onBarClick) }
            item {
                TopSongsCard(
                    rows = state.snapshot.topSongs,
                    onSeeAll = { onNavigateToDetail("TOP_SONGS", state.scope.name, state.anchor.toEpochDay(), null) },
                    onRowClick = { onNavigateToDetail("TOP_SONGS", state.scope.name, state.anchor.toEpochDay(), null) },
                )
            }
            item {
                TopArtistsCard(
                    rows = state.snapshot.topArtists,
                    onSeeAll = { onNavigateToDetail("TOP_ARTISTS", state.scope.name, state.anchor.toEpochDay(), null) },
                    onRowClick = { artist -> onNavigateToDetail("BY_ARTIST", state.scope.name, state.anchor.toEpochDay(), artist) },
                )
            }
            if (state.snapshot.languageDistribution.coverage >= 0.30f) {
                item {
                    LanguageCard(
                        distribution = state.snapshot.languageDistribution,
                        onSegmentClick = { key ->
                            if (key != null) onNavigateToDetail("BY_LANGUAGE", state.scope.name, state.anchor.toEpochDay(), key)
                        },
                    )
                }
            }
            if (state.snapshot.genreDistribution.coverage >= 0.30f) {
                item {
                    GenreCard(
                        distribution = state.snapshot.genreDistribution,
                        onRowClick = { key ->
                            if (key != null) onNavigateToDetail("BY_GENRE", state.scope.name, state.anchor.toEpochDay(), key)
                        },
                    )
                }
            }
            item { HourCard(buckets = state.snapshot.hourBuckets) }
            item {
                StreakDiscoveryRow(
                    streakDays = state.snapshot.streakDays,
                    maxStreak = state.snapshot.maxStreak,
                    firstSeenCount = state.snapshot.firstSeenCount,
                    onStreakClick = { /* v1 不下钻打卡日历，留给 v2 */ },
                    onDiscoveryClick = {
                        onNavigateToDetail("FIRST_SEEN", state.scope.name, state.anchor.toEpochDay(), null)
                    },
                )
            }
            if (state.scope in listOf(TimeScope.MONTH, TimeScope.YEAR, TimeScope.ALL_TIME)) {
                item { HeatmapCard(cells = state.snapshot.heatmap, onCellClick = onHeatmapClick) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
