package com.hank.musicfree.data.repository

import com.hank.musicfree.core.cache.ByteCacheInvalidReason
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatus
import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.cache.ByteCacheValidationMethod
import com.hank.musicfree.core.cache.ByteCacheValidity
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.db.dao.MediaCacheDao
import com.hank.musicfree.data.db.dao.MediaCacheCatalogRow
import com.hank.musicfree.data.db.entity.MediaCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheRepositoryTest {

    private val item = MusicItem(
        id = "1", platform = "kg", title = "T", artist = "A",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test
    fun `get returns null when DAO has no row`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns null
        }
        val repo = MediaCacheRepository(dao)
        assertNull(repo.get(item, PlayQuality.STANDARD))
    }

    @Test
    fun `get returns CachedSource for matching quality`() = runTest {
        val json = """{"STANDARD":{"url":"http://a","headers":{"Referer":"r"},"userAgent":"ua"}}"""
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", json, 100L)
        }
        val repo = MediaCacheRepository(dao)
        val cached = repo.get(item, PlayQuality.STANDARD)
        assertNotNull(cached)
        assertEquals("http://a", cached?.url)
        assertEquals(mapOf("Referer" to "r"), cached?.headers)
        assertEquals("ua", cached?.userAgent)
    }

    @Test
    fun `get returns null when stored json lacks the requested quality`() = runTest {
        val json = """{"HIGH":{"url":"http://h"}}"""
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", json, 100L)
        }
        assertNull(MediaCacheRepository(dao).get(item, PlayQuality.STANDARD))
    }

    @Test
    fun `put merges new quality into existing json and bumps updatedAt`() = runTest {
        val existingJson = """{"STANDARD":{"url":"http://a"}}"""
        val capturedEntity = slot<MediaCacheEntity>()
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", existingJson, 100L)
            coEvery { upsert(capture(capturedEntity)) } returns Unit
            coEvery { totalSizeBytes() } returns 0L
            coEvery { count() } returns 1
        }
        val repo = MediaCacheRepository(dao) { 999L }
        repo.put(item, PlayQuality.HIGH, MediaSourceResult("http://h", null, null, PlayQuality.HIGH))

        val saved = capturedEntity.captured
        assertEquals(999L, saved.updatedAt)
        val parsed = org.json.JSONObject(saved.sourcesJson)
        assertEquals("http://a", parsed.getJSONObject("STANDARD").getString("url"))
        assertEquals("http://h", parsed.getJSONObject("HIGH").getString("url"))
    }

    @Test
    fun `put persists display metadata for cache catalog`() = runTest {
        val capturedEntity = slot<MediaCacheEntity>()
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns null
            coEvery { upsert(capture(capturedEntity)) } returns Unit
            coEvery { totalSizeBytes() } returns 0L
            coEvery { count() } returns 1
        }

        MediaCacheRepository(dao) { 123L }.put(
            item.copy(
                album = "Album",
                artwork = "https://img.test/a.jpg",
                duration = 180_000L,
            ),
            PlayQuality.STANDARD,
            MediaSourceResult("https://audio.test/a.mp3", null, null, PlayQuality.STANDARD),
        )

        val saved = capturedEntity.captured
        assertEquals("T", saved.title)
        assertEquals("A", saved.artist)
        assertEquals("Album", saved.album)
        assertEquals("https://img.test/a.jpg", saved.artwork)
        assertEquals(180_000L, saved.durationMs)
    }

    @Test
    fun `listOnlineCacheCatalog merges source qualities and byte cache statuses`() = runTest {
        val sourcesJson = """
            {
              "STANDARD": {"url": "https://audio.test/standard.mp3"},
              "HIGH": {"url": "https://audio.test/high.mp3"}
            }
        """.trimIndent()
        val dao: MediaCacheDao = mockk {
            coEvery { listCatalogRows() } returns listOf(
                catalogRow(
                    sourcesJson = sourcesJson,
                    updatedAt = 10L,
                    sourceMetadataBytes = 123L,
                    title = "Song",
                    artist = "Singer",
                ),
            )
        }
        val statusStore = RecordingByteCacheStatusStore().apply {
            statuses += byteCacheStatus(
                quality = PlayQuality.STANDARD,
                validity = ByteCacheValidity.PlayableVerified,
                cachedBytes = 4_096L,
                contentLength = 8_192L,
                updatedAt = 20L,
            )
        }

        val catalog = MediaCacheRepository(
            dao = dao,
            now = { 123L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = statusStore,
        ).listOnlineCacheCatalog()

        assertEquals(1, catalog.size)
        val song = catalog.single()
        assertEquals("Song", song.title)
        assertEquals("Singer", song.artist)
        assertEquals(4_219L, song.totalBytes)
        assertEquals(20L, song.updatedAt)
        assertEquals(2, song.qualities.size)

        val standard = song.qualities[0]
        assertEquals(PlayQuality.STANDARD, standard.quality)
        assertEquals(OnlineCacheQualityStatus.Reusable, standard.status)
        assertEquals(4_096L, standard.cachedBytes)
        assertEquals(8_192L, standard.contentLength)
        assertEquals(20L, standard.updatedAt)

        val high = song.qualities[1]
        assertEquals(PlayQuality.HIGH, high.quality)
        assertEquals(OnlineCacheQualityStatus.SourceOnly, high.status)
        assertEquals(0L, high.cachedBytes)
        assertNull(high.contentLength)
        assertEquals(10L, high.updatedAt)
    }

    @Test
    fun `listOnlineCacheCatalog falls back to unknown title for malformed json`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { listCatalogRows() } returns listOf(
                catalogRow(
                    sourcesJson = "{bad-json",
                    updatedAt = 5L,
                    sourceMetadataBytes = 9L,
                ),
            )
        }

        val catalog = MediaCacheRepository(
            dao = dao,
            now = { 123L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = RecordingByteCacheStatusStore(),
        ).listOnlineCacheCatalog()

        assertEquals(1, catalog.size)
        val song = catalog.single()
        assertEquals("未知歌曲", song.title)
        assertEquals("未知歌手", song.artist)
        assertEquals(9L, song.totalBytes)
        assertEquals(1, song.qualities.size)

        val quality = song.qualities.single()
        assertNull(quality.quality)
        assertEquals(OnlineCacheQualityStatus.Invalid, quality.status)
        assertEquals(0L, quality.cachedBytes)
        assertNull(quality.contentLength)
        assertEquals(5L, quality.updatedAt)
        assertNull(quality.invalidReason)
    }

    @Test
    fun `listOnlineCacheCatalog keeps stale invalid reason typed`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { listCatalogRows() } returns listOf(
                catalogRow(
                    sourcesJson = """{"HIGH":{"url":"https://audio.test/high.mp3"}}""",
                    updatedAt = 10L,
                    sourceMetadataBytes = 55L,
                    title = "Song",
                    artist = "Singer",
                ),
            )
        }
        val statusStore = RecordingByteCacheStatusStore().apply {
            statuses += byteCacheStatus(
                quality = PlayQuality.HIGH,
                validity = ByteCacheValidity.StaleOrInvalid,
                cachedBytes = 0L,
                contentLength = null,
                updatedAt = 20L,
                invalidReason = ByteCacheInvalidReason.BadByteCache,
            )
        }

        val quality = MediaCacheRepository(
            dao = dao,
            now = { 123L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = statusStore,
        ).listOnlineCacheCatalog().single().qualities.single()

        assertEquals(OnlineCacheQualityStatus.Invalid, quality.status)
        assertEquals(ByteCacheInvalidReason.BadByteCache, quality.invalidReason)
    }

    @Test
    fun `listOnlineCacheCatalog trims display fallback fields`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { listCatalogRows() } returns listOf(
                catalogRow(
                    sourcesJson = """{"STANDARD":{"url":"https://audio.test/standard.mp3"}}""",
                    updatedAt = 10L,
                    sourceMetadataBytes = 60L,
                    title = "   ",
                    artist = "  Cache Artist  ",
                    libraryTitle = "  Library Song  ",
                    listenTitle = "  Listen Song  ",
                ),
            )
        }

        val song = MediaCacheRepository(
            dao = dao,
            now = { 123L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = RecordingByteCacheStatusStore(),
        ).listOnlineCacheCatalog().single()

        assertEquals("Library Song", song.title)
        assertEquals("Cache Artist", song.artist)
    }

    @Test
    fun `put triggers deleteOldest(400) when count reaches limit of 800`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            coEvery { totalSizeBytes() } returns 0L
            coEvery { count() } returns 800
            coEvery { getOldestEntries() } returns listOf(MediaCacheEntity("kg", "old", "{}", 1L))
            coEvery { deleteOldest(400) } returns Unit
        }
        MediaCacheRepository(dao) { 1L }
            .put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))
        coVerify(exactly = 1) { dao.deleteOldest(400) }
    }

    @Test
    fun `put does not trigger deleteOldest when count below limit`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            coEvery { totalSizeBytes() } returns 0L
            coEvery { count() } returns 799
        }
        MediaCacheRepository(dao) { 1L }
            .put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))
        coVerify(exactly = 0) { dao.deleteOldest(any()) }
    }

    @Test
    fun `put trims oldest entries when byte limit is exceeded`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            coEvery { totalSizeBytes() } returns 120L
            coEvery { getOldestEntries() } returns listOf(
                MediaCacheEntity("kg", "old", "12345", 100L),
                MediaCacheEntity("kg", "new", "12345", 200L),
            )
            coEvery { delete("kg", "old") } returns Unit
            coEvery { count() } returns 1
        }

        MediaCacheRepository(
            dao = dao,
            now = { 300L },
            limitProvider = { 115L },
        ).put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))

        coVerify(exactly = 1) { dao.delete("kg", "old") }
        coVerify(exactly = 0) { dao.delete("kg", "new") }
    }

    @Test
    fun `put deletes byte cache status for metadata entries trimmed by byte limit`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            coEvery { totalSizeBytes() } returns 120L
            coEvery { getOldestEntries() } returns listOf(
                MediaCacheEntity("kg", "old", "12345", 100L),
                MediaCacheEntity("kg", "new", "12345", 200L),
            )
            coEvery { delete("kg", "old") } returns Unit
            coEvery { count() } returns 1
        }
        val statusStore = RecordingByteCacheStatusStore()

        MediaCacheRepository(
            dao = dao,
            now = { 300L },
            limitProvider = { 115L },
            byteCacheStatusStore = statusStore,
        ).put(item, PlayQuality.STANDARD, MediaSourceResult("http://a", null, null, PlayQuality.STANDARD))

        assertEquals(listOf("kg" to "old"), statusStore.deletedSongs)
    }

    @Test
    fun `deleteEntry triggers onSimpleCacheEvict with quality`() = runTest {
        val json = """{"STANDARD":{"url":"http://a"}}"""
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", json, 100L)
            coEvery { delete("kg", "1") } returns Unit
        }
        val evictCalls = mutableListOf<Triple<String, String, PlayQuality?>>()
        val repo = MediaCacheRepository(
            dao = dao,
            now = { 1L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            onSimpleCacheEvict = { p, id, q -> evictCalls.add(Triple(p, id, q)) },
        )
        repo.deleteEntry("kg", "1", PlayQuality.STANDARD)
        assertEquals(1, evictCalls.size)
        val (p, id, q) = evictCalls[0]
        assertEquals("kg", p)
        assertEquals("1", id)
        assertEquals(PlayQuality.STANDARD, q)
    }

    @Test
    fun `deleteEntry deletes byte cache status for matching quality`() = runTest {
        val json = """{"STANDARD":{"url":"http://a"}}"""
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", json, 100L)
            coEvery { delete("kg", "1") } returns Unit
        }
        val statusStore = RecordingByteCacheStatusStore()
        val repo = MediaCacheRepository(
            dao = dao,
            now = { 1L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = statusStore,
        )

        repo.deleteEntry("kg", "1", PlayQuality.STANDARD)

        assertEquals(listOf(ByteCacheKey("kg", "1", PlayQuality.STANDARD)), statusStore.deletedKeys)
    }

    @Test
    fun `deleteEntry clears status-only quality when source row lacks quality`() = runTest {
        val json = """{"STANDARD":{"url":"http://a"}}"""
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity("kg", "1", json, 100L)
        }
        val statusStore = RecordingByteCacheStatusStore()
        val evictCalls = mutableListOf<Triple<String, String, PlayQuality?>>()
        val repo = MediaCacheRepository(
            dao = dao,
            now = { 1L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            onSimpleCacheEvict = { p, id, q -> evictCalls.add(Triple(p, id, q)) },
            byteCacheStatusStore = statusStore,
        )

        repo.deleteEntry("kg", "1", PlayQuality.HIGH)

        assertEquals(listOf(Triple("kg", "1", PlayQuality.HIGH)), evictCalls)
        assertEquals(listOf(ByteCacheKey("kg", "1", PlayQuality.HIGH)), statusStore.deletedKeys)
        coVerify(exactly = 0) { dao.upsert(any()) }
        coVerify(exactly = 0) { dao.delete(any(), any()) }
    }

    @Test
    fun `deleteEntry preserves display metadata when remaining qualities are reupserted`() = runTest {
        val json = """{"STANDARD":{"url":"http://a"},"HIGH":{"url":"http://h"}}"""
        val capturedEntity = slot<MediaCacheEntity>()
        val dao: MediaCacheDao = mockk {
            coEvery { get("kg", "1") } returns MediaCacheEntity(
                platform = "kg",
                id = "1",
                sourcesJson = json,
                updatedAt = 100L,
                title = "Title",
                artist = "Artist",
                album = "Album",
                artwork = "https://img.test/a.jpg",
                durationMs = 180_000L,
            )
            coEvery { upsert(capture(capturedEntity)) } returns Unit
        }

        MediaCacheRepository(dao) { 200L }.deleteEntry("kg", "1", PlayQuality.HIGH)

        val saved = capturedEntity.captured
        assertEquals("Title", saved.title)
        assertEquals("Artist", saved.artist)
        assertEquals("Album", saved.album)
        assertEquals("https://img.test/a.jpg", saved.artwork)
        assertEquals(180_000L, saved.durationMs)
        assertEquals(200L, saved.updatedAt)

        val parsed = org.json.JSONObject(saved.sourcesJson)
        assertTrue(parsed.has("STANDARD"))
        assertFalse(parsed.has("HIGH"))
    }

    @Test
    fun `deleteItem triggers onSimpleCacheEvict with null quality`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { delete("kg", "1") } returns Unit
        }
        val evictCalls = mutableListOf<Triple<String, String, PlayQuality?>>()
        val repo = MediaCacheRepository(
            dao = dao,
            now = { 1L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            onSimpleCacheEvict = { p, id, q -> evictCalls.add(Triple(p, id, q)) },
        )
        repo.deleteItem("kg", "1")
        assertEquals(1, evictCalls.size)
        val (p, id, q) = evictCalls[0]
        assertEquals("kg", p)
        assertEquals("1", id)
        assertTrue("quality should be null for deleteItem", q == null)
    }

    @Test
    fun `deleteItem deletes byte cache status for the song`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { delete("kg", "1") } returns Unit
        }
        val statusStore = RecordingByteCacheStatusStore()
        val repo = MediaCacheRepository(
            dao = dao,
            now = { 1L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = statusStore,
        )

        repo.deleteItem("kg", "1")

        assertEquals(listOf("kg" to "1"), statusStore.deletedSongs)
    }

    @Test
    fun `deleteByPlatform deletes byte cache statuses for the platform`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { deleteByPlatform("kg") } returns Unit
        }
        val statusStore = RecordingByteCacheStatusStore()
        val repo = MediaCacheRepository(
            dao = dao,
            now = { 1L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = statusStore,
        )

        repo.deleteByPlatform("kg")

        assertEquals(listOf("kg"), statusStore.deletedPlatforms)
    }

    @Test
    fun `clearAll deletes all byte cache statuses`() = runTest {
        val dao: MediaCacheDao = mockk {
            coEvery { deleteAll() } returns Unit
        }
        val statusStore = RecordingByteCacheStatusStore()
        val repo = MediaCacheRepository(
            dao = dao,
            now = { 1L },
            limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
            byteCacheStatusStore = statusStore,
        )

        repo.clearAll()

        assertEquals(1, statusStore.deleteAllCount)
    }

    // ---- 10% sub-quota tests (Task 5.4) ----

    @Test
    fun `create factory derives 10 percent quota from maxMusicCacheSizeBytes`() = runTest {
        // When maxMusicCacheSizeBytes = 1000, limitProvider() must return 100 (10%)
        val resolvedLimit = (1000L / MediaCacheRepository.REPO_QUOTA_DIVISOR).coerceAtLeast(1L)
        assertEquals(100L, resolvedLimit)
    }

    @Test
    fun `put trims oversize entries when 10 percent quota is applied`() = runTest {
        // Total budget = 1000 bytes → repo quota = 100 bytes.
        // totalSizeBytes() returns 120 (exceeds quota), so pruneToLimit must delete
        // the oldest entry (40 bytes) to bring total down to 80, which is within quota.
        val oldEntry = MediaCacheEntity("kg", "old", "1".repeat(40), 100L)
        val newEntry = MediaCacheEntity("kg", "new", "2".repeat(40), 200L)

        // pruneToLimit iterates getOldestEntries() and deletes until totalBytes <= limit.
        // Use a fixed list — do NOT mutate it inside the mock to avoid ConcurrentModificationException.
        val dao: MediaCacheDao = mockk {
            coEvery { get(any(), any()) } returns null
            coEvery { upsert(any()) } returns Unit
            // After the put, total is 120 bytes (exceeds 100-byte quota)
            coEvery { totalSizeBytes() } returns 120L
            coEvery { getOldestEntries() } returns listOf(oldEntry, newEntry)
            coEvery { delete("kg", "old") } returns Unit
            coEvery { count() } returns 2
        }

        MediaCacheRepository(
            dao = dao,
            now = { 400L },
            limitProvider = { (1000L / MediaCacheRepository.REPO_QUOTA_DIVISOR).coerceAtLeast(1L) },
        ).put(
            item,
            PlayQuality.STANDARD,
            MediaSourceResult("http://x", null, null, PlayQuality.STANDARD),
        )

        // The oldest entry must have been deleted to bring the total under 100 bytes
        coVerify(exactly = 1) { dao.delete("kg", "old") }
        coVerify(exactly = 0) { dao.delete("kg", "new") }
    }

    private class RecordingByteCacheStatusStore : ByteCacheStatusStore {
        val statuses = mutableListOf<ByteCacheStatus>()
        val deletedKeys = mutableListOf<ByteCacheKey>()
        val deletedSongs = mutableListOf<Pair<String, String>>()
        val deletedPlatforms = mutableListOf<String>()
        var deleteAllCount = 0

        override suspend fun get(key: ByteCacheKey): ByteCacheStatus? = null
        override suspend fun listAll(): List<ByteCacheStatus> = statuses
        override suspend fun listBySong(platform: String, musicId: String): List<ByteCacheStatus> =
            statuses.filter { it.key.platform == platform && it.key.musicId == musicId }

        override suspend fun upsert(status: ByteCacheStatus) = Unit
        override suspend fun markInvalid(
            key: ByteCacheKey,
            reason: ByteCacheInvalidReason,
            updatedAt: Long,
        ) = Unit

        override suspend fun delete(key: ByteCacheKey) {
            deletedKeys += key
        }

        override suspend fun deleteBySong(platform: String, musicId: String) {
            deletedSongs += platform to musicId
        }

        override suspend fun deleteByPlatform(platform: String) {
            deletedPlatforms += platform
        }

        override suspend fun deleteAll() {
            deleteAllCount += 1
        }
    }

    private fun catalogRow(
        sourcesJson: String,
        updatedAt: Long,
        sourceMetadataBytes: Long,
        platform: String = "kg",
        itemId: String = "1",
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artwork: String? = null,
        durationMs: Long? = null,
        libraryTitle: String? = null,
        libraryArtist: String? = null,
        libraryAlbum: String? = null,
        libraryArtwork: String? = null,
        libraryDurationMs: Long? = null,
        listenTitle: String? = null,
        listenArtist: String? = null,
        listenAlbum: String? = null,
        listenArtwork: String? = null,
        listenDurationMs: Long? = null,
    ): MediaCacheCatalogRow = MediaCacheCatalogRow(
        platform = platform,
        itemId = itemId,
        sourcesJson = sourcesJson,
        updatedAt = updatedAt,
        sourceMetadataBytes = sourceMetadataBytes,
        title = title,
        artist = artist,
        album = album,
        artwork = artwork,
        durationMs = durationMs,
        libraryTitle = libraryTitle,
        libraryArtist = libraryArtist,
        libraryAlbum = libraryAlbum,
        libraryArtwork = libraryArtwork,
        libraryDurationMs = libraryDurationMs,
        listenTitle = listenTitle,
        listenArtist = listenArtist,
        listenAlbum = listenAlbum,
        listenArtwork = listenArtwork,
        listenDurationMs = listenDurationMs,
    )

    private fun byteCacheStatus(
        quality: PlayQuality,
        validity: ByteCacheValidity,
        cachedBytes: Long,
        contentLength: Long?,
        updatedAt: Long,
        invalidReason: ByteCacheInvalidReason? = null,
    ): ByteCacheStatus = ByteCacheStatus(
        key = ByteCacheKey(platform = "kg", musicId = "1", quality = quality),
        validity = validity,
        cachedBytes = cachedBytes,
        contentLength = contentLength,
        validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
        sourceFingerprint = "fingerprint",
        invalidReason = invalidReason,
        verifiedAt = if (validity == ByteCacheValidity.PlayableVerified) updatedAt else null,
        updatedAt = updatedAt,
    )
}
