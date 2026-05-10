package com.zili.android.musicfreeandroid.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StarredSheetRepositoryJvmTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: StarredSheetRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StarredSheetRepository(db.starredSheetDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun toggle_updatesStarredStateFalseTrueFalse() = runTest {
        val sheet = starredSheet()

        assertFalse(repository.observeIsStarred(sheet.id, sheet.platform).first())

        repository.toggle(sheet)
        assertTrue(repository.observeIsStarred(sheet.id, sheet.platform).first())

        repository.toggle(sheet)
        assertFalse(repository.observeIsStarred(sheet.id, sheet.platform).first())
    }

    @Test
    fun upsert_roundTripsExtendedSeedFieldsAndRawPayload() = runTest {
        val sheet = starredSheet(
            description = "Daily picks",
            artwork = "https://example.com/artwork.jpg",
            worksNum = 42,
            raw = mapOf(
                "id" to "sheet-1",
                "tags" to listOf("daily", "rock"),
                "nested" to mapOf("source" to "fixture", "public" to true),
            ),
        )

        repository.toggle(sheet)

        val restored = repository.observeAll().first().single()
        assertEquals(sheet.description, restored.description)
        assertEquals(sheet.artwork, restored.artwork)
        assertEquals(sheet.worksNum, restored.worksNum)
        assertEquals(sheet.raw, restored.raw)
    }

    private fun starredSheet(
        description: String? = null,
        artwork: String? = null,
        worksNum: Int? = null,
        raw: Map<String, Any?> = emptyMap(),
    ) = StarredSheet(
        id = "sheet-1",
        platform = "fixture",
        title = "Fixture Sheet",
        artist = "Artist",
        coverUri = "https://example.com/cover.jpg",
        sourceUrl = "https://example.com/sheet",
        description = description,
        artwork = artwork,
        worksNum = worksNum,
        raw = raw,
    )
}
