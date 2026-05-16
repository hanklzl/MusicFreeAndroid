package com.hank.musicfree.bootstrap

import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlayQueueRepository
import com.hank.musicfree.di.ApplicationScope
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
                    val startIndex = savedIndex.coerceIn(0, queue.lastIndex)
                    val autoPlay = playbackRuntimeSettings.autoPlayWhenAppStart()
                    withContext(Dispatchers.Main.immediate) {
                        playerController.restoreQueue(
                            items = queue,
                            startIndex = startIndex,
                            playWhenRestored = autoPlay,
                        )
                    }
                    MfLog.detail(
                        category = LogCategory.PLAYER,
                        event = "playback_startup_restore_completed",
                        fields = mapOf(
                            "queueSize" to queue.size,
                            "startIndex" to startIndex,
                            "autoPlay" to autoPlay,
                            "durationMs" to elapsedMs(startedAt),
                        ),
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
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
