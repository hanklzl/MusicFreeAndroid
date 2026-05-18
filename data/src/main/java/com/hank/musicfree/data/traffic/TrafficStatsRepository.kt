package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.NetworkType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class TrafficBucket(
    val label: String,
    val byNetwork: Map<NetworkType, Long>,
)

data class TrafficRangeSummary(
    val anchor: LocalDate,
    val buckets: List<TrafficBucket>,
    val totalBytes: Long,
    val byNetwork: Map<NetworkType, Long>,
)

interface TrafficStatsRepository {
    fun observeDaily(date: LocalDate): Flow<TrafficRangeSummary>
    fun observeWeekly(weekStart: LocalDate): Flow<TrafficRangeSummary>
    fun observeMonthly(monthStart: LocalDate): Flow<TrafficRangeSummary>
    fun observeYearly(yearStart: LocalDate): Flow<TrafficRangeSummary>
    fun observeTotal(): Flow<TrafficRangeSummary>
    fun observeFirstRecordDate(): Flow<LocalDate?>
    suspend fun clearAll()
}
