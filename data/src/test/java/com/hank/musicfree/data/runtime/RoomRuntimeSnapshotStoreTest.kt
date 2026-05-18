package com.hank.musicfree.data.runtime

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class RoomRuntimeSnapshotStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RoomRuntimeSnapshotStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        store = RoomRuntimeSnapshotStore(db.runtimeSnapshotDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun writeThenReadReturnsSnapshot() = runTest {
        val snapshot = RuntimeSnapshot(
            namespace = "search_session",
            key = "search:music:demo:hash",
            snapshotVersion = 1,
            sourceSignature = "plugin:demo:1",
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
            expiresAtEpochMs = 3,
            payloadJson = """{"query":"hello"}""",
        )

        store.write(snapshot)

        assertEquals(snapshot, store.read("search_session", "search:music:demo:hash"))
    }

    @Test
    fun deleteExpiredRemovesOnlyExpiredRows() = runTest {
        store.write(sample("a", expiresAt = 100))
        store.write(sample("b", expiresAt = 300))
        store.write(sample("c", expiresAt = null))

        val deleted = store.deleteExpired("search_session", nowEpochMs = 200)

        assertEquals(1, deleted)
        assertNull(store.read("search_session", "a"))
        assertEquals("b", store.read("search_session", "b")?.key)
        assertEquals("c", store.read("search_session", "c")?.key)
    }

    @Test
    fun pruneNamespaceKeepsLatestUpdatedRows() = runTest {
        store.write(sample("a", updatedAt = 1))
        store.write(sample("b", updatedAt = 2))
        store.write(sample("c", updatedAt = 3))

        val deleted = store.pruneNamespace("search_session", keepLatest = 2)

        assertEquals(1, deleted)
        assertNull(store.read("search_session", "a"))
        assertEquals(listOf("c", "b"), store.keys("search_session", limit = 10))
    }

    @Test
    fun keysWithZeroOrNegativeLimitReturnsEmptyList() = runTest {
        store.write(sample("a", updatedAt = 1))
        store.write(sample("b", updatedAt = 2))

        assertEquals(emptyList<String>(), store.keys("search_session", limit = 0))
        assertEquals(emptyList<String>(), store.keys("search_session", limit = -1))
        assertEquals(emptyList<String>(), store.keys("unknown_namespace", limit = 5))
    }

    @Test
    fun keysTieByUpdatedAtOrdersByKeyAsc() = runTest {
        store.write(sample("z", updatedAt = 10))
        store.write(sample("a", updatedAt = 10))
        store.write(sample("m", updatedAt = 9))

        assertEquals(listOf("a", "z", "m"), store.keys("search_session", limit = 10))
    }

    private fun sample(
        key: String,
        updatedAt: Long = 1,
        expiresAt: Long? = 1_000,
    ): RuntimeSnapshot = RuntimeSnapshot(
        namespace = "search_session",
        key = key,
        snapshotVersion = 1,
        sourceSignature = "source",
        createdAtEpochMs = 1,
        updatedAtEpochMs = updatedAt,
        expiresAtEpochMs = expiresAt,
        payloadJson = "{}",
    )
}
