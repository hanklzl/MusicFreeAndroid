package com.hank.musicfree.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.data.repository.StarredSheetRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.home.scanner.LocalMusicScanner
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanner: LocalMusicScanner,
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val starredSheetRepository: StarredSheetRepository,
    private val musicRepository: MusicRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .map { sortFavoriteFirst(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val starredSheets = starredSheetRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadActiveCount: StateFlow<Int> = downloader.tasks
        .map { tasks ->
            tasks.count { it.status != com.hank.musicfree.downloader.model.DownloadStatus.FAILED }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadedKeys: StateFlow<Set<com.hank.musicfree.downloader.model.MediaKey>> =
        downloader.downloadedKeys

    private fun sortFavoriteFirst(playlists: List<Playlist>): List<Playlist> {
        val (favorite, others) = playlists.partition { it.isDefault }
        return favorite + others.sortedByDescending { it.createdAt }
    }

    fun scanLocalMusic() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            val flowId = newFlowId("local_scan")
            val startedAt = System.nanoTime()
            MfLog.detail(
                LogCategory.HOME,
                "local_scan_start",
                mapOf(
                    "screen" to SCREEN_HOME,
                    "operation" to "local_scan",
                    "flowId" to flowId,
                ),
            )
            val storageDirectoryUri = try {
                appPreferences.storageDirectoryUri.first()
            } catch (e: CancellationException) {
                logCancelled(
                    category = LogCategory.FILE_IO,
                    event = "storage_directory_read_cancelled",
                    screen = SCREEN_HOME,
                    operation = "read_storage_directory",
                    flowId = flowId,
                    startedAt = startedAt,
                )
                throw e
            } catch (e: Exception) {
                MfLog.error(
                    LogCategory.FILE_IO,
                    "storage_directory_read_failed",
                    e,
                    mapOf(
                        "screen" to SCREEN_HOME,
                        "operation" to "read_storage_directory",
                        "flowId" to flowId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                    ),
                )
                _uiState.value = HomeUiState.Error(e.message ?: "扫描失败")
                return@launch
            }

            scanner.scan(storageDirectoryUri)
                .catch { e ->
                    if (e is CancellationException) {
                        MfLog.detail(
                            LogCategory.HOME,
                            "local_scan_cancelled",
                            mapOf(
                                "screen" to SCREEN_HOME,
                                "operation" to "local_scan",
                                "flowId" to flowId,
                                "pathType" to storageDirectoryUri.pathType(),
                                "durationMs" to elapsedMs(startedAt),
                                "result" to LogFields.Result.CANCELLED,
                                "reason" to LogFields.Reason.CANCELLED,
                            ),
                        )
                        throw e
                    }
                    MfLog.error(
                        LogCategory.HOME,
                        "local_scan_failed",
                        e,
                        mapOf(
                            "screen" to SCREEN_HOME,
                            "operation" to "local_scan",
                            "flowId" to flowId,
                            "pathType" to storageDirectoryUri.pathType(),
                            "durationMs" to elapsedMs(startedAt),
                            "result" to LogFields.Result.FAILURE,
                        ),
                    )
                    _uiState.value = HomeUiState.Error(e.message ?: "扫描失败")
                }
                .collect { items ->
                    MfLog.detail(
                        LogCategory.HOME,
                        "local_scan_success",
                        mapOf(
                            "screen" to SCREEN_HOME,
                            "operation" to "local_scan",
                            "flowId" to flowId,
                            "pathType" to storageDirectoryUri.pathType(),
                            "count" to items.size,
                            "durationMs" to elapsedMs(startedAt),
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                    _uiState.value = HomeUiState.Success(items)
                }
        }
    }

    fun playItem(item: MusicItem, queue: List<MusicItem>) {
        val index = queue.indexOf(item)
        MfLog.detail(
            LogCategory.HOME,
            "home_play_item",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_HOME,
                "operation" to "play_item",
                "count" to queue.size,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        playerController.playQueue(queue, if (index >= 0) index else 0)
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val playlistId = UUID.randomUUID().toString()
            val flowId = newFlowId("create_playlist")
            MfLog.detail(
                LogCategory.HOME,
                "playlist_create_start",
                mapOf(
                    "screen" to SCREEN_HOME,
                    "operation" to "create_playlist",
                    "flowId" to flowId,
                    "playlistId" to playlistId,
                    "itemName" to name,
                ),
            )
            val startedAt = System.nanoTime()
            try {
                playlistRepository.createPlaylist(
                    Playlist(
                        id = playlistId,
                        name = name,
                        coverUri = null,
                    )
                )
                MfLog.detail(
                    LogCategory.HOME,
                    "playlist_create_success",
                    mapOf(
                        "screen" to SCREEN_HOME,
                        "operation" to "create_playlist",
                        "flowId" to flowId,
                        "playlistId" to playlistId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
            } catch (e: CancellationException) {
                logCancelled(
                    category = LogCategory.HOME,
                    event = "playlist_create_cancelled",
                    screen = SCREEN_HOME,
                    operation = "create_playlist",
                    flowId = flowId,
                    startedAt = startedAt,
                    fields = mapOf("playlistId" to playlistId),
                )
                throw e
            } catch (e: Exception) {
                MfLog.error(
                    LogCategory.HOME,
                    "playlist_create_failed",
                    e,
                    mapOf(
                        "screen" to SCREEN_HOME,
                        "operation" to "create_playlist",
                        "flowId" to flowId,
                        "playlistId" to playlistId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                    ),
                )
                throw e
            }
        }
    }

    fun addToPlaylist(playlistId: String, item: MusicItem) {
        viewModelScope.launch {
            val flowId = newFlowId("add_to_playlist")
            val startedAt = System.nanoTime()
            MfLog.detail(
                LogCategory.HOME,
                "playlist_add_item_start",
                musicItemFields(item) + mapOf(
                    "screen" to SCREEN_HOME,
                    "operation" to "add_to_playlist",
                    "flowId" to flowId,
                    "playlistId" to playlistId,
                ),
            )
            try {
                playlistRepository.addMusicToPlaylist(playlistId, item)
                MfLog.detail(
                    LogCategory.HOME,
                    "playlist_add_item_success",
                    musicItemFields(item) + mapOf(
                        "screen" to SCREEN_HOME,
                        "operation" to "add_to_playlist",
                        "flowId" to flowId,
                        "playlistId" to playlistId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
            } catch (e: CancellationException) {
                logCancelled(
                    category = LogCategory.HOME,
                    event = "playlist_add_item_cancelled",
                    screen = SCREEN_HOME,
                    operation = "add_to_playlist",
                    flowId = flowId,
                    startedAt = startedAt,
                    fields = musicItemFields(item) + mapOf("playlistId" to playlistId),
                )
                throw e
            } catch (e: Exception) {
                MfLog.error(
                    LogCategory.HOME,
                    "playlist_add_item_failed",
                    e,
                    musicItemFields(item) + mapOf(
                        "screen" to SCREEN_HOME,
                        "operation" to "add_to_playlist",
                        "flowId" to flowId,
                        "playlistId" to playlistId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                    ),
                )
                throw e
            }
        }
    }

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        MfLog.detail(
            LogCategory.DOWNLOAD,
            "download_intent",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_HOME,
                "operation" to "download",
                "quality" to quality.name,
                "count" to 1,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        downloader.enqueue(listOf(item), quality)
    }

    private fun logCancelled(
        category: LogCategory,
        event: String,
        screen: String,
        operation: String,
        flowId: String,
        startedAt: Long,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category,
            event,
            fields + mapOf(
                "screen" to screen,
                "operation" to operation,
                "flowId" to flowId,
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.CANCELLED,
                "reason" to LogFields.Reason.CANCELLED,
            ),
        )
    }

    private fun musicItemFields(item: MusicItem): Map<String, Any?> = mapOf(
        "platform" to item.platform,
        "itemId" to item.id,
        "itemName" to item.title,
    )

    private fun newFlowId(operation: String): String = "$SCREEN_HOME:$operation:${System.nanoTime()}"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private fun String?.pathType(): String = if (isNullOrBlank()) "mediastore" else "document_tree"

    private companion object {
        const val SCREEN_HOME = "home"
    }
}
