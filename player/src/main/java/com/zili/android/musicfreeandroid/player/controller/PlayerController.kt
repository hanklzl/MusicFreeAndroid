package com.zili.android.musicfreeandroid.player.controller

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.player.ext.defaultAlbumArtworkUri
import com.zili.android.musicfreeandroid.player.ext.toMediaItem
import com.zili.android.musicfreeandroid.player.model.PlaybackState
import com.zili.android.musicfreeandroid.player.model.PlayerState
import com.zili.android.musicfreeandroid.player.queue.PlayQueue
import com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationQueueControls
import com.zili.android.musicfreeandroid.player.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackNotificationQueueControls {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectionMutex = Mutex()
    private var mediaController: MediaController? = null
    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private var connectJob: Job? = null
    private val defaultArtworkUri = context.defaultAlbumArtworkUri()

    val playQueue = PlayQueue()

    private val _playerState = MutableStateFlow(PlayerState.EMPTY)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    private val _playHistory = MutableStateFlow<List<MusicItem>>(emptyList())
    val playHistory: StateFlow<List<MusicItem>> = _playHistory.asStateFlow()
    private val _queueState = MutableStateFlow(PlayQueueSnapshot.EMPTY)
    val queueState: StateFlow<PlayQueueSnapshot> = _queueState.asStateFlow()
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffleEnabled: Boolean = false

    init {
        attachNotificationControls()
    }

    suspend fun connect() {
        connectionMutex.withLock {
            attachNotificationControls()
            if (mediaController != null) return
            withContext(Dispatchers.Main.immediate) {
                attachNotificationControls()
                if (mediaController != null) return@withContext
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
                attachNotificationControls()
                emitState()
            }
        }
    }

    fun play() {
        withConnectedController { controller ->
            controller.play()
        }
    }

    fun pause() {
        withConnectedController { controller ->
            controller.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        withConnectedController { controller ->
            controller.seekTo(positionMs)
        }
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
        withConnectedController { controller ->
            val position = controller.currentPosition
            if (position > 3_000L) {
                controller.seekTo(0L)
                return@withConnectedController
            }
            val prev = playQueue.previous(repeatMode) ?: return@withConnectedController
            val mediaItem = prev.toMediaItem(defaultArtworkUri)
            recordHistory(prev)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    override fun skipToPreviousFromNotification() {
        skipToPrevious()
    }

    override fun skipToNextFromNotification() {
        skipToNext()
    }

    fun skipTo(index: Int) {
        val item = playQueue.skipTo(index) ?: return
        setMediaItemAndPlay(item)
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        runOnControllerThread {
            emitState()
        }
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        runOnControllerThread {
            emitState()
        }
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled) {
            playQueue.shuffle()
        } else {
            playQueue.unshuffle()
        }
        runOnControllerThread {
            emitState()
        }
    }

    fun addToQueue(item: MusicItem) {
        playQueue.add(item)
    }

    fun addNextInQueue(item: MusicItem) {
        playQueue.addNext(item)
    }

    fun clearHistory() {
        _playHistory.value = emptyList()
    }

    fun reset() {
        runOnControllerThread {
            positionUpdateJob?.cancel()
            positionUpdateJob = null
            mediaController?.stop()
            mediaController?.clearMediaItems()
            playQueue.clear()
            repeatMode = RepeatMode.OFF
            shuffleEnabled = false
            _playerState.value = PlayerState.EMPTY
        }
    }

    fun removeFromQueue(index: Int): MusicItem? {
        val wasCurrentItem = playQueue.currentItem
        val newCurrent = playQueue.remove(index)
        if (newCurrent != null && newCurrent != wasCurrentItem) {
            setMediaItemAndPlay(newCurrent)
        } else if (newCurrent == null) {
            runOnControllerThread {
                mediaController?.stop()
                mediaController?.clearMediaItems()
            }
        }
        runOnControllerThread {
            emitState()
        }
        return newCurrent
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        playQueue.move(fromIndex, toIndex)
    }

    fun release() {
        connectJob?.cancel()
        runOnControllerThread {
            connectJob = null
            positionUpdateJob?.cancel()
            positionUpdateJob = null
            val controller = mediaController
            mediaController = null
            controller?.removeListener(playerListener)
            controller?.release()
            PlaybackNotificationCommandHandler.detach(this)
        }
    }

    private fun setMediaItemAndPlay(item: MusicItem) {
        withConnectedController { controller ->
            try {
                recordHistory(item)
                val mediaItem = item.toMediaItem(defaultArtworkUri)
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            } catch (e: RuntimeException) {
                _errorEvents.tryEmit("播放失败: ${e.message}")
            }
        }
    }

    private fun withConnectedController(action: (MediaController) -> Unit) {
        attachNotificationControls()
        mediaController?.let { controller ->
            runOnControllerThread {
                action(controller)
                emitState()
            }
            return
        }

        val existingConnectJob = connectJob
        if (existingConnectJob?.isActive == true) {
            scope.launch {
                existingConnectJob.join()
                runOnControllerThread {
                    mediaController?.let { controller ->
                        action(controller)
                        emitState()
                    }
                }
            }
            return
        }

        connectJob = scope.launch {
            runCatching {
                connect()
            }.onFailure { e ->
                _errorEvents.emit("播放服务连接失败: ${e.message}")
                return@launch
            }

            runOnControllerThread {
                attachNotificationControls()
                mediaController?.let { controller ->
                    action(controller)
                    emitState()
                }
            }
        }
    }

    private fun attachNotificationControls() {
        PlaybackNotificationCommandHandler.attach(this)
    }

    private fun runOnControllerThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        context.mainExecutor.execute {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

    private fun recordHistory(item: MusicItem) {
        val current = _playHistory.value
        val deduped = current.filterNot {
            it.id == item.id && it.platform == item.platform
        }
        _playHistory.value = listOf(item) + deduped.take(HISTORY_MAX_SIZE - 1)
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

    private fun emitQueueState() {
        _queueState.value = PlayQueueSnapshot(
            items = playQueue.items,
            currentIndex = playQueue.currentIndex,
        )
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 200L
        private const val HISTORY_MAX_SIZE = 200
    }
}

private fun Int?.toPlaybackState(): PlaybackState = when (this) {
    Player.STATE_IDLE -> PlaybackState.IDLE
    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
    Player.STATE_READY -> PlaybackState.READY
    Player.STATE_ENDED -> PlaybackState.ENDED
    else -> PlaybackState.IDLE
}
