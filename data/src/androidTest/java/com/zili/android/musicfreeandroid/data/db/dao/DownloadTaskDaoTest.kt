package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadTaskDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadTaskDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadTaskDao()
    }

    @After fun teardown() = db.close()

    private fun task(id: String, status: String = "PENDING", platform: String = "qq") = DownloadTaskEntity(
        id = id, platform = platform, title = "t-$id", artist = "a", album = null,
        artwork = null, durationMs = 0L, targetQuality = "standard",
        status = status, errorReason = null, resolvedUrl = null, resolvedHeadersJson = null,
        fileSize = null, downloadedSize = null, createdAt = 1L, updatedAt = 1L,
    )

    @Test fun upsertThenObserveAll() = runTest {
        dao.upsert(task("1"))
        dao.upsert(task("2", status = "FAILED"))
        val all = dao.observeAll().first()
        assertEquals(2, all.size)
    }

    @Test fun findNextPendingReturnsEarliest() = runTest {
        dao.upsert(task("a", status = "FAILED").copy(createdAt = 1L))
        dao.upsert(task("b", status = "PENDING").copy(createdAt = 3L))
        dao.upsert(task("c", status = "PENDING").copy(createdAt = 2L))
        val next = dao.findNextPending()
        assertEquals("c", next?.id)
    }

    @Test fun resetInflightToPendingMovesPreparingAndDownloading() = runTest {
        dao.upsert(task("p", status = "PREPARING"))
        dao.upsert(task("d", status = "DOWNLOADING"))
        dao.upsert(task("f", status = "FAILED"))
        dao.resetInflightToPending()
        val all = dao.observeAll().first().associateBy { it.id }
        assertEquals("PENDING", all["p"]!!.status)
        assertEquals("PENDING", all["d"]!!.status)
        assertEquals("FAILED", all["f"]!!.status)
    }

    @Test fun deleteAllFailedRemovesOnlyFailedRows() = runTest {
        dao.upsert(task("ok", status = "PENDING"))
        dao.upsert(task("bad", status = "FAILED"))
        dao.deleteAllFailed()
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("ok", all[0].id)
    }

    @Test fun deleteByKeyRemovesRow() = runTest {
        dao.upsert(task("x"))
        dao.deleteByKey("x", "qq")
        assertNull(dao.findByKey("x", "qq"))
    }
}
