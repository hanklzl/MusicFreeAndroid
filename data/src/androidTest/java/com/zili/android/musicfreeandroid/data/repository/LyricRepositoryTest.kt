package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LyricRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LyricRepository(db.lyricCacheDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun saveRemoteLyricWritesCache() = runTest {
        val music = musicItem("1", "demo")

        repository.saveRemoteLyric(
            music = music,
            source = LyricSourceInfo.Plugin("demo"),
            payload = RawLyricPayload(rawLrc = "[00:01.00]Hello", translation = "[00:01.00]你好"),
        )

        val cache = repository.observeCache(music).first()
        assertEquals("[00:01.00]Hello", cache?.remotePayload?.rawLrc)
        assertEquals("[00:01.00]你好", cache?.remotePayload?.translation)
        assertEquals("demo", cache?.remoteSourcePlatform)
    }

    @Test
    fun importLocalLyricKeepsRemoteCache() = runTest {
        val music = musicItem("1", "demo")
        repository.saveRemoteLyric(music, LyricSourceInfo.Plugin("demo"), RawLyricPayload(rawLrc = "remote"))

        repository.importLocalLyric(music, "local", LocalLyricKind.Raw)

        val cache = repository.getCache(music)
        assertEquals("remote", cache?.remotePayload?.rawLrc)
        assertEquals("local", cache?.localRawLrc)
    }

    @Test
    fun associateAndClearLyric() = runTest {
        val music = musicItem("1", "demo")
        val target = musicItem("lrc-1", "lyric")

        repository.associateLyric(music, target)
        assertEquals(target.id, repository.getCache(music)?.associatedMusic?.id)

        repository.clearAssociatedLyric(music)
        assertNull(repository.getCache(music)?.associatedMusic)
    }

    @Test
    fun setOffsetCreatesCacheRow() = runTest {
        val music = musicItem("1", "demo")

        repository.setLyricOffset(music, 500L)

        assertEquals(500L, repository.getCache(music)?.userOffsetMs)
    }

    private fun musicItem(id: String, platform: String) = MusicItem(
        id = id,
        platform = platform,
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
