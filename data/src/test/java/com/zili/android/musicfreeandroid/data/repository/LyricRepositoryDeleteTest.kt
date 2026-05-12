package com.zili.android.musicfreeandroid.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricRepositoryDeleteTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LyricRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LyricRepository(db.lyricCacheDao(), Converters())
    }

    @After fun tearDown() { db.close() }

    @Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
        val kuwoA = music("1", "kuwo")
        val kuwoB = music("2", "kuwo")
        val kugou = music("3", "kugou")
        repository.saveRemoteLyric(kuwoA, LyricSourceInfo.Plugin("kuwo"), RawLyricPayload(rawLrc = "a"))
        repository.saveRemoteLyric(kuwoB, LyricSourceInfo.Plugin("kuwo"), RawLyricPayload(rawLrc = "b"))
        repository.saveRemoteLyric(kugou, LyricSourceInfo.Plugin("kugou"), RawLyricPayload(rawLrc = "c"))

        repository.deleteByPlatform("kuwo")

        assertNull(repository.getCache(kuwoA))
        assertNull(repository.getCache(kuwoB))
        assertNotNull(repository.getCache(kugou))
    }

    private fun music(id: String, platform: String) = MusicItem(
        id = id,
        platform = platform,
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
