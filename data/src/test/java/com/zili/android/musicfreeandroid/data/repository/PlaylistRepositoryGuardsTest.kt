package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.cover.PlaylistCoverStore
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class PlaylistRepositoryGuardsTest {
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val musicDao: MusicDao = mockk(relaxed = true)
    private val coverStore: PlaylistCoverStore = mockk(relaxed = true)
    private val converters = Converters()
    private val repo = PlaylistRepository(playlistDao, musicDao, coverStore, converters)

    @Test fun deletePlaylist_throwsForFavorite() = runTest {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repo.deletePlaylist(Playlist(id = "favorite", name = "我喜欢", coverUri = null))
            }
        }
    }

    @Test fun updatePlaylistInfo_throwsWhenRenamingFavorite() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.updatePlaylistInfo(id = "favorite", name = "新名字", description = null)
            }
        }
    }

    @Test fun updatePlaylistInfo_allowsDescriptionEditOnFavorite() = runTest {
        coEvery { playlistDao.getPlaylistById("favorite") } returns
            com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity(
                id = "favorite", name = "我喜欢", coverUri = null,
                description = null, sortMode = "Manual", createdAt = 0L, updatedAt = 0L,
            )
        try {
            repo.updatePlaylistInfo(id = "favorite", name = null, description = "我的最爱")
        } catch (e: Throwable) {
            fail("description update on favorite should not throw, got $e")
        }
    }
}
