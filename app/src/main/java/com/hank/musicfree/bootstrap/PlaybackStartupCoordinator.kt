package com.hank.musicfree.bootstrap

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlayQueueRepository
import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.controller.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PlaybackStartupCoordinator @Inject constructor(
    private val playerController: PlayerController,
    private val playQueueRepository: PlayQueueRepository,
    private val appPreferences: AppPreferences,
    private val playbackRuntimeSettings: PlaybackRuntimeSettings,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun start() {
        applicationScope.launch(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            try {
                val queue = playQueueRepository.getQueue()
                if (queue.isNotEmpty()) {
                    val savedIndex = appPreferences.currentMusicIndex.first()
                    val savedPositionMs = appPreferences.currentMusicPositionMs.first()
                    val savedDurationMs = appPreferences.currentMusicDurationMs.first()
                    val startIndex = savedIndex.coerceIn(0, queue.lastIndex)
                    val current = queue.getOrNull(startIndex)
                    val autoPlay = playbackRuntimeSettings.autoPlayWhenAppStart()
                    withContext(Dispatchers.Main.immediate) {
                        playerController.restoreQueue(
                            items = queue,
                            startIndex = startIndex,
                            savedPositionMs = savedPositionMs,
                            savedDurationMs = savedDurationMs,
                            playWhenRestored = autoPlay,
                        )
                    }
                    MfLog.detail(
                        category = LogCategory.PLAYER,
                        event = "playback_startup_restore_completed",
                        fields = mapOf(
                            "queueSize" to queue.size,
                            "startIndex" to startIndex,
                            "currentPlatform" to current?.platform,
                            "currentItemId" to current?.id,
                            "savedPositionMs" to savedPositionMs,
                            "savedDurationMs" to savedDurationMs,
                            "autoPlay" to autoPlay,
                            "durationMs" to elapsedMs(startedAt),
                        ) + current.diagnosticFields(),
                    )
                } else {
                    MfLog.detail(
                        category = LogCategory.PLAYER,
                        event = "playback_startup_restore_skipped",
                        fields = mapOf("reason" to "empty_queue"),
                    )
                }

                playerController.queueState.drop(1).collect { snapshot ->
                    playQueueRepository.saveQueue(snapshot.items)
                    appPreferences.setCurrentMusicIndex(snapshot.currentIndex)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_startup_restore_failed",
                    throwable = error,
                    fields = mapOf(
                        "reason" to "exception",
                        "durationMs" to elapsedMs(startedAt),
                    ),
                )
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            var lastIsPlaying = false
            var lastItemKey: String? = null
            var lastPersistedPosition = -1L
            var lastPersistedDuration = -1L
            var lastTickAt = System.currentTimeMillis()
            playerController.playerState.collect { state ->
                val itemKey = state.currentItem?.let { "${it.platform}:${it.id}" }
                val now = System.currentTimeMillis()

                val flush = when {
                    // 切歌：把上一首终态写入（如果有）
                    itemKey != lastItemKey -> true
                    // 边沿：从播放变为暂停 / 停止
                    lastIsPlaying && !state.isPlaying -> true
                    // 周期：播放中 5s 写一次，且变化超过 1s
                    state.isPlaying &&
                        (now - lastTickAt) >= 5_000L &&
                        abs(state.position - lastPersistedPosition) >= 1_000L -> true
                    // duration 首次更新或变更
                    state.duration > 0L && state.duration != lastPersistedDuration -> true
                    else -> false
                }

                if (flush) {
                    try {
                        if (state.position != lastPersistedPosition) {
                            appPreferences.setCurrentMusicPositionMs(state.position.coerceAtLeast(0L))
                            lastPersistedPosition = state.position
                        }
                        if (state.duration > 0L && state.duration != lastPersistedDuration) {
                            appPreferences.setCurrentMusicDurationMs(state.duration)
                            lastPersistedDuration = state.duration
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        MfLog.error(
                            category = LogCategory.PLAYER,
                            event = "playback_position_persist_failed",
                            throwable = error,
                            fields = mapOf(
                                "positionMs" to state.position,
                                "durationMs" to state.duration,
                            ),
                        )
                    }
                    lastTickAt = now
                }

                lastIsPlaying = state.isPlaying
                lastItemKey = itemKey
            }
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}

private fun MusicItem?.diagnosticFields(): Map<String, Any?> = mapOf(
    "rawKeyCount" to (this?.raw?.size ?: 0),
    "hasQualities" to !this?.qualities.isNullOrEmpty(),
    "hasUrl" to !this?.url.isNullOrBlank(),
    "hasLocalPath" to !this?.localPath.isNullOrBlank(),
)
