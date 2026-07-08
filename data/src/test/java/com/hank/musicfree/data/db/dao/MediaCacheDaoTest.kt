package com.hank.musicfree.data.db.dao

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.withAppSQLiteDriver
import com.hank.musicfree.data.db.entity.ListenEventEntity
import com.hank.musicfree.data.db.entity.MediaCacheEntity
import com.hank.musicfree.data.db.entity.MusicItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaCacheDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).withAppSQLiteDriver().allowMainThreadQueries().build()
        dao = db.mediaCacheDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kuwo", "2", "{}", 200))
        dao.upsert(MediaCacheEntity("kugou", "3", "{}", 300))

        dao.deleteByPlatform("kuwo")

        assertNull(dao.get("kuwo", "1"))
        assertNull(dao.get("kuwo", "2"))
        assertNotNull(dao.get("kugou", "3"))
        assertEquals(1, dao.count())
    }

    @Test fun `delete removes only matching row`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kuwo", "2", "{}", 200))
        dao.delete("kuwo", "1")
        assertNull(dao.get("kuwo", "1"))
        assertNotNull(dao.get("kuwo", "2"))
    }

    @Test fun `deleteAll removes every row`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kugou", "2", "{}", 200))

        dao.deleteAll()

        assertEquals(0, dao.count())
    }

    @Test fun `totalSizeBytes sums source json bytes and oldest entries are ordered`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "12345", 300))
        dao.upsert(MediaCacheEntity("kuwo", "2", "123", 100))
        dao.upsert(MediaCacheEntity("kuwo", "3", "1234", 200))

        assertEquals(12L, dao.totalSizeBytes())
        assertEquals(listOf("2", "3", "1"), dao.getOldestEntries().map { it.id })
    }

    @Test fun `listCatalogRows orders rows and returns display fallbacks`() = runTest {
        val cacheSource = """{"STANDARD":{"url":"https://source.test/cache.mp3"}}"""
        val librarySource = """{"HIGH":{"url":"https://source.test/library.mp3"}}"""
        val listenSource = """{"LOW":{"url":"https://source.test/listen.mp3"}}"""

        db.musicDao().insert(
            musicItem(
                id = "cache",
                title = "Library Should Lose",
                artist = "Library Artist Should Lose",
                album = "Library Album Should Lose",
                artwork = "library-art-should-lose",
                duration = 111L,
            ),
        )
        db.listenStatsDao().insertEvent(
            listenEvent(
                musicId = "cache",
                title = "Listen Should Lose",
                artistRaw = "Listen Artist Should Lose",
                album = "Listen Album Should Lose",
                artwork = "listen-art-should-lose",
                durationMs = 222L,
                playedAtMs = 100L,
            ),
        )
        dao.upsert(
            MediaCacheEntity(
                platform = "kg",
                id = "cache",
                sourcesJson = cacheSource,
                updatedAt = 300L,
                title = "Cache Title",
                artist = "Cache Artist",
                album = "Cache Album",
                artwork = "cache-art",
                durationMs = 3_000L,
            ),
        )

        db.musicDao().insert(
            musicItem(
                id = "library",
                title = "Library Title",
                artist = "Library Artist",
                album = "Library Album",
                artwork = "library-art",
                duration = 4_444L,
            ),
        )
        db.listenStatsDao().insertEvent(
            listenEvent(
                musicId = "library",
                title = "Listen Should Lose",
                artistRaw = "Listen Artist Should Lose",
                album = "Listen Album Should Lose",
                artwork = "listen-art-should-lose",
                durationMs = 5_555L,
                playedAtMs = 110L,
            ),
        )
        dao.upsert(
            MediaCacheEntity(
                platform = "kg",
                id = "library",
                sourcesJson = librarySource,
                updatedAt = 200L,
            ),
        )

        db.listenStatsDao().insertEvent(
            listenEvent(
                musicId = "listen",
                title = "Old Listen Title",
                artistRaw = "Old Listen Artist",
                album = "Old Listen Album",
                artwork = "old-listen-art",
                durationMs = 7_777L,
                playedAtMs = 100L,
            ),
        )
        db.listenStatsDao().insertEvent(
            listenEvent(
                musicId = "listen",
                title = "Latest Listen Title",
                artistRaw = "Latest Listen Artist",
                album = "Latest Listen Album",
                artwork = "latest-listen-art",
                durationMs = 8_888L,
                playedAtMs = 200L,
            ),
        )
        db.listenStatsDao().insertEvent(
            listenEvent(
                musicId = "listen",
                title = "Tie Latest Listen Title",
                artistRaw = "Tie Latest Listen Artist",
                album = "Tie Latest Listen Album",
                artwork = "tie-latest-listen-art",
                durationMs = 9_999L,
                playedAtMs = 200L,
            ),
        )
        dao.upsert(
            MediaCacheEntity(
                platform = "kg",
                id = "listen",
                sourcesJson = listenSource,
                updatedAt = 100L,
            ),
        )

        val rows = dao.listCatalogRows()

        assertEquals(listOf("cache", "library", "listen"), rows.map { it.itemId })

        val cache = rows[0]
        assertEquals("Cache Title", cache.title)
        assertEquals("Cache Artist", cache.artist)
        assertEquals("Cache Album", cache.album)
        assertEquals("cache-art", cache.artwork)
        assertEquals(3_000L, cache.durationMs)
        assertEquals("Library Should Lose", cache.libraryTitle)
        assertEquals("Listen Should Lose", cache.listenTitle)
        assertEquals(cacheSource.toByteArray().size.toLong(), cache.sourceMetadataBytes)

        val library = rows[1]
        assertNull(library.title)
        assertEquals("Library Title", library.libraryTitle)
        assertEquals("Library Artist", library.libraryArtist)
        assertEquals("Library Album", library.libraryAlbum)
        assertEquals("library-art", library.libraryArtwork)
        assertEquals(4_444L, library.libraryDurationMs)
        assertEquals("Listen Should Lose", library.listenTitle)
        assertEquals(librarySource.toByteArray().size.toLong(), library.sourceMetadataBytes)

        val listen = rows[2]
        assertNull(listen.title)
        assertNull(listen.libraryTitle)
        assertEquals("Tie Latest Listen Title", listen.listenTitle)
        assertEquals("Tie Latest Listen Artist", listen.listenArtist)
        assertEquals("Tie Latest Listen Album", listen.listenAlbum)
        assertEquals("tie-latest-listen-art", listen.listenArtwork)
        assertEquals(9_999L, listen.listenDurationMs)
        assertEquals(listenSource.toByteArray().size.toLong(), listen.sourceMetadataBytes)
    }

    private fun musicItem(
        id: String,
        title: String,
        artist: String,
        album: String?,
        artwork: String?,
        duration: Long,
        platform: String = "kg",
    ): MusicItemEntity = MusicItemEntity(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        url = null,
        artwork = artwork,
        qualitiesJson = null,
    )

    private fun listenEvent(
        musicId: String,
        title: String,
        artistRaw: String,
        album: String?,
        artwork: String?,
        durationMs: Long,
        playedAtMs: Long,
        platform: String = "kg",
    ): ListenEventEntity = ListenEventEntity(
        playedAtMs = playedAtMs,
        musicId = musicId,
        platform = platform,
        title = title,
        artistRaw = artistRaw,
        album = album,
        artwork = artwork,
        durationMs = durationMs,
        playedSeconds = 60,
        completed = true,
        language = null,
        genre = null,
        mergeKey = "$platform:$musicId:$playedAtMs:$title",
    )
}
