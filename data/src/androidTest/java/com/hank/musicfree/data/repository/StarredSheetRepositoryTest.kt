package com.hank.musicfree.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        repository = StarredSheetRepository(db.starredSheetDao(), Converters())
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

    @Test
    fun upsert_sameIdentity_preservesCreatedAtAndRefreshesUpdatedAt() = runTest {
        val initial = StarredSheet(
            id = "sheet-1",
            platform = "qq",
            title = "Top Hits",
            artist = "Artist A",
            coverUri = null,
            sourceUrl = null,
        )
        repository.upsert(initial)
        val first = requireNotNull(db.starredSheetDao().getByIdAndPlatform("sheet-1", "qq"))

        delay(5)

        repository.upsert(initial.copy(title = "Top Hits Updated"))
        val second = requireNotNull(db.starredSheetDao().getByIdAndPlatform("sheet-1", "qq"))

        assertEquals(first.createdAt, second.createdAt)
        assertTrue(second.updatedAt >= first.updatedAt)
        assertEquals("Top Hits Updated", second.title)
    }

    @Test
    fun deleteByIdAndPlatform_deletesOnlyTargetIdentity() = runTest {
        repository.upsert(
            StarredSheet(
                id = "sheet-1",
                platform = "qq",
                title = "QQ Sheet",
                artist = null,
                coverUri = null,
                sourceUrl = null,
            )
        )
        repository.upsert(
            StarredSheet(
                id = "sheet-1",
                platform = "kuwo",
                title = "Kuwo Sheet",
                artist = null,
                coverUri = null,
                sourceUrl = null,
            )
        )

        repository.deleteByIdAndPlatform(id = "sheet-1", platform = "qq")
        val all = repository.observeAll()

        all.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("kuwo", items.first().platform)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun starringAlbumThenUnstarPreservesAlbumKindThenRemovesRow() = runTest {
        val albumModel = StarredSheet(
            id = "alb-9", platform = "qq",
            title = "AlbumNine", artist = "X", coverUri = null, sourceUrl = null,
            kind = com.hank.musicfree.core.model.StarredKind.ALBUM,
        )
        repository.toggle(albumModel)
        val first = repository.observeAll().first()
        assertEquals(1, first.size)
        assertEquals(com.hank.musicfree.core.model.StarredKind.ALBUM, first.single().kind)

        repository.toggle(albumModel)
        assertEquals(0, repository.observeAll().first().size)
    }
}
