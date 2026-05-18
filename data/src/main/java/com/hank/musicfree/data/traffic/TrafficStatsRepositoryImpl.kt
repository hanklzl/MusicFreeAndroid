package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.data.db.dao.TrafficDailyDao
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficStatsRepositoryImpl @Inject constructor(
    private val dao: TrafficDailyDao,
) : TrafficStatsRepository {

    override fun observeDaily(date: LocalDate): Flow<TrafficRangeSummary> =
        dao.observeRange(date.toString(), date.toString())
            .map { rows -> rows.toSummary(anchor = date) }

    override fun observeWeekly(weekStart: LocalDate): Flow<TrafficRangeSummary> {
        val end = weekStart.plusDays(6)
        return dao.observeRange(weekStart.toString(), end.toString())
            .map { rows -> rows.toSummary(anchor = weekStart) }
    }

    override fun observeMonthly(monthStart: LocalDate): Flow<TrafficRangeSummary> {
        val end = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        return dao.observeRange(monthStart.toString(), end.toString())
            .map { rows -> rows.toSummary(anchor = monthStart) }
    }

    override fun observeYearly(yearStart: LocalDate): Flow<TrafficRangeSummary> {
        val yearStartStr = yearStart.toString()
        val nextYear = yearStart.plusYears(1).toString()
        return dao.observeMonthlyRange(yearStartStr, nextYear)
            .map { rows ->
                val byMonth = rows.groupBy { it.yearMonth }
                val buckets = (1..12).map { m ->
                    val ym = YearMonth.of(yearStart.year, m).toString()
                    val byNet = byMonth[ym]?.associate {
                        NetworkType.valueOf(it.networkType) to (it.bytesReceived + it.bytesSent)
                    } ?: emptyMap()
                    TrafficBucket(label = ym, byNetwork = byNet)
                }
                TrafficRangeSummary(
                    anchor = yearStart,
                    buckets = buckets,
                    totalBytes = buckets.sumOf { it.byNetwork.values.sum() },
                    byNetwork = NetworkType.values().associateWith { nt ->
                        buckets.sumOf { it.byNetwork[nt] ?: 0L }
                    },
                )
            }
    }

    override fun observeTotal(): Flow<TrafficRangeSummary> =
        dao.observeTotalsByNetwork().map { rows ->
            val byNet = rows.associate {
                NetworkType.valueOf(it.networkType) to (it.bytesReceived + it.bytesSent)
            }
            TrafficRangeSummary(
                anchor = LocalDate.now(),
                buckets = listOf(TrafficBucket(label = "TOTAL", byNetwork = byNet)),
                totalBytes = byNet.values.sum(),
                byNetwork = byNet,
            )
        }

    override fun observeFirstRecordDate(): Flow<LocalDate?> =
        dao.observeFirstRecordDate().map { it?.let(LocalDate::parse) }

    override suspend fun clearAll() = dao.clearAll()

    private fun List<TrafficDailyEntity>.toSummary(anchor: LocalDate): TrafficRangeSummary {
        val grouped = groupBy { it.localDate }
        val buckets = grouped.entries.sortedBy { it.key }.map { (k, rows) ->
            val byNet = rows.associate {
                NetworkType.valueOf(it.networkType) to (it.bytesReceived + it.bytesSent)
            }
            TrafficBucket(label = k, byNetwork = byNet)
        }
        return TrafficRangeSummary(
            anchor = anchor,
            buckets = buckets,
            totalBytes = buckets.sumOf { it.byNetwork.values.sum() },
            byNetwork = NetworkType.values().associateWith { nt ->
                buckets.sumOf { it.byNetwork[nt] ?: 0L }
            },
        )
    }
}
