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

    private fun entity(id: String, updatedAt: Long) = StarredSheetEntity(
        id = id,
        platform = "qq",
        title = "Sheet $id",
        artist = "Artist",
        coverUri = null,
        sourceUrl = null,
        createdAt = 1000L,
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
}
