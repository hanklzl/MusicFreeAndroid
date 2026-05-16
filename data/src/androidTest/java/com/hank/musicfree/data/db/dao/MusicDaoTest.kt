package com.hank.musicfree.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.MusicItemEntity
import com.hank.musicfree.data.db.entity.PlaylistEntity
import com.hank.musicfree.data.db.entity.PlaylistMusicCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MusicDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.musicDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun entity(id: String = "1", platform: String = "local", title: String = "Song") =
        MusicItemEntity(id, platform, title, "Artist", null, 180_000, null, null, null)

    @Test
    fun insertAndGetById() = runTest {
        val item = entity()
        dao.insert(item)
        val result = dao.getById("1", "local")
        assertNotNull(result)
        assertEquals("Song", result!!.title)
    }

    @Test
    fun insertReplaceOnConflict() = runTest {
        dao.insert(entity(title = "Original"))
        dao.insert(entity(title = "Updated"))
        val result = dao.getById("1", "local")
        assertEquals("Updated", result!!.title)
    }

    @Test
    fun deleteItem() = runTest {
        val item = entity()
        dao.insert(item)
        dao.delete(item)
        assertNull(dao.getById("1", "local"))
    }

    @Test
    fun observeAll() = runTest {
        dao.insert(entity("1", title = "B Song"))
        dao.insert(entity("2", title = "A Song"))
        val items = dao.observeAll().first()
        assertEquals(2, items.size)
        assertEquals("A Song", items[0].title)
    }

    @Test
    fun observeByPlatform() = runTest {
        dao.insert(entity("1", "local"))
        dao.insert(entity("2", "netease"))
        dao.insert(entity("3", "local"))
        val locals = dao.observeByPlatform("local").first()
        assertEquals(2, locals.size)
    }

    @Test
    fun count() = runTest {
        assertEquals(0, dao.count())
        dao.insert(entity("1"))
        dao.insert(entity("2"))
        assertEquals(2, dao.count())
    }

    @Test
    fun deleteByPlatform() = runTest {
        dao.insert(entity("1", "local"))
        dao.insert(entity("2", "netease"))
        dao.insert(entity("3", "local"))
        dao.deleteByPlatform("local")
        assertEquals(1, dao.count())
        assertNotNull(dao.getById("2", "netease"))
    }

    @Test
    fun insertAll() = runTest {
        dao.insertAll(listOf(entity("1"), entity("2"), entity("3")))
        assertEquals(3, dao.count())
    }

    @Test
    fun replaceByPlatform_keepsCrossRefsForRetainedItems() = runTest {
        val playlistDao = db.playlistDao()
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = "playlist-1",
                name = "Local favorites",
                coverUri = null,
                description = null,
                sortMode = "Manual",
                createdAt = 0L,
                updatedAt = 0L,
            )
        )
        dao.insert(entity("1", platform = "local", title = "Old Song"))
        dao.insert(entity("2", platform = "local", title = "Deleted Song"))
        playlistDao.insertCrossRef(
            PlaylistMusicCrossRef(
                playlistId = "playlist-1",
                musicId = "1",
                musicPlatform = "local",
                sortOrder = 0,
            )
        )

        dao.replaceByPlatform("local", listOf(entity("1", platform = "local", title = "Updated Song")))

        assertEquals("Updated Song", dao.getById("1", "local")!!.title)
        assertNull(dao.getById("2", "local"))
        assertEquals(1, playlistDao.countMusicInPlaylist("playlist-1"))
    }
}
