package com.zili.android.musicfreeandroid.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LyricCacheDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.lyricCacheDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
        dao.upsert(makeEntity(id = "1", platform = "kuwo"))
        dao.upsert(makeEntity(id = "2", platform = "kuwo"))
        dao.upsert(makeEntity(id = "3", platform = "kugou"))

        dao.deleteByPlatform("kuwo")

        assertNull(dao.getByKey("kuwo", "1"))
        assertNull(dao.getByKey("kuwo", "2"))
        assertNotNull(dao.getByKey("kugou", "3"))
    }

    @Test fun `deleteAll removes every row`() = runTest {
        dao.upsert(makeEntity(id = "1", platform = "kuwo"))
        dao.upsert(makeEntity(id = "2", platform = "kugou"))

        dao.deleteAll()

        assertNull(dao.getByKey("kuwo", "1"))
        assertNull(dao.getByKey("kugou", "2"))
    }

    private fun makeEntity(id: String, platform: String): LyricCacheEntity = LyricCacheEntity(
        musicId = id,
        musicPlatform = platform,
        remoteRawLrc = null,
        remoteRawLrcTxt = null,
        remoteTranslation = null,
        remoteSourceType = null,
        remoteSourcePlatform = null,
        remoteSourceMusicId = null,
        remoteSourceTitle = null,
        localRawLrc = null,
        localTranslation = null,
        associatedMusicJson = null,
        userOffsetMs = 0L,
        updatedAt = 0L,
    )
}
