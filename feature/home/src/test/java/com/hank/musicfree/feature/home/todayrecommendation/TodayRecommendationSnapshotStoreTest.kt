package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayRecommendationSnapshotStoreTest {

    @Test
    fun `round trips nested raw fields and personalized reason`() = runTest {
        val backing = FakeSnapshotStore()
        val store = TodayRecommendationSnapshotStore(backing, nowEpochMs = { 1_720_000_000_000L })
        val date = LocalDate.of(2026, 7, 11)
        val snapshot = DailyRecommendationSnapshot(
            date = date,
            profileSignature = "profile-a",
            confidence = ProfileConfidence.ESTABLISHED,
            createdAtEpochMs = 1_720_000_000_000L,
            updatedAtEpochMs = 1_720_000_000_000L,
            items = listOf(
                RecommendedSheet(
                    sheet = sheet(
                        raw = mapOf(
                            "id" to 42L,
                            "filters" to listOf("rock", "live"),
                            "meta" to mapOf("region" to "CN"),
                        ),
                    ),
                    reason = "因为你常听摇滚",
                    score = 98.5,
                ),
            ),
        )

        store.write(snapshot, sourceSignature = "source-a")
        val restored = store.readCurrent(date, expectedSourceSignature = "source-a")

        assertEquals("因为你常听摇滚", restored?.items?.single()?.reason)
        assertEquals(42L, restored?.items?.single()?.sheet?.raw?.get("id"))
        assertEquals(listOf("rock", "live"), restored?.items?.single()?.sheet?.raw?.get("filters"))
        assertEquals(mapOf("region" to "CN"), restored?.items?.single()?.sheet?.raw?.get("meta"))
    }

    @Test
    fun `source signature mismatch invalidates current snapshot but remains fallback`() = runTest {
        val backing = FakeSnapshotStore()
        val store = TodayRecommendationSnapshotStore(backing, nowEpochMs = { 2_000L })
        val date = LocalDate.of(2026, 7, 11)
        store.write(
            DailyRecommendationSnapshot(
                date = date,
                profileSignature = "profile-a",
                confidence = ProfileConfidence.LOW,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
                items = listOf(RecommendedSheet(sheet(), "热门推荐", 20.0)),
            ),
            sourceSignature = "source-a",
        )

        assertNull(store.readCurrent(date, expectedSourceSignature = "source-b"))
        assertEquals(date, store.readLatestFallback(beforeOrOnDate = date)?.date)
    }

    private fun sheet(raw: Map<String, Any?> = mapOf("id" to "sheet-1")) = MusicSheetItemBase(
        id = "sheet-1",
        platform = "qq",
        title = "摇滚现场",
        artist = "编辑精选",
        description = "desc",
        coverImg = "https://example.com/cover.jpg",
        artwork = null,
        worksNum = 30,
        raw = raw,
    )

    private class FakeSnapshotStore : SnapshotStore {
        private val snapshots = mutableMapOf<Pair<String, String>, RuntimeSnapshot>()

        override suspend fun read(namespace: String, key: String) = snapshots[namespace to key]

        override suspend fun write(snapshot: RuntimeSnapshot) {
            snapshots[snapshot.namespace to snapshot.key] = snapshot
        }

        override suspend fun delete(namespace: String, key: String) {
            snapshots.remove(namespace to key)
        }

        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int {
            val keys = snapshots.filterValues { it.namespace == namespace && it.isExpired(nowEpochMs) }.keys
            keys.forEach(snapshots::remove)
            return keys.size
        }

        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int {
            val stale = snapshots.values
                .filter { it.namespace == namespace }
                .sortedByDescending { it.updatedAtEpochMs }
                .drop(keepLatest)
            stale.forEach { snapshots.remove(it.namespace to it.key) }
            return stale.size
        }

        override suspend fun keys(namespace: String, limit: Int): List<String> = snapshots.values
            .filter { it.namespace == namespace }
            .sortedByDescending { it.updatedAtEpochMs }
            .take(limit)
            .map { it.key }
    }
}
