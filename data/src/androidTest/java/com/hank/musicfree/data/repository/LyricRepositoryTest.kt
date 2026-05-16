package com.hank.musicfree.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RawLyricPayload
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.LyricCacheEntity
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
        assertEquals("plugin", cache?.remoteSourceType)
        assertEquals("demo", cache?.remoteSourcePlatform)
    }

    @Test
    fun saveRemoteLyricKeepsLocalAssociationAndOffset() = runTest {
        val music = musicItem("1", "demo")
        val target = musicItem("t-1", "lyric")

        repository.importLocalLyric(music, "local", LocalLyricKind.Raw)
        repository.associateLyric(music, target)
        repository.setLyricOffset(music, 123L)

        repository.saveRemoteLyric(
            music = music,
            source = LyricSourceInfo.Plugin("demo"),
            payload = RawLyricPayload(rawLrc = "remote"),
        )

        val cache = repository.getCache(music)
        assertEquals("remote", cache?.remotePayload?.rawLrc)
        assertEquals("local", cache?.localRawLrc)
        assertEquals(target.id, cache?.associatedMusic?.id)
        assertEquals(123L, cache?.userOffsetMs)
    }

    @Test
    fun importLocalLyricKeepsRemoteCache() = runTest {
        val music = musicItem("1", "demo")
        repository.saveRemoteLyric(music, LyricSourceInfo.Plugin("demo"), RawLyricPayload(rawLrc = "remote"))
        repository.associateLyric(music, musicItem("t-1", "lyric"))
        repository.setLyricOffset(music, 321L)

        repository.importLocalLyric(music, "local", LocalLyricKind.Translation)

        val cache = repository.getCache(music)
        assertEquals("remote", cache?.remotePayload?.rawLrc)
        assertEquals("local", cache?.localTranslation)
        assertEquals("t-1", cache?.associatedMusic?.id)
        assertEquals(321L, cache?.userOffsetMs)
    }

    @Test
    fun importLocalLyricKeepsRemoteCacheWithNoRow() = runTest {
        val music = musicItem("2", "demo")
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
    fun clearAssociatedLyricDoesNotCreateRowWhenMissing() = runTest {
        val music = musicItem("2", "demo")

        repository.clearAssociatedLyric(music)

        assertNull(db.lyricCacheDao().getByKey("demo", "2"))
    }

    @Test
    fun deleteLocalLyricDoesNotCreateRowWhenMissing() = runTest {
        val music = musicItem("3", "demo")

        repository.deleteLocalLyric(music)

        assertNull(db.lyricCacheDao().getByKey("demo", "3"))
    }

    @Test
    fun setOffsetCreatesCacheRow() = runTest {
        val music = musicItem("1", "demo")

        repository.setLyricOffset(music, 500L)

        assertEquals(500L, repository.getCache(music)?.userOffsetMs)
    }

    @Test
    fun associatedMusicJsonCorruptJsonReturnsNull() = runTest {
        val music = musicItem("4", "demo")
        db.lyricCacheDao().insertIgnore(
            LyricCacheEntity(
                musicId = music.id,
                musicPlatform = music.platform,
                remoteRawLrc = null,
                remoteRawLrcTxt = null,
                remoteTranslation = null,
                remoteSourceType = null,
                remoteSourcePlatform = null,
                remoteSourceMusicId = null,
                remoteSourceTitle = null,
                localRawLrc = null,
                localTranslation = null,
                associatedMusicJson = "{invalid-json",
                userOffsetMs = 0L,
                updatedAt = 1L,
            ),
        )

        val cache = repository.getCache(music)

        assertNull(cache?.associatedMusic)
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
