package com.hank.musicfree.feature.settings.cachemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.shortLabel
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.OnlineCacheQualityStatus
import com.hank.musicfree.data.repository.OnlineCacheSongRow
import com.hank.musicfree.feature.settings.SettingsCacheCleaner
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CacheManagementFilter { All, Reusable, Partial, SourceOnly, Invalid }

data class CacheManagementSummary(
    val songCount: Int = 0,
    val qualityCount: Int = 0,
    val reusableCount: Int = 0,
    val totalBytes: Long = 0L,
)

data class CacheManagementUiState(
    val isLoading: Boolean = true,
    val isClearing: Boolean = false,
    val query: String = "",
    val filter: CacheManagementFilter = CacheManagementFilter.All,
    val allRows: List<OnlineCacheSongRow> = emptyList(),
    val visibleRows: List<OnlineCacheSongRow> = emptyList(),
    val selectedRow: OnlineCacheSongRow? = null,
    val summary: CacheManagementSummary = CacheManagementSummary(),
    val message: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CacheManagementViewModel @Inject constructor(
    private val mediaCacheRepository: MediaCacheRepository,
    private val cacheCleaner: SettingsCacheCleaner,
) : ViewModel() {

    private val clearInProgress = AtomicBoolean(false)
    private val loadGeneration = AtomicLong(0L)
    private var searchLogJob: Job? = null
    private val _uiState = MutableStateFlow(CacheManagementUiState())
    val uiState: StateFlow<CacheManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val generation = loadGeneration.incrementAndGet()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val rows = loadRows(generation) ?: return@launch
                _uiState.update {
                    it.withRows(rows).copy(
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isLoading = false) }
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "缓存列表加载失败",
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(value: String) {
        val current = _uiState.value
        val visibleRows = filterRows(current.allRows, value, current.filter)
        _uiState.update {
            it.copy(
                query = value,
                visibleRows = visibleRows,
                message = null,
                errorMessage = null,
            )
        }
        scheduleSearchLog(value, visibleRows.size)
    }

    fun onFilterChange(filter: CacheManagementFilter) {
        _uiState.update {
            it.copy(
                filter = filter,
                visibleRows = filterRows(it.allRows, it.query, filter),
                message = null,
                errorMessage = null,
            )
        }
    }

    fun selectRow(row: OnlineCacheSongRow?) {
        _uiState.update { it.copy(selectedRow = row, message = null, errorMessage = null) }
    }

    fun clearSelectedQuality(quality: PlayQuality) {
        val row = _uiState.value.selectedRow ?: run {
            _uiState.update { it.copy(errorMessage = "请选择要清理的歌曲") }
            return
        }
        clearWithRefresh(
            action = {
                cacheCleaner.clearOnlineSongCache(row.platform, row.itemId, quality)
                "已清理 ${row.displayTitle()} 的${quality.shortLabel()}在线播放缓存"
            },
        )
    }

    fun clearSelectedSong() {
        val row = _uiState.value.selectedRow ?: run {
            _uiState.update { it.copy(errorMessage = "请选择要清理的歌曲") }
            return
        }
        clearWithRefresh(
            action = {
                cacheCleaner.clearOnlineSongCache(row.platform, row.itemId, null)
                "已清理 ${row.displayTitle()} 的在线播放缓存"
            },
        )
    }

    fun clearAll() {
        clearWithRefresh(
            action = {
                cacheCleaner.clearAllOnlinePlaybackCache()
                "已清理全部在线播放缓存"
            },
        )
    }

    private fun clearWithRefresh(action: suspend () -> String) {
        if (!beginClearing()) return

        viewModelScope.launch {
            try {
                val message = action()
                val generation = loadGeneration.incrementAndGet()
                val rows = loadRows(generation)
                _uiState.update {
                    if (rows == null) {
                        it.copy(
                            isLoading = false,
                            isClearing = false,
                            selectedRow = null,
                            message = message,
                        )
                    } else {
                        it.withRows(rows).copy(
                            isLoading = false,
                            isClearing = false,
                            selectedRow = null,
                            message = message,
                            errorMessage = null,
                        )
                    }
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isClearing = false) }
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        errorMessage = "清理失败：${error.message ?: "未知错误"}",
                    )
                }
            } finally {
                clearInProgress.set(false)
            }
        }
    }

    private fun beginClearing(): Boolean {
        if (!clearInProgress.compareAndSet(false, true)) return false
        _uiState.update {
            it.copy(
                isClearing = true,
                message = null,
                errorMessage = null,
            )
        }
        return true
    }

    private fun scheduleSearchLog(query: String, resultCount: Int) {
        searchLogJob?.cancel()
        searchLogJob = viewModelScope.launch {
            delay(SEARCH_LOG_DEBOUNCE_MS)
            MfLog.detail(
                category = LogCategory.UI,
                event = "cache_management.search",
                fields = mapOf(
                    "screen" to CACHE_MANAGEMENT_SCREEN,
                    "queryLength" to query.trim().length,
                    "resultCount" to resultCount,
                ),
            )
        }
    }

    private suspend fun loadRows(generation: Long): List<OnlineCacheSongRow>? {
        val startedAt = System.nanoTime()
        return try {
            val rows = mediaCacheRepository.listOnlineCacheCatalog()
            val qualityCount = rows.sumOf { it.qualities.size }
            val durationMs = elapsedMs(startedAt)
            if (generation != loadGeneration.get()) {
                MfLog.detail(
                    category = LogCategory.SETTINGS,
                    event = "settings_online_cache_load",
                    fields = mapOf(
                        "count" to rows.size,
                        "qualityCount" to qualityCount,
                        "durationMs" to durationMs,
                        "generation" to generation,
                        "result" to LogFields.Result.STALE,
                        "reason" to LogFields.Reason.STALE_GENERATION,
                    ),
                )
                return null
            }
            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "settings_online_cache_load",
                fields = mapOf(
                    "count" to rows.size,
                    "qualityCount" to qualityCount,
                    "durationMs" to durationMs,
                    "generation" to generation,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            rows
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "settings_online_cache_load",
                fields = mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "generation" to generation,
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SETTINGS,
                event = "settings_online_cache_load",
                throwable = error,
                fields = mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "generation" to generation,
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "exception",
                ),
            )
            throw error
        }
    }

    private fun CacheManagementUiState.withRows(rows: List<OnlineCacheSongRow>): CacheManagementUiState =
        copy(
            allRows = rows,
            visibleRows = filterRows(rows, query, filter),
            summary = summarize(rows),
            selectedRow = selectedRow?.takeIf { selected ->
                rows.any { it.platform == selected.platform && it.itemId == selected.itemId }
            },
        )

    private fun filterRows(
        rows: List<OnlineCacheSongRow>,
        query: String,
        filter: CacheManagementFilter,
    ): List<OnlineCacheSongRow> {
        val normalizedQuery = query.trim().lowercase()
        return rows.filter { row ->
            val matchesQuery = normalizedQuery.isEmpty() ||
                row.title.contains(normalizedQuery, ignoreCase = true) ||
                row.artist.contains(normalizedQuery, ignoreCase = true) ||
                row.platform.contains(normalizedQuery, ignoreCase = true)
            matchesQuery && row.matchesFilter(filter)
        }
    }

    private fun OnlineCacheSongRow.matchesFilter(filter: CacheManagementFilter): Boolean = when (filter) {
        CacheManagementFilter.All -> true
        CacheManagementFilter.Reusable -> qualities.any { it.status == OnlineCacheQualityStatus.Reusable }
        CacheManagementFilter.Partial -> qualities.any { it.status == OnlineCacheQualityStatus.Partial }
        CacheManagementFilter.SourceOnly -> qualities.any { it.status == OnlineCacheQualityStatus.SourceOnly }
        CacheManagementFilter.Invalid -> qualities.any { it.status == OnlineCacheQualityStatus.Invalid }
    }

    private fun summarize(rows: List<OnlineCacheSongRow>): CacheManagementSummary = CacheManagementSummary(
        songCount = rows.size,
        qualityCount = rows.sumOf { it.qualities.size },
        reusableCount = rows.sumOf { row ->
            row.qualities.count { it.status == OnlineCacheQualityStatus.Reusable }
        },
        totalBytes = rows.sumOf { it.totalBytes },
    )

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}

private fun OnlineCacheSongRow.displayTitle(): String = title.ifBlank { "未知歌曲" }

private const val CACHE_MANAGEMENT_SCREEN = "cache_management"
private const val SEARCH_LOG_DEBOUNCE_MS = 300L
