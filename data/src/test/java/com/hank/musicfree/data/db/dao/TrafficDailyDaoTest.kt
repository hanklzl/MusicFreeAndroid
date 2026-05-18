package com.hank.musicfree.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
class TrafficDailyDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrafficDailyDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.trafficDailyDao()
    }

    @After fun teardown() { db.close() }

    @Test fun upsert_accumulates_existing_row() = runTest {
        dao.upsertAllAccumulating(
            listOf(TrafficDailyEntity("2026-05-19", "WIFI", 100, 10, 1L)),
        )
        dao.upsertAllAccumulating(
            listOf(TrafficDailyEntity("2026-05-19", "WIFI", 200, 20, 2L)),
        )
        val rows = dao.observeRange("2026-05-19", "2026-05-19").first()
        assertEquals(1, rows.size)
        assertEquals(300, rows[0].bytesReceived)
        assertEquals(30, rows[0].bytesSent)
    }

    @Test fun upsert_inserts_new_row_when_missing() = runTest {
        dao.upsertAllAccumulating(
            listOf(TrafficDailyEntity("2026-05-19", "CELLULAR", 50, 5, 1L)),
        )
        val rows = dao.observeRange("2026-05-19", "2026-05-19").first()
        assertEquals(1, rows.size)
        assertEquals(50, rows[0].bytesReceived)
        assertEquals("CELLULAR", rows[0].networkType)
    }

    @Test fun observeTotalsByNetwork_sums_across_dates() = runTest {
        dao.upsertAllAccumulating(
            listOf(
                TrafficDailyEntity("2026-05-18", "WIFI", 100, 0, 1L),
                TrafficDailyEntity("2026-05-19", "WIFI", 200, 0, 2L),
            ),
        )
        val totals = dao.observeTotalsByNetwork().first()
        assertEquals(1, totals.size)
        assertEquals(300, totals[0].bytesReceived)
    }

    @Test fun clearAll_empties_table() = runTest {
        dao.upsertAllAccumulating(
            listOf(TrafficDailyEntity("2026-05-19", "WIFI", 1, 1, 1L)),
        )
        dao.clearAll()
        assertEquals(0, dao.observeRange("2000-01-01", "2099-12-31").first().size)
    }

    @Test fun upsert_accumulates_same_key_within_batch() = runTest {
        dao.upsertAllAccumulating(
            listOf(
                TrafficDailyEntity("2026-05-19", "WIFI", 100, 10, 1L),
                TrafficDailyEntity("2026-05-19", "WIFI", 200, 20, 2L),
            ),
        )
        val rows = dao.observeRange("2026-05-19", "2026-05-19").first()
        assertEquals(1, rows.size)
        assertEquals(300L, rows[0].bytesReceived)
        assertEquals(30L, rows[0].bytesSent)
        assertEquals(2L, rows[0].updatedAt)
    }

    @Test fun observeMonthlyRange_groups_by_year_month_and_network() = runTest {
        dao.upsertAllAccumulating(
            listOf(
                TrafficDailyEntity("2026-04-30", "WIFI", 100, 0, 1L),
                TrafficDailyEntity("2026-05-01", "WIFI", 200, 0, 2L),
                TrafficDailyEntity("2026-05-19", "WIFI", 300, 0, 3L),
                TrafficDailyEntity("2026-05-19", "CELLULAR", 50, 5, 4L),
                TrafficDailyEntity("2026-06-01", "WIFI", 1000, 0, 5L),
            ),
        )
        val rows = dao.observeMonthlyRange("2026-05-01", "2026-06-01").first()
        assertEquals(2, rows.size)
        val wifi = rows.first { it.networkType == "WIFI" }
        val cellular = rows.first { it.networkType == "CELLULAR" }
        assertEquals("2026-05", wifi.yearMonth)
        assertEquals(500L, wifi.bytesReceived)
        assertEquals(50L, cellular.bytesReceived)
        assertEquals(5L, cellular.bytesSent)
    }

    @Test fun observeFirstRecordDate_returns_minimum_local_date() = runTest {
        assertEquals(null, dao.observeFirstRecordDate().first())
        dao.upsertAllAccumulating(
            listOf(
                TrafficDailyEntity("2026-05-19", "WIFI", 1, 0, 1L),
                TrafficDailyEntity("2026-01-15", "WIFI", 1, 0, 2L),
                TrafficDailyEntity("2026-03-08", "CELLULAR", 1, 0, 3L),
            ),
        )
        assertEquals("2026-01-15", dao.observeFirstRecordDate().first())
    }
}
