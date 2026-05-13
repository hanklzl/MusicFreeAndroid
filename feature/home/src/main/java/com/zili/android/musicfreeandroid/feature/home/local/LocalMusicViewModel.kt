package com.zili.android.musicfreeandroid.feature.home.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val scanner: LocalMusicScanner,
    private val playerController: PlayerController,
    private val musicRepository: MusicRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocalMusicUiState>(LocalMusicUiState.Loading)
    val uiState: StateFlow<LocalMusicUiState> = _uiState

    private var latestRepositoryItems: List<MusicItem> = emptyList()
    private var scanInProgress: Boolean = false
    private var scanGeneration: Long = 0L
    private var scanJob: Job? = null

    val downloadActiveCount: StateFlow<Int> = downloader.tasks
        .map { tasks -> tasks.count { it.status != DownloadStatus.FAILED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadedKeys: StateFlow<Set<MediaKey>> = downloader.downloadedKeys

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    init {
        viewModelScope.launch {
            musicRepository.observeLocalLibrary()
                .catch { e ->
                    _uiState.value = LocalMusicUiState.Error(e.message ?: "加载失败")
                }
                .collect { items ->
                    latestRepositoryItems = items
                    if (!scanInProgress && _uiState.value !is LocalMusicUiState.Error) {
                        _uiState.value = LocalMusicUiState.Success(items)
                    }
                }
        }
    }

    suspend fun currentStorageDirectoryUri(): String? = appPreferences.storageDirectoryUri.first()

    fun scanLocalMusic(storageDirectoryUri: String? = null) = startScan(
        operation = "local_scan",
        fallbackErrorMessage = "扫描失败",
    ) {
        val uri = storageDirectoryUri ?: appPreferences.storageDirectoryUri.first()
        ScanDiagnostic(
            count = scanAndPersist(uri),
            pathType = uri.pathType(),
        )
    }

    fun persistStorageDirectoryAndScan(uri: String) = startScan(
        operation = "persist_storage_directory_and_scan",
        fallbackErrorMessage = "保存目录失败",
    ) {
        persistStorageDirectory(uri)
        ScanDiagnostic(
            count = scanAndPersist(uri),
            pathType = uri.pathType(),
        )
    }

    fun showError(message: String) {
        scanInProgress = false
        _uiState.value = LocalMusicUiState.Error(message)
    }

    private suspend fun scanAndPersist(storageDirectoryUri: String?): Int {
        var count = 0
        scanner.scan(storageDirectoryUri)
            .collect { items ->
                count = items.size
                musicRepository.replaceByPlatform(LocalMusicScanner.PLATFORM_LOCAL, items)
            }
        return count
    }

    private fun startScan(
        operation: String,
        fallbackErrorMessage: String,
        block: suspend () -> ScanDiagnostic,
    ) {
        scanJob?.cancel()
        val generation = ++scanGeneration
        val flowId = newFlowId(operation, generation)
        scanJob = viewModelScope.launch {
            scanInProgress = true
            _uiState.value = LocalMusicUiState.Loading
            val startedAt = System.nanoTime()
            MfLog.detail(
                LogCategory.HOME,
                "local_scan_start",
                mapOf(
                    "screen" to SCREEN_LOCAL_MUSIC,
                    "operation" to operation,
                    "flowId" to flowId,
                    "generation" to generation,
                ),
            )
            try {
                val diagnostic = block()
                if (generation == scanGeneration) {
                    scanInProgress = false
                    _uiState.value = LocalMusicUiState.Success(latestRepositoryItems)
                    MfLog.detail(
                        LogCategory.HOME,
                        "local_scan_success",
                        mapOf(
                            "screen" to SCREEN_LOCAL_MUSIC,
                            "operation" to operation,
                            "flowId" to flowId,
                            "generation" to generation,
                            "pathType" to diagnostic.pathType,
                            "count" to diagnostic.count,
                            "durationMs" to elapsedMs(startedAt),
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                } else {
                    logStale(operation, flowId, generation, startedAt)
                }
            } catch (e: CancellationException) {
                MfLog.detail(
                    LogCategory.HOME,
                    "local_scan_cancelled",
                    mapOf(
                        "screen" to SCREEN_LOCAL_MUSIC,
                        "operation" to operation,
                        "flowId" to flowId,
                        "generation" to generation,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.CANCELLED,
                        "reason" to LogFields.Reason.CANCELLED,
                    ),
                )
                throw e
            } catch (e: Exception) {
                if (generation == scanGeneration) {
                    MfLog.error(
                        LogCategory.HOME,
                        "local_scan_failed",
                        e,
                        mapOf(
                            "screen" to SCREEN_LOCAL_MUSIC,
                            "operation" to operation,
                            "flowId" to flowId,
                            "generation" to generation,
                            "durationMs" to elapsedMs(startedAt),
                            "result" to LogFields.Result.FAILURE,
                        ),
                    )
                    showError(e.message ?: fallbackErrorMessage)
                } else {
                    logStale(operation, flowId, generation, startedAt)
                }
            }
        }
    }

    fun playItem(item: MusicItem, queue: List<MusicItem>) {
        val index = queue.indexOf(item)
        MfLog.detail(
            LogCategory.HOME,
            "local_music_play_item",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_LOCAL_MUSIC,
                "operation" to "play_item",
                "count" to queue.size,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        playerController.playQueue(queue, if (index >= 0) index else 0)
    }

    fun removeFromLocalLibrary(item: MusicItem) {
        viewModelScope.launch {
            val flowId = newFlowId("remove_local_item")
            val startedAt = System.nanoTime()
            try {
                musicRepository.removeFromLocalLibrary(item)
                MfLog.detail(
                    LogCategory.HOME,
                    "local_music_remove_success",
                    musicItemFields(item) + mapOf(
                        "screen" to SCREEN_LOCAL_MUSIC,
                        "operation" to "remove_local_item",
                        "flowId" to flowId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
            } catch (e: CancellationException) {
                MfLog.detail(
                    LogCategory.HOME,
                    "local_music_remove_cancelled",
                    musicItemFields(item) + mapOf(
                        "screen" to SCREEN_LOCAL_MUSIC,
                        "operation" to "remove_local_item",
                        "flowId" to flowId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.CANCELLED,
                        "reason" to LogFields.Reason.CANCELLED,
                    ),
                )
                throw e
            } catch (e: Exception) {
                MfLog.error(
                    LogCategory.HOME,
                    "local_music_remove_failed",
                    e,
                    musicItemFields(item) + mapOf(
                        "screen" to SCREEN_LOCAL_MUSIC,
                        "operation" to "remove_local_item",
                        "flowId" to flowId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                    ),
                )
                throw e
            }
        }
    }

    fun download(item: MusicItem, quality: PlayQuality) {
        MfLog.detail(
            LogCategory.DOWNLOAD,
            "download_intent",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_LOCAL_MUSIC,
                "operation" to "download",
                "quality" to quality.name,
                "count" to 1,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        downloader.enqueue(listOf(item), quality)
    }

    private suspend fun persistStorageDirectory(uri: String) {
        val startedAt = System.nanoTime()
        MfLog.detail(
            LogCategory.FILE_IO,
            "storage_directory_persist_start",
            mapOf(
                "screen" to SCREEN_LOCAL_MUSIC,
                "operation" to "persist_storage_directory",
                "pathType" to uri.pathType(),
            ),
        )
        try {
            appPreferences.setStorageDirectoryUri(uri)
            MfLog.detail(
                LogCategory.FILE_IO,
                "storage_directory_persist_success",
                mapOf(
                    "screen" to SCREEN_LOCAL_MUSIC,
                    "operation" to "persist_storage_directory",
                    "pathType" to uri.pathType(),
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (e: CancellationException) {
            MfLog.detail(
                LogCategory.FILE_IO,
                "storage_directory_persist_cancelled",
                mapOf(
                    "screen" to SCREEN_LOCAL_MUSIC,
                    "operation" to "persist_storage_directory",
                    "pathType" to uri.pathType(),
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw e
        } catch (e: Exception) {
            MfLog.error(
                LogCategory.FILE_IO,
                "storage_directory_persist_failed",
                e,
                mapOf(
                    "screen" to SCREEN_LOCAL_MUSIC,
                    "operation" to "persist_storage_directory",
                    "pathType" to uri.pathType(),
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            throw e
        }
    }

    private fun logStale(operation: String, flowId: String, generation: Long, startedAt: Long) {
        MfLog.detail(
            LogCategory.HOME,
            "local_scan_stale",
            mapOf(
                "screen" to SCREEN_LOCAL_MUSIC,
                "operation" to operation,
                "flowId" to flowId,
                "generation" to generation,
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.STALE,
                "reason" to LogFields.Reason.STALE_GENERATION,
            ),
        )
    }

    private fun musicItemFields(item: MusicItem): Map<String, Any?> = mapOf(
        "platform" to item.platform,
        "itemId" to item.id,
        "itemName" to item.title,
    )

    private fun newFlowId(operation: String, generation: Long = System.nanoTime()): String =
        "$SCREEN_LOCAL_MUSIC:$operation:$generation"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private fun String?.pathType(): String = if (isNullOrBlank()) "mediastore" else "document_tree"

    private data class ScanDiagnostic(
        val count: Int,
        val pathType: String,
    )

    private companion object {
        const val SCREEN_LOCAL_MUSIC = "local_music"
    }
}
