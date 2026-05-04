package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadedTrackDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadedTrackDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadedTrackDao()
    }

    @After fun teardown() = db.close()

    private fun row(id: String, platform: String = "qq") = DownloadedTrackEntity(
        id = id, platform = platform,
        mediaStoreUri = "content://media/external/audio/media/$id",
        relativePath = "Music/MusicFree/", mimeType = "audio/mpeg",
        quality = "standard", sizeBytes = 1024L, downloadedAt = 1L,
    )

    @Test fun insertAndExists() = runTest {
        assertFalse(dao.exists("1", "qq"))
        dao.insert(row("1"))
        assertTrue(dao.exists("1", "qq"))
        assertEquals("content://media/external/audio/media/1", dao.findUri("1", "qq"))
    }

    @Test fun deleteByKeyRemovesRow() = runTest {
        dao.insert(row("1"))
        dao.deleteByKey("1", "qq")
        assertFalse(dao.exists("1", "qq"))
        assertNull(dao.findUri("1", "qq"))
    }

    @Test fun observeKeysEmitsCurrentSet() = runTest {
        dao.insert(row("a"))
        dao.insert(row("b"))
        val keys = dao.observeKeys().first().toSet()
        assertEquals(setOf("a@qq", "b@qq"), keys)
    }
}
