package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StarredSheetDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: StarredSheetDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.starredSheetDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun entity(
        id: String,
        platform: String = "qq",
        createdAt: Long = 1000L,
        updatedAt: Long,
    ) = StarredSheetEntity(
        id = id,
        platform = platform,
        title = "Sheet $id",
        artist = "Artist",
        coverUri = null,
        sourceUrl = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun observeAll_ordersByUpdatedAtDesc() = runTest {
        dao.upsert(entity(id = "old", updatedAt = 1000L))
        dao.upsert(entity(id = "new", updatedAt = 3000L))
        dao.upsert(entity(id = "mid", updatedAt = 2000L))

        val result = dao.observeAll().first()

        assertEquals(listOf("new", "mid", "old"), result.map { it.id })
    }

    @Test
    fun upsert_allowsSameIdAcrossDifferentPlatforms() = runTest {
        dao.upsert(entity(id = "sheet-1", platform = "qq", updatedAt = 1000L))
        dao.upsert(entity(id = "sheet-1", platform = "kuwo", updatedAt = 2000L))

        val result = dao.observeAll().first()

        assertEquals(2, result.size)
        assertEquals(setOf("qq", "kuwo"), result.map { it.platform }.toSet())
    }

    @Test
    fun upsert_existingIdentity_replacesStoredRow() = runTest {
        dao.upsert(entity(id = "sheet-1", platform = "qq", createdAt = 111L, updatedAt = 200L))
        dao.upsert(entity(id = "sheet-1", platform = "qq", createdAt = 999L, updatedAt = 300L))

        val stored = requireNotNull(dao.getByIdAndPlatform(id = "sheet-1", platform = "qq"))

        assertEquals(999L, stored.createdAt)
        assertEquals(300L, stored.updatedAt)
    }

    @Test
    fun deleteByIdAndPlatform_deletesOnlyTargetIdentity() = runTest {
        dao.upsert(entity(id = "sheet-1", platform = "qq", updatedAt = 1000L))
        dao.upsert(entity(id = "sheet-1", platform = "kuwo", updatedAt = 2000L))
        dao.upsert(entity(id = "sheet-2", platform = "qq", updatedAt = 3000L))

        dao.deleteByIdAndPlatform(id = "sheet-1", platform = "qq")

        val result = dao.observeAll().first()
        assertEquals(2, result.size)
        assertEquals(
            setOf("sheet-1|kuwo", "sheet-2|qq"),
            result.map { "${it.id}|${it.platform}" }.toSet()
        )
    }
}
