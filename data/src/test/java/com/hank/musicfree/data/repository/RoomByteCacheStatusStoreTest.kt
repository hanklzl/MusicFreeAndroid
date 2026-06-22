package com.hank.musicfree.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.cache.ByteCacheInvalidReason
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatus
import com.hank.musicfree.core.cache.ByteCacheValidationMethod
import com.hank.musicfree.core.cache.ByteCacheValidity
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomByteCacheStatusStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RoomByteCacheStatusStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomByteCacheStatusStore(db.byteCacheStatusDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert and get round trips core model`() = runTest {
        val status = ByteCacheStatus(
            key = key(),
            validity = ByteCacheValidity.PlayableVerified,
            cachedBytes = 512L,
            contentLength = 512L,
            validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
            sourceFingerprint = "fp",
            invalidReason = null,
            verifiedAt = 100L,
            updatedAt = 200L,
        )

        store.upsert(status)

        assertEquals(status, store.get(key()))
    }

    @Test
    fun `markInvalid writes stale invalid model`() = runTest {
        store.markInvalid(key(), ByteCacheInvalidReason.HttpBadStatus, updatedAt = 300L)

        val status = store.get(key())

        assertEquals(ByteCacheValidity.StaleOrInvalid, status?.validity)
        assertEquals(ByteCacheInvalidReason.HttpBadStatus, status?.invalidReason)
        assertEquals(ByteCacheValidationMethod.StaleFailure, status?.validationMethod)
        assertEquals(300L, status?.updatedAt)
    }

    @Test
    fun `deleteBySong removes all qualities`() = runTest {
        store.upsert(status(key(PlayQuality.STANDARD)))
        store.upsert(status(key(PlayQuality.HIGH)))

        store.deleteBySong("qq", "song-1")

        assertNull(store.get(key(PlayQuality.STANDARD)))
        assertNull(store.get(key(PlayQuality.HIGH)))
    }

    @Test
    fun `deleteByPlatform removes only platform statuses`() = runTest {
        store.upsert(status(key(platform = "qq", musicId = "song-1")))
        store.upsert(status(key(platform = "kuwo", musicId = "song-2")))

        store.deleteByPlatform("qq")

        assertNull(store.get(key(platform = "qq", musicId = "song-1")))
        assertEquals(status(key(platform = "kuwo", musicId = "song-2")), store.get(key(platform = "kuwo", musicId = "song-2")))
    }

    @Test
    fun `deleteAll removes all statuses`() = runTest {
        store.upsert(status(key(platform = "qq", musicId = "song-1")))
        store.upsert(status(key(platform = "kuwo", musicId = "song-2")))

        store.deleteAll()

        assertNull(store.get(key(platform = "qq", musicId = "song-1")))
        assertNull(store.get(key(platform = "kuwo", musicId = "song-2")))
    }

    private fun status(key: ByteCacheKey) = ByteCacheStatus(
        key = key,
        validity = ByteCacheValidity.Partial,
        cachedBytes = 100L,
        contentLength = 200L,
        validationMethod = ByteCacheValidationMethod.SpanInspection,
        sourceFingerprint = null,
        invalidReason = null,
        verifiedAt = null,
        updatedAt = 1L,
    )

    private fun key(
        quality: PlayQuality = PlayQuality.STANDARD,
        platform: String = "qq",
        musicId: String = "song-1",
    ) = ByteCacheKey(
        platform = platform,
        musicId = musicId,
        quality = quality,
    )
}
