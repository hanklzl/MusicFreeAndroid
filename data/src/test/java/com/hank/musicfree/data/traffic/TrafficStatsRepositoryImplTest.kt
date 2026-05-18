package com.hank.musicfree.data.traffic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TrafficStatsRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TrafficStatsRepositoryImpl

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TrafficStatsRepositoryImpl(db.trafficDailyDao())
    }

    @After fun teardown() { db.close() }

    @Test fun observeTotal_sums_all_dates_and_networks() = runTest {
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-18", "WIFI", 100, 10, 1),
            TrafficDailyEntity("2026-05-19", "CELLULAR", 200, 20, 2),
        ))
        val s = repo.observeTotal().first()
        assertEquals(330L, s.totalBytes)
        assertEquals(110L, s.byNetwork[NetworkType.WIFI])
        assertEquals(220L, s.byNetwork[NetworkType.CELLULAR])
    }

    @Test fun observeMonthly_returns_summary_with_buckets_per_day() = runTest {
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-01", "WIFI", 100, 0, 1),
            TrafficDailyEntity("2026-05-15", "WIFI", 200, 0, 2),
        ))
        val s = repo.observeMonthly(LocalDate.of(2026, 5, 1)).first()
        assertEquals(2, s.buckets.size)
        assertEquals(300L, s.totalBytes)
    }

    @Test fun observeYearly_emits_12_buckets() = runTest {
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-03-15", "WIFI", 1000, 0, 1),
        ))
        val s = repo.observeYearly(LocalDate.of(2026, 1, 1)).first()
        assertEquals(12, s.buckets.size)
        assertEquals(1000L, s.totalBytes)
        // 3 月（index 2）应该有数据
        val march = s.buckets[2]
        assertEquals("2026-03", march.label)
        assertEquals(1000L, march.byNetwork[NetworkType.WIFI])
    }

    @Test fun observeFirstRecordDate_returns_LocalDate_or_null() = runTest {
        assertEquals(null, repo.observeFirstRecordDate().first())
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-02-14", "WIFI", 1, 0, 1),
        ))
        assertEquals(LocalDate.of(2026, 2, 14), repo.observeFirstRecordDate().first())
    }

    @Test fun observeDaily_excludes_neighboring_days() = runTest {
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-18", "WIFI", 100, 0, 1),
            TrafficDailyEntity("2026-05-19", "WIFI", 200, 0, 2),
            TrafficDailyEntity("2026-05-20", "WIFI", 400, 0, 3),
        ))
        val s = repo.observeDaily(LocalDate.of(2026, 5, 19)).first()
        assertEquals(200L, s.totalBytes)
        assertEquals(1, s.buckets.size)
        assertEquals("2026-05-19", s.buckets[0].label)
    }

    @Test fun observeWeekly_includes_full_7_day_range() = runTest {
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-17", "WIFI", 1, 0, 1),   // 周一前一天，不计
            TrafficDailyEntity("2026-05-18", "WIFI", 100, 0, 2),  // 周一
            TrafficDailyEntity("2026-05-24", "WIFI", 200, 0, 3),  // 周日（第 7 天）
            TrafficDailyEntity("2026-05-25", "WIFI", 1000, 0, 4), // 下周一，不计
        ))
        val s = repo.observeWeekly(LocalDate.of(2026, 5, 18)).first()
        assertEquals(300L, s.totalBytes)
        assertEquals(2, s.buckets.size)
    }

    @Test fun clearAll_empties_summary() = runTest {
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-19", "WIFI", 100, 10, 1),
        ))
        assertEquals(110L, repo.observeTotal().first().totalBytes)
        repo.clearAll()
        assertEquals(0L, repo.observeTotal().first().totalBytes)
    }
}
