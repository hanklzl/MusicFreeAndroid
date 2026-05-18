package com.hank.musicfree.player.runtime

import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlayQueueRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.controller.PlayerController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class PlaybackRuntimeState(
    val restored: Boolean = false,
    val queueSize: Int = 0,
    val currentIndex: Int = 0,
    val savedPositionMs: Long = 0,
    val savedDurationMs: Long = 0,
    val autoPlayWhenRestored: Boolean = false,
    val lastFailureReason: String? = null,
)

@Singleton
class PlaybackRuntimeStore @Inject constructor(
    private val playQueueRepository: PlayQueueRepository,
    private val appPreferences: AppPreferences,
    private val playerController: PlayerController,
    private val playbackRuntimeSettings: PlaybackRuntimeSettings,
) : RuntimeStore<PlaybackRuntimeState> {
    override val storeName: String = STORE_NAME
    private val _state = MutableStateFlow(PlaybackRuntimeState())
    override val state: StateFlow<PlaybackRuntimeState> = _state.asStateFlow()

    private val key = RuntimeStoreKey.singleton(storeName).value

    override suspend fun restore(): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        return try {
            val queue = playQueueRepository.getQueue()
            if (queue.isEmpty()) {
                _state.value = _state.value.copy(
                    restored = false,
                    queueSize = 0,
                    currentIndex = 0,
                    savedPositionMs = 0,
                    savedDurationMs = 0,
                    autoPlayWhenRestored = false,
                    lastFailureReason = null,
                )
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "playback_runtime_restore_skipped",
                    fields = baseFields(
                        queue = queue,
                        startIndex = 0,
                        savedPositionMs = 0L,
                        savedDurationMs = 0L,
                        autoPlay = false,
                        result = LogFields.Result.SKIPPED,
                        reason = "empty_queue",
                        durationMs = elapsedMs(startedAt),
                    ),
                )
                RuntimeRestoreResult.Skipped("empty_queue")
            } else {
                val savedIndex = appPreferences.currentMusicIndex.first().coerceIn(0, queue.lastIndex)
                val savedPositionMs = appPreferences.currentMusicPositionMs.first()
                val savedDurationMs = appPreferences.currentMusicDurationMs.first()
                val autoPlay = playbackRuntimeSettings.autoPlayWhenAppStart()
                withContext(Dispatchers.Main.immediate) {
                    playerController.restoreQueue(
                        items = queue,
                        startIndex = savedIndex,
                        savedPositionMs = savedPositionMs,
                        savedDurationMs = savedDurationMs,
                        playWhenRestored = autoPlay,
                    )
                }

                _state.value = PlaybackRuntimeState(
                    restored = true,
                    queueSize = queue.size,
                    currentIndex = savedIndex,
                    savedPositionMs = savedPositionMs,
                    savedDurationMs = savedDurationMs,
                    autoPlayWhenRestored = autoPlay,
                )

                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "playback_runtime_restore_success",
                    fields = baseFields(
                        queue = queue,
                        startIndex = savedIndex,
                        savedPositionMs = savedPositionMs,
                        savedDurationMs = savedDurationMs,
                        autoPlay = autoPlay,
                        result = LogFields.Result.SUCCESS,
                        reason = null,
                        durationMs = elapsedMs(startedAt),
                    ),
                )
                RuntimeRestoreResult.Restored
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val reason = "exception"
            _state.value = _state.value.copy(
                restored = false,
                lastFailureReason = reason,
            )
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_runtime_restore_failed",
                throwable = error,
                fields = mapOf(
                    "store" to STORE_NAME,
                    "key" to key,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to reason,
                ),
            )
            RuntimeRestoreResult.Failed(reason, error)
        }
    }

    override suspend fun persist() = Unit

    override suspend fun prune(nowEpochMs: Long) = Unit

    private fun baseFields(
        queue: List<MusicItem>,
        startIndex: Int,
        savedPositionMs: Long,
        savedDurationMs: Long,
        autoPlay: Boolean,
        result: String,
        reason: String?,
        durationMs: Long,
    ): Map<String, Any?> = buildMap {
        put("store", STORE_NAME)
        put("key", key)
        put("queueSize", queue.size)
        put("startIndex", startIndex)
        put("savedPositionMs", savedPositionMs)
        put("savedDurationMs", savedDurationMs)
        put("autoPlay", autoPlay)
        put("result", result)
        put("durationMs", durationMs)
        if (reason != null) put("reason", reason)
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val STORE_NAME = "playback"
    }
}
