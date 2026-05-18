package com.hank.musicfree.downloader.engine

import android.net.Uri
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.db.dao.DownloadTaskDao
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.DownloadTaskEntity
import com.hank.musicfree.data.db.entity.DownloadedTrackEntity
import com.hank.musicfree.downloader.io.HttpDownloadException
import com.hank.musicfree.downloader.io.HttpDownloader
import com.hank.musicfree.downloader.io.NetworkState
import com.hank.musicfree.downloader.model.DownloadFailReason
import com.hank.musicfree.downloader.model.DownloadStatus
import com.hank.musicfree.downloader.model.DownloadTaskUi
import com.hank.musicfree.downloader.model.MediaKey
import com.hank.musicfree.downloader.prefs.DownloadConfig
import com.hank.musicfree.downloader.quality.QualityFallback
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadEngine(
    private val taskDao: DownloadTaskDao,
    private val downloadedDao: DownloadedTrackDao,
    private val http: HttpDownloader,
    private val writer: suspend (cacheFile: File, displayName: String, mime: String, relPath: String, size: Long) -> Uri,
    private val resolver: suspend (MusicItem, qualityWire: String) -> MediaSourceResult?,
    private val converters: Converters,
    private val musicRepository: MusicRepository,
    private val configFlow: StateFlow<DownloadConfig>,
    private val networkFlow: StateFlow<NetworkState>,
    private val cacheDir: File,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val mutex = Mutex()
    private val inflight = ConcurrentHashMap<MediaKey, Job>()
    private val progressCache = ConcurrentHashMap<MediaKey, Pair<Long?, Long?>>()
    private var networkWatchJob: Job? = null
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    val tasks: StateFlow<List<DownloadTaskUi>> = taskDao.observeAll()
        .map { rows -> rows.map { it.toUi(progressCache[MediaKey.of(it.id, it.platform)]) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val downloadedKeys: StateFlow<Set<MediaKey>> = downloadedDao.observeKeys()
        .map { list ->
            list.map { keyStr ->
                val parts = keyStr.split("@", limit = 2)
                MediaKey.of(parts[0], parts.getOrNull(1) ?: "")
            }.toSet()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    suspend fun taskSnapshot(): List<DownloadTaskUi> = taskDao.observeAll().first()
        .map { it.toUi(progressCache[MediaKey.of(it.id, it.platform)]) }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // Restart recovery: any PREPARING/DOWNLOADING become PENDING (with resolvedUrl cleared)
            taskDao.resetInflightToPending()
            // Wipe stale cache files left over from a previous run
            runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
            // Watch network for mid-download cellular cutoff; resched on any state change
            networkWatchJob = combine(networkFlow, configFlow) { net, cfg ->
                net to cfg.useCellularDownload
            }.onEach { (net, cellularAllowed) ->
                if (net == NetworkState.Cellular && !cellularAllowed) {
                    // mid-download cellular cutoff: cancel all inflight, mark them FAILED with reason
                    inflight.values.forEach { it.cancel() }
                    inflight.clear()
                    val rows = taskDao.observeAll().first()
                    rows.filter {
                        it.status == DownloadStatus.PREPARING.name || it.status == DownloadStatus.DOWNLOADING.name
                    }.forEach {
                        taskDao.markFailed(
                            it.id, it.platform,
                            reason = DownloadFailReason.NotAllowToDownloadInCellular.name,
                            now = System.currentTimeMillis(),
                        )
                    }
                } else {
                    scheduleNext()
                }
            }.launchIn(scope)
        }
    }

    fun stop() {
        networkWatchJob?.cancel()
        networkWatchJob = null
        inflight.values.forEach { it.cancel() }
        inflight.clear()
    }

    fun enqueue(items: List<MusicItem>, quality: PlayQuality?) {
        scope.launch {
            val effectiveQuality = quality ?: configFlow.value.defaultDownloadQuality
            val now = System.currentTimeMillis()
            val toAdd = mutableListOf<DownloadTaskEntity>()
            for (item in items) {
                if (downloadedDao.exists(item.id, item.platform)) continue
                if (taskDao.findByKey(item.id, item.platform) != null) continue
                toAdd += DownloadTaskEntity(
                    id = item.id, platform = item.platform, title = item.title, artist = item.artist,
                    album = item.album, artwork = item.artwork, durationMs = item.duration,
                    targetQuality = effectiveQuality.name.lowercase(),
                    status = DownloadStatus.PENDING.name, errorReason = null,
                    seedUrl = item.url,
                    musicItemJson = converters.musicItemToJson(item),
                    resolvedUrl = null, resolvedHeadersJson = null,
                    fileSize = null, downloadedSize = null,
                    createdAt = now, updatedAt = now,
                )
            }
            toAdd.forEach { taskDao.upsert(it) }
            scheduleNext()
        }
    }

    private suspend fun scheduleNext() = mutex.withLock {
        val cap = configFlow.value.maxDownload.coerceIn(1, 10)
        if (inflight.size >= cap) return@withLock
        val net = networkFlow.value
        if (net == NetworkState.Offline) return@withLock
        if (net == NetworkState.Cellular && !configFlow.value.useCellularDownload) return@withLock
        val next = taskDao.findNextPending() ?: return@withLock
        val key = MediaKey.of(next.id, next.platform)
        if (inflight.containsKey(key)) return@withLock
        taskDao.updateStatus(next.id, next.platform, DownloadStatus.PREPARING.name, System.currentTimeMillis())
        val job = scope.launch { runOne(next, key) }
        inflight[key] = job
    }

    private suspend fun runOne(task: DownloadTaskEntity, key: MediaKey) {
        val musicItem = task.toMusicItemSeed(converters)
        val targetQuality = runCatching {
            PlayQuality.valueOf(task.targetQuality.uppercase())
        }.getOrDefault(PlayQuality.STANDARD)
        val config = configFlow.value

        val resolved = QualityFallback.resolve(
            musicItem,
            targetQuality,
            config.downloadQualityOrder,
            resolver,
        )
        val source: MediaSourceResult = if (resolved != null) {
            resolved.second
        } else {
            val seed = musicItem.url
            if (!seed.isNullOrBlank()) {
                MediaSourceResult(url = seed, headers = null, userAgent = null, quality = targetQuality)
            } else {
                markFailed(key, DownloadFailReason.FailToFetchSource)
                inflight.remove(key)
                scheduleNext()
                return
            }
        }
        taskDao.setResolved(task.id, task.platform, source.url, null, System.currentTimeMillis())
        taskDao.updateStatus(task.id, task.platform, DownloadStatus.DOWNLOADING.name, System.currentTimeMillis())

        val ext = DownloadFilenames.extensionFromUrl(source.url)
        val mime = DownloadFilenames.mimeFor(ext)
        val displayName = DownloadFilenames.displayName(musicItem, ext)
        val relPath = config.downloadDirRelative

        val cacheFile = File(cacheDir, "${UUID.randomUUID()}.$ext").also { it.parentFile?.mkdirs() }
        try {
            http.download(
                url = source.url,
                headers = source.headers ?: emptyMap(),
                target = cacheFile,
                onProgress = { p ->
                    progressCache[key] = p.downloaded to p.total.takeIf { it > 0 }
                    scope.launch {
                        taskDao.updateProgress(
                            task.id, task.platform,
                            p.total.takeIf { it > 0 }, p.downloaded,
                            System.currentTimeMillis(),
                        )
                    }
                },
            )
            val size = cacheFile.length()
            withContext(NonCancellable) {
                val uri = writer(cacheFile, displayName, mime, relPath, size)
                val downloaded = DownloadedTrackEntity(
                    id = task.id, platform = task.platform,
                    mediaStoreUri = uri.toString(), relativePath = relPath,
                    mimeType = mime, quality = task.targetQuality,
                    sizeBytes = size, downloadedAt = System.currentTimeMillis(),
                )
                val localWriteStartedAt = System.nanoTime()
                val localWriteFields = downloadLocalLibraryWriteFields(musicItem, downloaded)
                MfLog.detail(
                    category = LogCategory.DOWNLOAD,
                    event = "download_local_library_write_start",
                    fields = localWriteFields,
                )
                try {
                    musicRepository.commitDownloadedTrack(musicItem, downloaded)
                    taskDao.deleteByKey(task.id, task.platform)
                    MfLog.detail(
                        category = LogCategory.DOWNLOAD,
                        event = "download_local_library_write_success",
                        fields = localWriteFields + mapOf(
                            "durationMs" to elapsedMs(localWriteStartedAt),
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                } catch (e: CancellationException) {
                    MfLog.detail(
                        category = LogCategory.DOWNLOAD,
                        event = "download_local_library_write_cancelled",
                        fields = localWriteFields + mapOf(
                            "durationMs" to elapsedMs(localWriteStartedAt),
                            "result" to LogFields.Result.CANCELLED,
                            "reason" to LogFields.Reason.CANCELLED,
                        ),
                    )
                    throw e
                } catch (t: Throwable) {
                    MfLog.error(
                        category = LogCategory.DOWNLOAD,
                        event = "download_local_library_write_failed",
                        throwable = t,
                        fields = localWriteFields + mapOf(
                            "durationMs" to elapsedMs(localWriteStartedAt),
                            "result" to LogFields.Result.FAILURE,
                            "reason" to "exception",
                        ),
                    )
                    throw t
                }
            }
            progressCache.remove(key)
            _events.tryEmit(DownloadEvent.Completed(key))
        } catch (e: CancellationException) {
            throw e   // propagate cancellation per Kotlin coroutines convention
        } catch (e: HttpDownloadException) {
            markFailed(key, DownloadFailReason.Unknown)
        } catch (t: Throwable) {
            markFailed(key, DownloadFailReason.Unknown)
        } finally {
            if (cacheFile.exists()) cacheFile.delete()
            inflight.remove(key)
            scheduleNext()
            if (inflight.isEmpty() && taskDao.findNextPending() == null) {
                _events.tryEmit(DownloadEvent.QueueIdle)
            }
        }
    }

    private suspend fun markFailed(key: MediaKey, reason: DownloadFailReason) {
        taskDao.markFailed(
            id = key.id, platform = key.platform,
            reason = reason.name, now = System.currentTimeMillis(),
        )
        progressCache.remove(key)
    }

    fun cancel(key: MediaKey) {
        scope.launch {
            inflight[key]?.cancel()
            inflight.remove(key)
            taskDao.deleteByKey(key.id, key.platform)
            progressCache.remove(key)
            scheduleNext()
        }
    }

    fun retry(key: MediaKey) {
        scope.launch {
            val row = taskDao.findByKey(key.id, key.platform) ?: return@launch
            if (row.status != DownloadStatus.FAILED.name) return@launch
            taskDao.updateStatus(key.id, key.platform, DownloadStatus.PENDING.name, System.currentTimeMillis())
            scheduleNext()
        }
    }

    fun clearFailed() {
        scope.launch { taskDao.deleteAllFailed() }
    }

    fun retryAllFailed() {
        scope.launch {
            taskDao.resetAllFailedToPending()
            scheduleNext()
        }
    }

    fun cancelAllInflight() {
        scope.launch {
            inflight.values.forEach { it.cancel() }
            inflight.clear()
            taskDao.deleteAllInflight()
        }
    }
}

private fun DownloadTaskEntity.toUi(progress: Pair<Long?, Long?>?): DownloadTaskUi = DownloadTaskUi(
    key = MediaKey.of(id, platform),
    title = title, artist = artist, artwork = artwork,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    targetQuality = targetQuality,
    downloadedBytes = progress?.first ?: downloadedSize,
    totalBytes = progress?.second ?: fileSize,
    errorReason = errorReason?.let { runCatching { DownloadFailReason.valueOf(it) }.getOrNull() },
)

private fun DownloadTaskEntity.toMusicItemSeed(converters: Converters): MusicItem =
    runCatching { converters.jsonToMusicItem(musicItemJson) }.getOrNull() ?: MusicItem(
        id = id, platform = platform, title = title, artist = artist,
        album = album, duration = durationMs, url = seedUrl, artwork = artwork, qualities = null,
    )

private fun downloadLocalLibraryWriteFields(
    item: MusicItem,
    downloaded: DownloadedTrackEntity,
): Map<String, Any?> = mapOf(
    "operation" to "download_local_library_write",
    "itemId" to item.id,
    "itemName" to item.title,
    "platform" to item.platform,
    "quality" to downloaded.quality,
    "pathType" to "mediastore",
    "sizeBytes" to downloaded.sizeBytes,
)

private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

sealed interface DownloadEvent {
    data class Completed(val key: MediaKey) : DownloadEvent
    data object QueueIdle : DownloadEvent
    data class Toast(val reason: DownloadFailReason) : DownloadEvent
}
