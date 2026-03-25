package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StarredSheetRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: StarredSheetRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = StarredSheetRepository(db.starredSheetDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_exposesMappedModel() = runTest {
        val target = StarredSheet(
            id = "sheet-1",
            platform = "qq",
            title = "Top Hits",
            artist = "Artist A",
            coverUri = "https://example.com/cover.jpg",
            sourceUrl = "https://example.com/sheet",
        )

        repository.observeAll().test {
            assertEquals(emptyList<StarredSheet>(), awaitItem())

            repository.upsert(target)
            assertEquals(listOf(target), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
