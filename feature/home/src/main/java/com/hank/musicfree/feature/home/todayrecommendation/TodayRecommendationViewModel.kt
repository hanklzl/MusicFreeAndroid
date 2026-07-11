package com.hank.musicfree.feature.home.todayrecommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayRecommendationUiState(
    val items: List<RecommendedSheet> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val showingFallback: Boolean = false,
    val confidence: ProfileConfidence? = null,
    val noPlugins: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TodayRecommendationViewModel @Inject constructor(
    private val generator: TodayRecommendationGenerator,
    private val snapshotStore: TodayRecommendationSnapshotStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodayRecommendationUiState())
    val uiState: StateFlow<TodayRecommendationUiState> = _uiState.asStateFlow()
    private var generation = 0L
    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    fun logSheetOpen(item: RecommendedSheet) {
        MfLog.detail(
            category = LogCategory.HOME,
            event = "recommend_sheet_open",
            fields = mapOf(
                "operation" to "recommend_sheet_open",
                "platform" to item.sheet.platform,
                "sheetId" to item.sheet.id,
                "itemName" to item.sheet.title.orEmpty(),
                "reason" to item.reason,
            ),
        )
    }

    private fun load(forceRefresh: Boolean) {
        val requestGeneration = ++generation
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val date = LocalDate.now()
            if (!forceRefresh) {
                val fallback = snapshotStore.readLatestFallback(date)
                if (fallback != null && requestGeneration == generation) {
                    _uiState.value = TodayRecommendationUiState(
                        items = fallback.items,
                        loading = false,
                        refreshing = false,
                        showingFallback = fallback.date != date,
                        confidence = fallback.confidence,
                    )
                }
            }
            _uiState.update { current ->
                current.copy(
                    loading = current.items.isEmpty(),
                    refreshing = current.items.isNotEmpty(),
                    errorMessage = null,
                )
            }
            try {
                val result = generator.generate(date, forceRefresh)
                if (requestGeneration != generation) return@launch
                _uiState.value = TodayRecommendationUiState(
                    items = result.snapshot.items,
                    loading = false,
                    refreshing = false,
                    showingFallback = false,
                    confidence = result.snapshot.confidence,
                    noPlugins = result.availablePluginCount == 0,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (requestGeneration != generation) return@launch
                MfLog.error(
                    category = LogCategory.HOME,
                    event = "recommend_refresh_failed",
                    throwable = error,
                    fields = mapOf(
                        "operation" to "recommend_refresh",
                        "result" to LogFields.Result.FAILURE,
                        "hasFallback" to _uiState.value.items.isNotEmpty(),
                    ),
                )
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        refreshing = false,
                        showingFallback = current.items.isNotEmpty(),
                        errorMessage = if (current.items.isEmpty()) "生成推荐失败，请稍后重试" else "刷新失败，已保留原推荐",
                    )
                }
            }
        }
    }
}
