package com.zili.android.musicfreeandroid.player.controller

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.player.ext.toMediaItem
import com.zili.android.musicfreeandroid.player.model.PlaybackState
import com.zili.android.musicfreeandroid.player.model.PlayerState
import com.zili.android.musicfreeandroid.player.queue.PlayQueue
import com.zili.android.musicfreeandroid.player.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaController: MediaController? = null
    private var positionUpdateJob: kotlinx.coroutines.Job? = null

    val playQueue = PlayQueue()

    private val _playerState = MutableStateFlow(PlayerState.EMPTY)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffleEnabled: Boolean = false

    suspend fun connect() {
        if (mediaController != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val controller = suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
        mediaController = controller
        controller.addListener(playerListener)
        emitState()
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        emitState()
    }

    fun playItem(item: MusicItem) {
        val index = playQueue.items.indexOfFirst {
            it.id == item.id && it.platform == item.platform
        }
        if (index >= 0) {
            playQueue.skipTo(index)
        } else {
            playQueue.add(item)
            playQueue.skipTo(playQueue.size - 1)
        }
        setMediaItemAndPlay(item)
    }

    fun playQueue(items: List<MusicItem>, startIndex: Int = 0) {
        playQueue.setQueue(items, startIndex)
        if (shuffleEnabled) playQueue.shuffle()
        playQueue.currentItem?.let { setMediaItemAndPlay(it) }
    }

    fun skipToNext() {
        val next = playQueue.next(repeatMode) ?: return
        setMediaItemAndPlay(next)
    }

    fun skipToPrevious() {
        val position = mediaController?.currentPosition ?: 0L
        if (position > 3_000L) {
            mediaController?.seekTo(0L)
            return
        }
        val prev = playQueue.previous(repeatMode) ?: return
        setMediaItemAndPlay(prev)
    }

    fun skipTo(index: Int) {
        val item = playQueue.skipTo(index) ?: return
        setMediaItemAndPlay(item)
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        emitState()
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        emitState()
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled) {
            playQueue.shuffle()
        } else {
            playQueue.unshuffle()
        }
        emitState()
    }

    fun addToQueue(item: MusicItem) {
        playQueue.add(item)
    }

    fun addNextInQueue(item: MusicItem) {
        playQueue.addNext(item)
    }

    fun removeFromQueue(index: Int): MusicItem? {
        val wasCurrentItem = playQueue.currentItem
        val newCurrent = playQueue.remove(index)
        if (newCurrent != null && newCurrent != wasCurrentItem) {
            setMediaItemAndPlay(newCurrent)
        } else if (newCurrent == null) {
            mediaController?.stop()
            mediaController?.clearMediaItems()
        }
        emitState()
        return newCurrent
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        playQueue.move(fromIndex, toIndex)
    }

    fun release() {
        positionUpdateJob?.cancel()
        mediaController?.release()
        mediaController = null
    }

    private fun setMediaItemAndPlay(item: MusicItem) {
        val controller = mediaController ?: return
        val mediaItem = item.toMediaItem()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                handleTrackEnded()
            }
            emitState()
            updatePositionTracking()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            emitState()
            updatePositionTracking()
        }

        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            emitState()
        }
    }

    private fun handleTrackEnded() {
        when (repeatMode) {
            RepeatMode.ONE -> {
                mediaController?.seekTo(0L)
                mediaController?.play()
            }
            RepeatMode.ALL -> skipToNext()
            RepeatMode.OFF -> {
                val next = playQueue.next(repeatMode)
                if (next != null) {
                    setMediaItemAndPlay(next)
                }
            }
        }
    }

    private fun updatePositionTracking() {
        val isPlaying = mediaController?.isPlaying == true
        if (isPlaying) {
            startPositionUpdates()
        } else {
            positionUpdateJob?.cancel()
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                emitState()
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun emitState() {
        val controller = mediaController
        _playerState.value = PlayerState(
            currentItem = playQueue.currentItem,
            isPlaying = controller?.isPlaying == true,
            playbackState = controller?.playbackState.toPlaybackState(),
            duration = controller?.duration?.coerceAtLeast(0L) ?: 0L,
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
        )
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 200L
    }
}

private fun Int?.toPlaybackState(): PlaybackState = when (this) {
    Player.STATE_IDLE -> PlaybackState.IDLE
    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
    Player.STATE_READY -> PlaybackState.READY
    Player.STATE_ENDED -> PlaybackState.ENDED
    else -> PlaybackState.IDLE
}
