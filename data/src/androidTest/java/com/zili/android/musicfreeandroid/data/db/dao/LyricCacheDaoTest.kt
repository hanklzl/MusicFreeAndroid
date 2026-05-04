package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LyricCacheDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.lyricCacheDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsertAndObserveByKey() = runTest {
        dao.upsert(entity("1", "demo", remoteRawLrc = "[00:01.00]Hello"))

        val row = dao.observeByKey("demo", "1").first()

        assertEquals("[00:01.00]Hello", row?.remoteRawLrc)
    }

    @Test
    fun clearAssociationKeepsLyrics() = runTest {
        dao.upsert(entity("1", "demo", remoteRawLrc = "raw", associatedMusicJson = """{"id":"l1"}"""))

        dao.clearAssociation("demo", "1", updatedAt = 200L)
        val row = dao.getByKey("demo", "1")

        assertEquals("raw", row?.remoteRawLrc)
        assertNull(row?.associatedMusicJson)
        assertEquals(200L, row?.updatedAt)
    }

    @Test
    fun deleteLocalLyricsKeepsRemoteCache() = runTest {
        dao.upsert(entity("1", "demo", remoteRawLrc = "remote", localRawLrc = "local", localTranslation = "tran"))

        dao.deleteLocalLyrics("demo", "1", updatedAt = 300L)
        val row = dao.getByKey("demo", "1")

        assertEquals("remote", row?.remoteRawLrc)
        assertNull(row?.localRawLrc)
        assertNull(row?.localTranslation)
    }

    @Test
    fun setOffsetUpdatesOnlyOffsetAndUpdatedAt() = runTest {
        dao.upsert(entity("1", "demo", remoteRawLrc = "remote"))

        dao.setOffset("demo", "1", 500L, updatedAt = 400L)
        val row = dao.getByKey("demo", "1")

        assertEquals(500L, row?.userOffsetMs)
        assertEquals(400L, row?.updatedAt)
        assertEquals("remote", row?.remoteRawLrc)
    }

    private fun entity(
        id: String,
        platform: String,
        remoteRawLrc: String? = null,
        associatedMusicJson: String? = null,
        localRawLrc: String? = null,
        localTranslation: String? = null,
    ) = LyricCacheEntity(
        musicId = id,
        musicPlatform = platform,
        remoteRawLrc = remoteRawLrc,
        remoteRawLrcTxt = null,
        remoteTranslation = null,
        remoteSourceType = null,
        remoteSourcePlatform = null,
        remoteSourceMusicId = null,
        remoteSourceTitle = null,
        localRawLrc = localRawLrc,
        localTranslation = localTranslation,
        associatedMusicJson = associatedMusicJson,
        userOffsetMs = 0L,
        updatedAt = 100L,
    )
}
