package com.hank.musicfree.player.controller

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.hank.musicfree.core.media.EmptyMediaSourceResolver
import com.hank.musicfree.core.media.EmptyStaleUrlRefresher
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.media.StaleUrlRefresher
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlaybackMode
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.RepeatMode
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.ext.defaultAlbumArtworkUri
import com.hank.musicfree.player.ext.toMediaItem
import com.hank.musicfree.player.model.PlaybackState
import com.hank.musicfree.player.model.PlayerState
import com.hank.musicfree.player.network.PlaybackNetworkStateProvider
import com.hank.musicfree.player.queue.PlayQueue
import com.hank.musicfree.player.queue.PlayQueueSnapshot
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import com.hank.musicfree.player.service.PlaybackNotificationQueueDiagnostics
import com.hank.musicfree.player.service.PlaybackNotificationQueueControls
import com.hank.musicfree.player.service.PlaybackService
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
import com.hank.musicfree.player.source.TrackHeaderRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import com.hank.musicfree.player.prefetch.ProgressTick
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaSourceResolver: MediaSourceResolver = EmptyMediaSourceResolver,
    private val playbackRuntimeSettings: PlaybackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
    private val networkStateProvider: PlaybackNetworkStateProvider = PlaybackNetworkStateProvider.AlwaysAllowed,
    private val trackHeaderRegistry: TrackHeaderRegistry = TrackHeaderRegistry(),
    private val playbackCacheKeyRegistrar: PlaybackCacheKeyRegistrar = PlaybackCacheKeyRegistrar(
        trackHeaderRegistry,
    ),
    private val staleUrlRefresher: StaleUrlRefresher = EmptyStaleUrlRefresher,
    private val listenTracker: ListenTracker,
    private val currentSidProvider: CurrentSidProvider,
    private val playCacheTelemetry: PlayCacheTelemetry,
) : PlaybackNotificationQueueControls {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectionMutex = Mutex()
    private var mediaController: MediaController? = null
    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private var connectJob: Job? = null
    private var playbackRequestJob: Job? = null
    private val playbackRequestGeneration = AtomicLong(0L)
    private val defaultArtworkUri = context.defaultAlbumArtworkUri()

    /**
     * Per-(platform, id) flag tracking whether we already attempted to evict and
     * re-resolve the current play URL after an input/source failure that may be
     * caused by a stale URL or corrupted byte-cache entry. Cleared on queue advance
     * so revisiting the same item later gets a fresh budget. Process-local; not
     * persisted. Spec §5.7 of `2026-05-11-plugin-engine-alignment-design.md`.
     */
    private val staleUrlRetryState = ConcurrentHashMap<Pair<String, String>, Boolean>()
    private val playFailureSourceRetryState = ConcurrentHashMap<Pair<String, String>, Boolean>()

    /**
     * Quality currently active in the player. Captured at `setMediaItemAndPlay`
     * time using the user's default; updated by `changeQuality`. Used to target
     * the right slot when evicting cache on HTTP failures.
     */
    private val _currentQualityFlow = MutableStateFlow(PlayQuality.STANDARD)
    val currentQualityFlow: StateFlow<PlayQuality> = _currentQualityFlow.asStateFlow()

    private var currentPlayQuality: PlayQuality
        get() = _currentQualityFlow.value
        set(value) {
            _currentQualityFlow.value = value
        }

    @Volatile
    private var pendingRestorePosition: Long? = null

    @VisibleForTesting
    internal val pendingRestorePositionForTest: Long?
        get() = pendingRestorePosition

    @VisibleForTesting
    internal fun setMediaControllerForTest(controller: MediaController?) {
        mediaController = controller
    }

    val playQueue = PlayQueue()

    private val _playerState = MutableStateFlow(PlayerState.EMPTY)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    private val _playHistory = MutableStateFlow<List<MusicItem>>(emptyList())
    val playHistory: StateFlow<List<MusicItem>> = _playHistory.asStateFlow()
    private val _queueState = MutableStateFlow(PlayQueueSnapshot.EMPTY)
    val queueState: StateFlow<PlayQueueSnapshot> = _queueState.asStateFlow()
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    /**
     * Emits a [ProgressTick] whenever [playerState] updates with a non-null current item.
     * Used by [com.hank.musicfree.player.prefetch.PrefetchCoordinator] to decide when to
     * prefetch the next queue item.
     */
    val progressTickFlow = _playerState
        .filterNotNull()
        .mapNotNull { state ->
            val item = state.currentItem ?: return@mapNotNull null
            ProgressTick(item, state.position, state.duration.coerceAtLeast(0L))
        }

    private val _nextItemFlow = MutableStateFlow<MusicItem?>(null)

    /**
     * The item that would play after the current track, updated whenever the queue changes.
     * Null when the queue is empty or playback would stop at the current track.
     */
    val nextItemFlow: StateFlow<MusicItem?> = _nextItemFlow.asStateFlow()

    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffleEnabled: Boolean = false
    private var playbackSpeed: Float = PlaybackSpeeds.DEFAULT

    init {
        attachNotificationControls()
    }

    suspend fun connect() {
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "player_connect_start",
            fields = mapOf("status" to "start"),
        )
        try {
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
                                } catch (error: Exception) {
                                    cont.resumeWithException(error)
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
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "player_connect_success",
                fields = mapOf("status" to "success"),
            )
        } catch (error: Exception) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "player_connect_failed",
                throwable = error,
                fields = mapOf(
                    "status" to "failed",
                    "errorClass" to error::class.java.name,
                ),
            )
            throw error
        }
    }

    fun play() {
        withConnectedController { controller ->
            val activationReason = queueActivationReasonForPlay(controller)
            if (activationReason != null) {
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "player_play_queue_activation_required",
                    fields = mapOf(
                        "status" to "start",
                        "operation" to "play",
                        "reason" to activationReason,
                        "queueIndex" to playQueue.currentIndex,
                        "queueSize" to playQueue.size,
                        "currentItemId" to playQueue.currentItem?.id.orEmpty(),
                        "preparedMediaId" to controller.currentMediaItem?.mediaId.orEmpty(),
                        "controllerPlaybackState" to controller.playbackState,
                    ),
                )
                activateCurrentQueueItem(operation = "play")
            } else {
                controller.play()
            }
        }
    }

    fun pause() {
        withConnectedController { controller ->
            controller.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        val sanitized = positionMs.coerceAtLeast(0L)
        val controller = mediaController
        if (controller == null || controller.currentMediaItem == null) {
            // Not yet activated — record into pending and mirror into UI state.
            pendingRestorePosition = sanitized
            _playerState.value = _playerState.value.copy(position = sanitized)
            return
        }
        withConnectedController { c ->
            c.seekTo(sanitized)
        }
    }

    fun playItem(item: MusicItem) {
        pendingRestorePosition = null
        val index = playQueue.items.indexOfFirst {
            it.id == item.id && it.platform == item.platform
        }
        val targetIndex = if (index >= 0) {
            index
        } else {
            playQueue.add(item)
            emitQueueState()
            playQueue.size - 1
        }
        val queuedItem = playQueue.items.getOrNull(targetIndex) ?: return
        startQueuePlaybackTransition(
            targetIndex = targetIndex,
            targetItem = queuedItem,
            operation = "play_item",
        )
    }

    fun playQueue(items: List<MusicItem>, startIndex: Int = 0) {
        pendingRestorePosition = null
        playQueue.setQueue(items, startIndex)
        if (shuffleEnabled) playQueue.shuffle()
        playQueue.currentItem?.let { setMediaItemAndPlay(it) }
        emitQueueState()
    }

    fun restoreQueue(
        items: List<MusicItem>,
        startIndex: Int = 0,
        savedPositionMs: Long = 0L,
        savedDurationMs: Long = 0L,
        playWhenRestored: Boolean = false,
    ) {
        playQueue.setQueue(items, startIndex)
        pendingRestorePosition = savedPositionMs.takeIf { it > 0L }
        if (playWhenRestored) {
            playQueue.currentItem?.let { setMediaItemAndPlay(it) }
        } else {
            runOnControllerThread {
                _playerState.value = PlayerState(
                    currentItem = playQueue.currentItem,
                    isPlaying = false,
                    playbackState = mediaController?.playbackState.toPlaybackState(),
                    duration = savedDurationMs.coerceAtLeast(0L),
                    position = savedPositionMs.coerceAtLeast(0L),
                    repeatMode = repeatMode,
                    shuffleEnabled = shuffleEnabled,
                    playbackSpeed = playbackSpeed,
                )
                emitQueueState()
            }
        }
        emitQueueState()
    }

    fun skipToNext() {
        pendingRestorePosition = null
        val previousIndex = playQueue.currentIndex
        val previousItem = playQueue.currentItem
        val nextIndex = playQueue.peekNextIndex(repeatMode) ?: return
        val next = playQueue.items.getOrNull(nextIndex) ?: return
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "player_skip_next",
            fields = mapOf(
                "fromIndex" to previousIndex,
                "toIndex" to nextIndex,
                "fromItemId" to previousItem?.id,
                "toItemId" to next.id,
                "platform" to next.platform,
            ) + next.diagnosticFields(prefix = "to"),
        )
        startQueuePlaybackTransition(
            targetIndex = nextIndex,
            targetItem = next,
            operation = "skip_next",
        )
    }

    fun skipToPrevious() {
        pendingRestorePosition = null
        val currentIndex = playQueue.currentIndex
        val currentItem = playQueue.currentItem
        val previousIndex = playQueue.peekPreviousIndex(repeatMode) ?: return
        val previous = playQueue.items.getOrNull(previousIndex) ?: return
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "player_skip_previous",
            fields = mapOf(
                "fromIndex" to currentIndex,
                "toIndex" to previousIndex,
                "fromItemId" to currentItem?.id,
                "toItemId" to previous.id,
                "platform" to previous.platform,
            ) + previous.diagnosticFields(prefix = "to"),
        )
        startQueuePlaybackTransition(
            targetIndex = previousIndex,
            targetItem = previous,
            operation = "skip_previous",
        )
    }

    override fun skipToPreviousFromNotification() {
        skipToPrevious()
    }

    override fun skipToNextFromNotification() {
        skipToNext()
    }

    override fun playFromNotification() {
        // This is only invoked from `PlaybackService.onPlayerCommandRequest` when
        // `session.player.mediaItemCount == 0`, i.e. the session has no loaded item.
        // We must always reload the queue's current item via `activateCurrentQueueItem`
        // — never shortcut to `play()`, because `play()` would call `controller.play()`
        // which re-enters `onPlayerCommandRequest` and recurses until StackOverflow
        // when the controller's cached `currentMediaItem` is stale relative to the
        // empty session player.
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "playback_notification_play",
            fields = mapOf(
                "status" to "start",
                "operation" to "notification_play",
                "hasPreparedMediaItem" to (mediaController?.currentMediaItem != null),
                "preparedMediaId" to mediaController?.currentMediaItem?.mediaId.orEmpty(),
                "controllerPlaybackState" to (mediaController?.playbackState ?: Player.STATE_IDLE),
                "queueIndex" to playQueue.currentIndex,
                "queueSize" to playQueue.size,
                "currentItemId" to playQueue.currentItem?.id.orEmpty(),
            ),
        )
        activateCurrentQueueItem(operation = "notification_play")
    }

    override fun notificationDiagnostics(): PlaybackNotificationQueueDiagnostics {
        return PlaybackNotificationQueueDiagnostics(
            queueIndex = playQueue.currentIndex,
            queueSize = playQueue.size,
            currentItemId = playQueue.currentItem?.id,
        )
    }

    override fun closeFromNotification() {
        reset()
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "playback_notification_close",
            fields = mapOf(
                "status" to LogFields.Result.SUCCESS,
                "operation" to "close_notification",
            ),
        )
    }

    fun skipTo(index: Int) {
        pendingRestorePosition = null
        val item = playQueue.items.getOrNull(index) ?: return
        startQueuePlaybackTransition(
            targetIndex = index,
            targetItem = item,
            operation = "skip_to",
        )
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

    fun cyclePlaybackMode() {
        when (PlaybackMode.from(shuffleEnabled, repeatMode)) {
            PlaybackMode.Shuffle -> {
                shuffleEnabled = false
                playQueue.unshuffle()
                repeatMode = RepeatMode.ONE
            }
            PlaybackMode.Single -> {
                if (shuffleEnabled) {
                    shuffleEnabled = false
                    playQueue.unshuffle()
                }
                repeatMode = RepeatMode.ALL
            }
            PlaybackMode.Queue -> {
                repeatMode = RepeatMode.ALL
                if (!shuffleEnabled) {
                    shuffleEnabled = true
                    playQueue.shuffle()
                }
            }
        }
        runOnControllerThread {
            emitState()
            emitQueueState()
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
            emitQueueState()
        }
    }

    fun addToQueue(item: MusicItem) {
        playQueue.add(item)
        emitQueueState()
    }

    fun addNextInQueue(item: MusicItem) {
        playQueue.addNext(item)
        emitQueueState()
    }

    fun clearHistory() {
        _playHistory.value = emptyList()
    }

    fun reset() {
        pendingRestorePosition = null
        cancelPlaybackRequest()
        runOnControllerThread {
            positionUpdateJob?.cancel()
            positionUpdateJob = null
            mediaController?.stop()
            mediaController?.clearMediaItems()
            playQueue.clear()
            repeatMode = RepeatMode.OFF
            shuffleEnabled = false
            staleUrlRetryState.clear()
            playFailureSourceRetryState.clear()
            _playerState.value = PlayerState.EMPTY
            emitQueueState()
        }
    }

    fun removeFromQueue(index: Int): MusicItem? {
        pendingRestorePosition = null
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
            emitQueueState()
        }
        return newCurrent
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        playQueue.move(fromIndex, toIndex)
        emitQueueState()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        runOnControllerThread {
            mediaController?.let {
                it.setPlaybackParameters(PlaybackParameters(speed))
            }
            emitState()
        }
    }

    fun changeQuality(quality: PlayQuality) {
        val item = playQueue.currentItem ?: return
        val expectedIndex = playQueue.currentIndex
        val savedPosition = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val wasPlaying = mediaController?.isPlaying == true
        val qualityValue = quality.name.lowercase()

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val startedAt = System.nanoTime()
            if (!canPlayOverCurrentNetwork(item)) {
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "player_quality_change_failed",
                    fields = qualityChangeFields(
                        item = item,
                        quality = qualityValue,
                        startedAt = startedAt,
                        reason = LogFields.Reason.CELLULAR_BLOCKED,
                    ),
                )
                _errorEvents.emit("当前网络不允许播放，请在设置中开启移动网络播放")
                return@launch
            }
            val resolution = try {
                // Quality-change is a mid-session operation: reuse the current play
                // session's sid so the new resolve event correlates with the same
                // psid in the log timeline.
                mediaSourceResolver.resolve(item, qualityValue, sid = currentSidProvider.peek())
            } catch (error: CancellationException) {
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "player_quality_change_cancelled",
                    fields = qualityChangeFields(
                        item = item,
                        quality = qualityValue,
                        startedAt = startedAt,
                        result = LogFields.Result.CANCELLED,
                        reason = LogFields.Reason.CANCELLED,
                    ),
                )
                throw error
            } catch (error: Exception) {
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "player_quality_change_failed",
                    throwable = error,
                    fields = qualityChangeFields(
                        item = item,
                        quality = qualityValue,
                        startedAt = startedAt,
                        reason = "resolver_exception",
                    ),
                )
                null
            }
            val playable = resolution?.item
            if (playable == null || playable.url.isNullOrBlank()) {
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "player_quality_change_failed",
                    fields = qualityChangeFields(
                        item = item,
                        quality = qualityValue,
                        startedAt = startedAt,
                        reason = "no_source",
                    ),
                )
                _errorEvents.emit("当前歌曲不支持该音质")
                return@launch
            }
            if (!playQueue.isCurrentItem(expectedIndex, item)) return@launch
            val changedUrl = requireNotNull(playable.url)
            val source = resolution.source
            registerPlayCacheKey(
                item = item,
                url = changedUrl,
                headers = source.headers.orEmpty(),
                userAgent = source.userAgent,
                quality = quality,
                cachePolicy = resolution.cachePolicy,
                trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
            )
            playQueue.replaceCurrent(expectedIndex, item, playable)
            emitQueueState()
            withConnectedController { controller ->
                if (!playQueue.isCurrentItem(expectedIndex, playable)) return@withConnectedController
                try {
                    val mediaItem = playable.toMediaItem(defaultArtworkUri)
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    if (savedPosition > 0L) controller.seekTo(savedPosition)
                    if (wasPlaying) controller.play()
                    currentPlayQuality = quality
                    // Switching quality starts a fresh playback for the current item,
                    // so any prior stale-url retry credit no longer applies.
                    staleUrlRetryState.clear()
                } catch (e: RuntimeException) {
                    MfLog.error(
                        category = LogCategory.PLAYER,
                        event = "player_quality_change_failed",
                        throwable = e,
                        fields = qualityChangeFields(
                            item = playable,
                            quality = qualityValue,
                            startedAt = startedAt,
                            reason = "prepare_failed",
                        ),
                    )
                    _errorEvents.tryEmit("切换音质失败: ${e.message}")
                }
            }
        }
    }

    fun release() {
        connectJob?.cancel()
        cancelPlaybackRequest()
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

    private fun setMediaItemAndPlay(
        item: MusicItem,
        expectedIndex: Int = playQueue.currentIndex,
        rollbackIndex: Int? = null,
    ) {
        attachNotificationControls()
        val generation = beginPlaybackRequest()
        // New playback for this item — drop any prior stale-url retry flag so the
        // first failure on the fresh URL is allowed to trigger one refresh.
        staleUrlRetryState.remove(item.platform to item.id)
        playFailureSourceRetryState.remove(item.platform to item.id)
        // Mint the sid synchronously (before the coroutine starts) so rapid
        // successive calls to setMediaItemAndPlay each capture their own sid.
        // A coroutine that suspends at resolve() must never read the global
        // currentSid that a later call may have already overwritten.
        val sid = currentSidProvider.newSession()
        playbackRequestJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_start",
                fields = mapOf(
                    "status" to "start",
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "expectedIndex" to expectedIndex,
                    "operation" to "direct_play",
                ) + item.diagnosticFields(),
            )
            currentPlayQuality = playbackRuntimeSettings.defaultPlayQuality()
            playCacheTelemetry.playSessionStart(
                sid = sid,
                platform = item.platform,
                id = item.id,
                requestedQuality = playbackRuntimeSettings.defaultPlayQuality().name.lowercase(),
                networkType = "unknown", // wired up in a later Task with NetworkMonitor
                isOnline = true,
            )
            val playable = resolvePlayableItemForPlayback(item, sid = sid, operation = "direct_play")
            if (playable == null) {
                rollbackPlaybackSelection(expectedIndex, item, rollbackIndex)
                return@launch
            }
            if (!isActivePlaybackRequest(generation)) return@launch
            if (!playQueue.isCurrentItem(expectedIndex, item)) return@launch
            if (playable != item) {
                playQueue.replaceCurrent(expectedIndex, item, playable)
                emitQueueState()
            }
            playResolvedItem(playable, generation)
        }
    }

    private fun startQueuePlaybackTransition(
        targetIndex: Int,
        targetItem: MusicItem,
        operation: String,
    ) {
        attachNotificationControls()
        val generation = beginPlaybackRequest()
        staleUrlRetryState.remove(targetItem.platform to targetItem.id)
        playFailureSourceRetryState.remove(targetItem.platform to targetItem.id)
        val sid = currentSidProvider.newSession()
        playbackRequestJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_transition_start",
                fields = mapOf(
                    "status" to "start",
                    "operation" to operation,
                    "fromIndex" to playQueue.currentIndex,
                    "toIndex" to targetIndex,
                    "platform" to targetItem.platform,
                    "itemId" to targetItem.id,
                ) + targetItem.diagnosticFields(),
            )
            currentPlayQuality = playbackRuntimeSettings.defaultPlayQuality()
            playCacheTelemetry.playSessionStart(
                sid = sid,
                platform = targetItem.platform,
                id = targetItem.id,
                requestedQuality = playbackRuntimeSettings.defaultPlayQuality().name.lowercase(),
                networkType = "unknown",
                isOnline = true,
            )
            val playable = resolvePlayableItemForPlayback(targetItem, sid = sid, operation = operation)
            if (playable == null) {
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_transition_failed",
                    fields = mapOf(
                        "status" to "failed",
                        "operation" to operation,
                        "reason" to "resolve_failed",
                        "targetIndex" to targetIndex,
                        "platform" to targetItem.platform,
                        "itemId" to targetItem.id,
                    ) + targetItem.diagnosticFields(),
                )
                return@launch
            }
            if (!isActivePlaybackRequest(generation)) return@launch
            if (playQueue.items.getOrNull(targetIndex) != targetItem) {
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "playback_transition_cancelled",
                    fields = mapOf(
                        "status" to LogFields.Result.CANCELLED,
                        "operation" to operation,
                        "reason" to "queue_changed",
                        "targetIndex" to targetIndex,
                        "platform" to targetItem.platform,
                        "itemId" to targetItem.id,
                    ),
                )
                return@launch
            }
            playQueue.skipTo(targetIndex) ?: return@launch
            if (playable != targetItem) {
                playQueue.replaceCurrent(targetIndex, targetItem, playable)
            }
            emitQueueState()
            playResolvedItem(playable, generation)
        }
    }

    private fun activateCurrentQueueItem(operation: String) {
        val item = playQueue.currentItem ?: return
        setMediaItemAndPlay(
            item = item,
            expectedIndex = playQueue.currentIndex,
            rollbackIndex = null,
        )
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "playback_queue_item_activated",
            fields = mapOf(
                "status" to "start",
                "operation" to operation,
                "platform" to item.platform,
                "itemId" to item.id,
                "expectedIndex" to playQueue.currentIndex,
            ),
        )
    }

    private fun queueActivationReasonForPlay(controller: MediaController): String? {
        val preparedItem = controller.currentMediaItem ?: return "missing_media_item"
        if (controller.playbackState == Player.STATE_IDLE) return "controller_idle"
        val queueItem = playQueue.currentItem ?: return null
        val expectedMediaId = queueItem.toPlaybackMediaId()
        return if (preparedItem.mediaId != expectedMediaId) {
            "media_item_mismatch"
        } else {
            null
        }
    }

    private fun MusicItem.toPlaybackMediaId(): String = "$platform:$id"

    private fun playResolvedItem(playable: MusicItem, generation: Long) {
        withConnectedController { controller ->
            if (!isActivePlaybackRequest(generation)) return@withConnectedController
            try {
                recordHistory(playable)
                val mediaItem = playable.toMediaItem(defaultArtworkUri)
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            } catch (error: RuntimeException) {
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_failed",
                    throwable = error,
                    fields = mapOf(
                        "platform" to playable.platform,
                        "itemId" to playable.id,
                        "status" to "failed",
                    ),
                )
                _errorEvents.tryEmit("播放失败: ${error.message}")
            }
        }
    }

    private fun beginPlaybackRequest(): Long {
        playbackRequestJob?.cancel()
        return playbackRequestGeneration.incrementAndGet()
    }

    private fun cancelPlaybackRequest() {
        playbackRequestJob?.cancel()
        playbackRequestJob = null
        playbackRequestGeneration.incrementAndGet()
    }

    private fun isActivePlaybackRequest(generation: Long): Boolean =
        playbackRequestGeneration.get() == generation

    private suspend fun resolvePlayableItemForPlayback(
        item: MusicItem,
        sid: String?,
        operation: String,
    ): MusicItem? {
        val startedAt = System.nanoTime()
        return try {
            withTimeout(PLAYBACK_RESOLVE_TIMEOUT_MS) {
                resolvePlayableItem(item, sid = sid)
            }
        } catch (error: TimeoutCancellationException) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_transition_failed",
                throwable = error,
                fields = mapOf(
                    "status" to "failed",
                    "operation" to operation,
                    "reason" to "resolve_timeout",
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "durationMs" to elapsedMs(startedAt),
                ) + item.diagnosticFields(),
            )
            _errorEvents.emit("播放失败: 音源解析超时")
            null
        }
    }

    private fun rollbackPlaybackSelection(
        expectedIndex: Int,
        expectedItem: MusicItem,
        rollbackIndex: Int?,
    ) {
        if (
            rollbackIndex != null &&
            rollbackIndex >= 0 &&
            rollbackIndex < playQueue.size &&
            playQueue.isCurrentItem(expectedIndex, expectedItem)
        ) {
            playQueue.skipTo(rollbackIndex)
            runOnControllerThread {
                emitState()
                emitQueueState()
            }
        }
    }

    private suspend fun resolvePlayableItem(item: MusicItem, sid: String? = null): MusicItem? {
        if (!canPlayOverCurrentNetwork(item)) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_blocked_by_network",
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "status" to "blocked",
                    "network" to "cellular",
                ),
            )
            _errorEvents.emit("当前网络不允许播放，请在设置中开启移动网络播放")
            return null
        }

        if (item.isLocalPlaybackSource()) return item

        val startedAt = System.nanoTime()
        val resolution = try {
            mediaSourceResolver.resolve(item, sid = sid)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_resolve_failed",
                throwable = error,
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "status" to "failed",
                    "reason" to "resolver_exception",
                    "durationMs" to elapsedMs(startedAt),
                    "inputHadUrl" to !item.url.isNullOrBlank(),
                ) + item.diagnosticFields(),
            )
            null
        }
        val playable = resolution?.item
        if (playable != null && !playable.url.isNullOrBlank() && !canPlayOverCurrentNetwork(playable)) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_blocked_by_network",
                fields = mapOf(
                    "platform" to playable.platform,
                    "itemId" to playable.id,
                    "status" to "blocked",
                    "network" to "cellular",
                ),
            )
            _errorEvents.emit("当前网络不允许播放，请在设置中开启移动网络播放")
            return null
        }

        val resolvedUrl = playable?.url
        return if (playable != null && !resolvedUrl.isNullOrBlank()) {
            val source = resolution.source
            registerPlayCacheKey(
                item = item,
                url = resolvedUrl,
                headers = source.headers.orEmpty(),
                userAgent = source.userAgent,
                quality = currentPlayQuality,
                cachePolicy = resolution.cachePolicy,
                trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
            )
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_resolve_success",
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "resolverPlatform" to resolution.resolverPlatform,
                    "redirected" to resolution.redirected,
                    "status" to "success",
                    "durationMs" to elapsedMs(startedAt),
                    "inputHadUrl" to !item.url.isNullOrBlank(),
                ) + item.diagnosticFields(),
            )
            playable
        } else if (!item.url.isNullOrBlank()) {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_resolve_fallback_original_url",
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "status" to "fallback",
                    "durationMs" to elapsedMs(startedAt),
                    "inputHadUrl" to true,
                ) + item.diagnosticFields(),
            )
            item
        } else {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_resolve_failed",
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "status" to "failed",
                    "reason" to "no_source",
                    "durationMs" to elapsedMs(startedAt),
                    "inputHadUrl" to false,
                ) + item.diagnosticFields(),
            )
            _errorEvents.emit("播放失败: 无法解析音源")
            null
        }
    }

    private suspend fun canPlayOverCurrentNetwork(item: MusicItem): Boolean {
        if (item.isLocalPlaybackSource()) return true
        if (playbackRuntimeSettings.useCellularPlay()) return true
        return !networkStateProvider.currentState().isCellular
    }

    private fun qualityChangeFields(
        item: MusicItem,
        quality: String,
        startedAt: Long,
        result: String = LogFields.Result.FAILURE,
        reason: String,
    ): Map<String, Any?> = mapOf(
        "operation" to "change_quality",
        "platform" to item.platform,
        "itemId" to item.id,
        "itemName" to item.title,
        "quality" to quality,
        "durationMs" to elapsedMs(startedAt),
        "result" to result,
        "reason" to reason,
    )

    private fun registerPlayCacheKey(
        item: MusicItem,
        url: String,
        headers: Map<String, String>,
        userAgent: String?,
        quality: PlayQuality,
        cachePolicy: com.hank.musicfree.core.media.MediaSourceCachePolicy,
        trigger: PlaybackCacheKeyRegistrar.Trigger,
    ) {
        playbackCacheKeyRegistrar.register(
            platform = item.platform,
            itemId = item.id,
            url = url,
            headers = headers,
            userAgent = userAgent,
            quality = quality,
            cachePolicy = cachePolicy,
            trigger = trigger,
        )
    }

    private fun MusicItem.isLocalPlaybackSource(): Boolean {
        if (platform.equals("local", ignoreCase = true)) return true
        val source = url ?: return false
        return source.startsWith("file://", ignoreCase = true) ||
            source.startsWith("content://", ignoreCase = true) ||
            source.startsWith("android.resource://", ignoreCase = true) ||
            source.startsWith("/")
    }

    private fun PlayQueue.isCurrentItem(index: Int, item: MusicItem): Boolean {
        val current = currentItem ?: return false
        return currentIndex == index && current == item
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
            }.onFailure { error ->
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_failed",
                    throwable = error,
                    fields = mapOf(
                        "status" to "failed",
                        "reason" to "controller_connect_failed",
                    ),
                )
                _errorEvents.emit("播放服务连接失败: ${error.message}")
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

    private fun consumePendingRestoreIfReady(controllerDurationMs: Long, seek: (Long) -> Unit) {
        val pending = pendingRestorePosition ?: return
        val upper = if (controllerDurationMs > 0L) controllerDurationMs else pending
        val target = pending.coerceIn(0L, upper)
        pendingRestorePosition = null
        seek(target)
    }

    @VisibleForTesting
    internal fun consumePendingRestoreForTest(durationMs: Long, seek: (Long) -> Unit) {
        consumePendingRestoreIfReady(durationMs, seek)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                listenTracker.onTrackEnded(playQueue.currentItem)
                handleTrackEnded()
            }
            if (playbackState == Player.STATE_READY) {
                val controllerRef = mediaController
                if (controllerRef != null) {
                    consumePendingRestoreIfReady(controllerRef.duration) { target ->
                        controllerRef.seekTo(target)
                    }
                }
            }
            emitState()
            updatePositionTracking()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listenTracker.onIsPlayingChanged(isPlaying, playQueue.currentItem)
            emitState()
            updatePositionTracking()
        }

        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            listenTracker.onMediaItemTransition(playQueue.currentItem, reason)
            emitState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            listenTracker.onPositionDiscontinuity(reason)
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    /**
     * Visible for tests so they can synchronously simulate
     * `Player.Listener.onPlayerError` without standing up a fake MediaController.
     */
    @VisibleForTesting
    internal fun handleStaleUrlErrorForTest(error: PlaybackException) {
        handlePlaybackError(error)
    }

    @VisibleForTesting
    internal fun handlePlaybackErrorForTest(error: PlaybackException) {
        handlePlaybackError(error)
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val item = playQueue.currentItem ?: return
        val expectedIndex = playQueue.currentIndex

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val staleUrlRecovered = refreshStaleUrlAfterFailure(error, item, expectedIndex)
            if (staleUrlRecovered) return@launch

            val sourceChanged = changeSourceAfterFailure(error, item, expectedIndex)
            if (sourceChanged) return@launch

            applyPlaybackFailurePolicy(error, item, expectedIndex)
        }
    }

    private suspend fun refreshStaleUrlAfterFailure(
        error: PlaybackException,
        item: MusicItem,
        expectedIndex: Int,
    ): Boolean {
        if (!shouldRefreshSourceAfterFailure(error, item)) return false

        val key = item.platform to item.id
        if (staleUrlRetryState.putIfAbsent(key, true) != null) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_stale_url_retry_exhausted",
                throwable = error,
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "errorCode" to error.errorCode,
                ),
            )
            return false
        }

        val quality = currentPlayQuality
        val startedAt = System.nanoTime()

        runCatching {
            staleUrlRefresher.evictCacheEntry(item.platform, item.id, quality)
        }.onFailure { evictErr ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "plugin_media_source_cache_evict_failed",
                throwable = evictErr,
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "quality" to quality.name.lowercase(),
                ),
            )
        }

        val fresh = runCatching {
            // Reuse current play session sid so the refresh event correlates with
            // the original failure in the log timeline.
            staleUrlRefresher.resolveFresh(item, quality.name.lowercase(), sid = currentSidProvider.peek())
        }.onFailure { refreshErr ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "plugin_media_source_refresh_failed",
                throwable = refreshErr,
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "quality" to quality.name.lowercase(),
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
        }.getOrNull()

        val refreshedItem = fresh?.item
        val freshUrl = refreshedItem?.url
        if (refreshedItem == null || freshUrl.isNullOrBlank()) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "plugin_media_source_refresh_failed",
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "quality" to quality.name.lowercase(),
                    "reason" to "no_fresh_url",
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
            return false
        }

        if (!playQueue.isCurrentItem(expectedIndex, item)) {
            // User skipped between the failure and the resolve completion.
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "plugin_media_source_refresh_dropped",
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "reason" to "queue_changed",
                ),
            )
            return true
        }

        val source = fresh.source
        registerPlayCacheKey(
            item = item,
            url = freshUrl,
            headers = source.headers.orEmpty(),
            userAgent = source.userAgent,
            quality = quality,
            cachePolicy = fresh.cachePolicy,
            trigger = PlaybackCacheKeyRegistrar.Trigger.STALE_REFRESH,
        )
        playQueue.replaceCurrent(expectedIndex, item, refreshedItem)
        emitQueueState()

        var applied = true
        withConnectedController { controller ->
            if (!playQueue.isCurrentItem(expectedIndex, refreshedItem)) return@withConnectedController
            try {
                val mediaItem = refreshedItem.toMediaItem(defaultArtworkUri)
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "plugin_media_source_refreshed_after_failure",
                    fields = mapOf(
                        "platform" to item.platform,
                        "itemId" to item.id,
                        "quality" to quality.name.lowercase(),
                        "durationMs" to elapsedMs(startedAt),
                    ),
                )
            } catch (e: RuntimeException) {
                applied = false
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "plugin_media_source_refresh_failed",
                    throwable = e,
                    fields = mapOf(
                        "platform" to item.platform,
                        "itemId" to item.id,
                        "quality" to quality.name.lowercase(),
                        "reason" to "set_media_item_failed",
                        "durationMs" to elapsedMs(startedAt),
                    ),
                )
            }
        }
        return applied
    }

    private fun shouldRefreshSourceAfterFailure(error: PlaybackException, item: MusicItem): Boolean {
        if (item.isLocalPlaybackSource()) return false
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> true
            else -> false
        }
    }

    private suspend fun changeSourceAfterFailure(
        error: PlaybackException,
        item: MusicItem,
        expectedIndex: Int,
    ): Boolean {
        if (item.isLocalPlaybackSource()) return false
        if (!playbackRuntimeSettings.tryChangeSourceWhenPlayFail()) return false
        val key = item.platform to item.id
        if (playFailureSourceRetryState.putIfAbsent(key, true) != null) return false

        val startedAt = System.nanoTime()
        val quality = currentPlayQuality.name.lowercase()
        val resolution = runCatching {
            // Failure-recovery resolve: reuse current play session sid so the new
            // resolve event correlates with the same psid in the log timeline.
            mediaSourceResolver.resolve(item, quality, sid = currentSidProvider.peek())
        }.onFailure { resolveError ->
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_failure_source_change_failed",
                throwable = resolveError,
                fields = playbackFailureFields(
                    item = item,
                    error = error,
                    startedAt = startedAt,
                    reason = "resolver_exception",
                ),
            )
        }.getOrNull()

        val changedItem = resolution?.item
        val changedUrl = changedItem?.url
        if (changedItem == null || changedUrl.isNullOrBlank() || changedUrl == item.url) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_failure_source_change_failed",
                fields = playbackFailureFields(
                    item = item,
                    error = error,
                    startedAt = startedAt,
                    reason = if (changedUrl == item.url) "same_source" else "no_source",
                ),
            )
            return false
        }

        if (!playQueue.isCurrentItem(expectedIndex, item)) {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_failure_source_change_dropped",
                fields = mapOf(
                    "platform" to item.platform,
                    "itemId" to item.id,
                    "reason" to "queue_changed",
                ),
            )
            return true
        }

        val source = resolution.source
        registerPlayCacheKey(
            item = item,
            url = changedUrl,
            headers = source.headers.orEmpty(),
            userAgent = source.userAgent,
            quality = currentPlayQuality,
            cachePolicy = resolution.cachePolicy,
            trigger = PlaybackCacheKeyRegistrar.Trigger.FAILURE_SOURCE_CHANGE,
        )
        playQueue.replaceCurrent(expectedIndex, item, changedItem)
        emitQueueState()

        var applied = true
        withConnectedController { controller ->
            if (!playQueue.isCurrentItem(expectedIndex, changedItem)) return@withConnectedController
            try {
                controller.setMediaItem(changedItem.toMediaItem(defaultArtworkUri))
                controller.prepare()
                controller.play()
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "playback_failure_source_changed",
                    fields = playbackFailureFields(
                        item = item,
                        error = error,
                        startedAt = startedAt,
                        result = LogFields.Result.SUCCESS,
                        reason = "resolved_new_source",
                    ),
                )
            } catch (setMediaError: RuntimeException) {
                applied = false
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_failure_source_change_failed",
                    throwable = setMediaError,
                    fields = playbackFailureFields(
                        item = item,
                        error = error,
                        startedAt = startedAt,
                        reason = "set_media_item_failed",
                    ),
                )
            }
        }
        return applied
    }

    private suspend fun applyPlaybackFailurePolicy(
        error: PlaybackException,
        item: MusicItem,
        expectedIndex: Int,
    ) {
        if (!playQueue.isCurrentItem(expectedIndex, item)) return
        val autoStop = playbackRuntimeSettings.autoStopWhenError()
        if (autoStop) {
            pause()
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_failure_auto_paused",
                throwable = error,
                fields = playbackFailureFields(
                    item = item,
                    error = error,
                    reason = "auto_stop_enabled",
                ),
            )
            return
        }

        val nextIndex = nextIndexAfterPlaybackFailure()
        val next = nextIndex?.let { playQueue.items.getOrNull(it) }
        if (nextIndex == null || next == null) {
            pause()
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_failure_next_unavailable",
                throwable = error,
                fields = playbackFailureFields(
                    item = item,
                    error = error,
                    reason = "no_next_item",
                ),
            )
            return
        }

        MfLog.error(
            category = LogCategory.PLAYER,
            event = "playback_failure_skip_next",
            throwable = error,
            fields = playbackFailureFields(
                item = item,
                error = error,
                reason = "auto_stop_disabled",
            ),
        )
        startQueuePlaybackTransition(
            targetIndex = nextIndex,
            targetItem = next,
            operation = "playback_failure_skip_next",
        )
    }

    private fun nextIndexAfterPlaybackFailure(): Int? {
        if (playQueue.size <= 1) return null
        val currentIndex = playQueue.currentIndex
        return when {
            currentIndex < 0 -> return null
            currentIndex < playQueue.size - 1 -> currentIndex + 1
            repeatMode == RepeatMode.ALL -> 0
            else -> return null
        }
    }

    private fun playbackFailureFields(
        item: MusicItem,
        error: PlaybackException,
        startedAt: Long? = null,
        result: String = LogFields.Result.FAILURE,
        reason: String,
    ): Map<String, Any?> = buildMap {
        put("platform", item.platform)
        put("itemId", item.id)
        put("errorCode", error.errorCode)
        put("result", result)
        put("reason", reason)
        if (startedAt != null) put("durationMs", elapsedMs(startedAt))
    }

    private fun handleTrackEnded() {
        when (repeatMode) {
            RepeatMode.ONE -> {
                mediaController?.seekTo(0L)
                mediaController?.play()
            }
            RepeatMode.ALL -> skipToNext()
            RepeatMode.OFF -> {
                val nextIndex = playQueue.peekNextIndex(repeatMode)
                val next = nextIndex?.let { playQueue.items.getOrNull(it) }
                if (nextIndex != null && next != null) {
                    startQueuePlaybackTransition(
                        targetIndex = nextIndex,
                        targetItem = next,
                        operation = "track_ended",
                    )
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
            playbackSpeed = playbackSpeed,
        )
    }

    private fun emitQueueState() {
        _queueState.value = PlayQueueSnapshot(
            items = playQueue.items,
            currentIndex = playQueue.currentIndex,
        )
        _nextItemFlow.value = playQueue.peekNextItem(repeatMode)
    }

    /**
     * 用户在统计页触发"清除统计数据"前调用此方法：
     * 把当前 session（如已达 30s 阈值）落库，避免清除时丢失。
     * 之后再调 [com.hank.musicfree.data.repository.ListenStatsRepository.clearAll]
     * 删除全部 listen_event。
     */
    fun flushListenTrackerForClear() {
        listenTracker.flushCurrentSession()
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private fun MusicItem.diagnosticFields(prefix: String = ""): Map<String, Any?> {
        fun key(name: String): String =
            if (prefix.isBlank()) name else prefix + name.replaceFirstChar { it.titlecase() }

        return mapOf(
            key("rawKeyCount") to raw.size,
            key("hasQualities") to !qualities.isNullOrEmpty(),
            key("hasUrl") to !url.isNullOrBlank(),
            key("hasLocalPath") to !localPath.isNullOrBlank(),
        )
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 200L
        private const val HISTORY_MAX_SIZE = 200
        private const val PLAYBACK_RESOLVE_TIMEOUT_MS = 45_000L
    }
}

private fun Int?.toPlaybackState(): PlaybackState = when (this) {
    Player.STATE_IDLE -> PlaybackState.IDLE
    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
    Player.STATE_READY -> PlaybackState.READY
    Player.STATE_ENDED -> PlaybackState.ENDED
    else -> PlaybackState.IDLE
}
