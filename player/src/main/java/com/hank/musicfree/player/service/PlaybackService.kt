package com.hank.musicfree.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hank.musicfree.core.R as CoreR
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.UiLogEvents
import com.hank.musicfree.player.R
import com.hank.musicfree.player.source.HeaderInjectingDataSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var headerInjectingFactory: HeaderInjectingDataSourceFactory
    @Inject lateinit var playbackRuntimeSettings: PlaybackRuntimeSettings
    @Inject lateinit var appPreferences: AppPreferences

    private var mediaSession: MediaSession? = null
    private var serviceCreatedAtMs: Long = 0L
    @Volatile
    private var showCloseButtonOnNotification: Boolean = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val playbackSessionCallback = object : MediaSession.Callback {
        @AndroidXOptIn(markerClass = [UnstableApi::class])
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_session_connect",
                fields = mapOf(
                    "status" to "start",
                    "controllerPackage" to controller.packageName,
                ),
            )
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(PlaybackNotificationActions.SkipToPreviousCommand)
                .add(PlaybackNotificationActions.SkipToNextCommand)
                .add(PlaybackNotificationActions.CloseCommand)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(
                    PlaybackNotificationActions.mediaButtonPreferences(showCloseButtonOnNotification),
                )
                .setSessionActivity(createSessionActivityPendingIntent())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_custom_command",
                fields = mapOf(
                    "status" to "start",
                    "command" to customCommand.customAction,
                    "controllerPackage" to controller.packageName,
                ),
            )
            when (customCommand.customAction) {
                PlaybackNotificationActions.ACTION_SKIP_TO_PREVIOUS -> {
                    PlaybackNotificationCommandHandler.skipToPrevious()
                }
                PlaybackNotificationActions.ACTION_SKIP_TO_NEXT -> {
                    PlaybackNotificationCommandHandler.skipToNext()
                }
                PlaybackNotificationActions.ACTION_CLOSE -> {
                    PlaybackNotificationCommandHandler.close()
                    session.player.stop()
                    session.player.clearMediaItems()
                    stopSelf()
                }
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            val sessionMediaItemCount = session.player.mediaItemCount
            if (playerCommand == Player.COMMAND_PLAY_PAUSE && sessionMediaItemCount == 0) {
                val diagnostics = PlaybackNotificationCommandHandler.diagnosticsSnapshot()
                val isAppController = controller.packageName == packageName
                val shouldRouteToNotification = shouldRouteEmptySessionPlayToNotification(
                    playerCommand = playerCommand,
                    sessionMediaItemCount = sessionMediaItemCount,
                    controllerPackage = controller.packageName,
                    servicePackage = packageName,
                )
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "playback_notification_player_command",
                    fields = mapOf(
                        "status" to if (shouldRouteToNotification) "start" else "skipped",
                        "command" to "play_pause",
                        "reason" to if (shouldRouteToNotification) {
                            "empty_session_player"
                        } else {
                            "app_controller_empty_session_player"
                        },
                        "controllerPackage" to controller.packageName,
                        "isAppController" to isAppController,
                        "sessionMediaItemCount" to sessionMediaItemCount,
                        "playerPlaybackState" to session.player.playbackState,
                        "queueIndex" to diagnostics.queueIndex,
                        "queueSize" to diagnostics.queueSize,
                        "currentItemId" to diagnostics.currentItemId.orEmpty(),
                    ),
                )
                if (shouldRouteToNotification) {
                    PlaybackNotificationCommandHandler.play()
                }
            }
            return SessionResult.RESULT_SUCCESS
        }
    }

    @AndroidXOptIn(markerClass = [UnstableApi::class])
    override fun onCreate() {
        super.onCreate()
        serviceCreatedAtMs = System.currentTimeMillis()
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "playback_service_created",
            fields = mapOf(
                "status" to "start",
            ),
        )
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = UiLogEvents.MEDIA_SESSION_STARTED,
            fields = emptyMap(),
        )

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(PLAYBACK_NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.playback_notification_channel_name)
                .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                .build()
                .apply {
                    setSmallIcon(CoreR.drawable.ic_motion_play)
                },
        )

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                musicAudioAttributes,
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(headerInjectingFactory)
            )
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createSessionActivityPendingIntent())
            .setMediaButtonPreferences(
                PlaybackNotificationActions.mediaButtonPreferences(showCloseButtonOnNotification),
            )
            .setCallback(playbackSessionCallback)
            .build()
        applyAudioFocusPolicy(player)
        applyNotificationCloseButtonPreference()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        flushLastPosition()
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val sessionPlayer = mediaSession?.player
        val queueSize = sessionPlayer?.mediaItemCount ?: 0
        val lastSongId = (sessionPlayer?.currentMediaItem?.mediaId).orEmpty()
        val lifetimeMs = if (serviceCreatedAtMs > 0L) {
            (System.currentTimeMillis() - serviceCreatedAtMs).coerceAtLeast(0L)
        } else 0L
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "playback_service_destroyed",
            fields = mapOf(
                "status" to "start",
            ),
        )
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = UiLogEvents.MEDIA_SESSION_DESTROYED,
            fields = mapOf(
                UiLogEvents.Fields.REASON to "service_on_destroy",
                UiLogEvents.Fields.LAST_SONG_ID to lastSongId,
                UiLogEvents.Fields.QUEUE_SIZE to queueSize,
                UiLogEvents.Fields.LIFETIME_MS to lifetimeMs,
            ),
        )
        flushLastPosition()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    @AndroidXOptIn(markerClass = [UnstableApi::class])
    private fun applyAudioFocusPolicy(player: ExoPlayer) {
        serviceScope.launch {
            val handleAudioFocus = runCatching {
                shouldHandleAudioFocus(playbackRuntimeSettings)
            }.onFailure { error ->
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_audio_focus_policy_failed",
                    throwable = error,
                    fields = mapOf(
                        "status" to "failed",
                        "reason" to "settings_read_failed",
                    ),
                )
            }.getOrDefault(true)

            player.setAudioAttributes(musicAudioAttributes, handleAudioFocus)
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_audio_focus_policy_applied",
                fields = mapOf(
                    "allowConcurrentPlayback" to !handleAudioFocus,
                    "handleAudioFocus" to handleAudioFocus,
                ),
            )
        }
    }

    @AndroidXOptIn(markerClass = [UnstableApi::class])
    private fun applyNotificationCloseButtonPreference() {
        serviceScope.launch {
            val enabled = runCatching {
                withContext(Dispatchers.IO) { playbackRuntimeSettings.showExitOnNotification() }
            }.onFailure { error ->
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_notification_close_preference_failed",
                    throwable = error,
                    fields = mapOf(
                        "status" to "failed",
                        "reason" to "settings_read_failed",
                    ),
                )
            }.getOrDefault(false)
            showCloseButtonOnNotification = enabled
            mediaSession?.setMediaButtonPreferences(
                PlaybackNotificationActions.mediaButtonPreferences(enabled),
            )
            MfLog.detail(
                category = LogCategory.PLAYER,
                event = "playback_notification_close_preference_applied",
                fields = mapOf(
                    "status" to "success",
                    "enabled" to enabled,
                ),
            )
        }
    }

    private fun flushLastPosition() {
        val player = mediaSession?.player ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val rawDuration = player.duration
        val durationMs = if (rawDuration == C.TIME_UNSET || rawDuration <= 0L) 0L else rawDuration
        if (positionMs <= 0L && durationMs <= 0L) return
        val result = runBlocking {
            withTimeoutOrNull(200L) {
                flushLastPositionTo(appPreferences, positionMs, durationMs)
                true
            }
        }
        if (result == null) {
            MfLog.error(
                category = LogCategory.PLAYER,
                event = "playback_flush_last_position_timeout",
                fields = mapOf(
                    "positionMs" to positionMs,
                    "durationMs" to durationMs,
                ),
            )
        }
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                setPackage(packageName)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal companion object {
        suspend fun shouldHandleAudioFocus(settings: PlaybackRuntimeSettings): Boolean =
            withContext(Dispatchers.IO) {
                !settings.allowConcurrentPlayback()
            }

        internal fun shouldRouteEmptySessionPlayToNotification(
            playerCommand: Int,
            sessionMediaItemCount: Int,
            controllerPackage: String,
            servicePackage: String,
        ): Boolean {
            return playerCommand == Player.COMMAND_PLAY_PAUSE &&
                sessionMediaItemCount == 0 &&
                controllerPackage != servicePackage
        }

        internal suspend fun flushLastPositionTo(
            prefs: AppPreferences,
            positionMs: Long,
            durationMs: Long,
        ) {
            if (positionMs <= 0L && durationMs <= 0L) return
            if (positionMs > 0L) prefs.setCurrentMusicPositionMs(positionMs)
            if (durationMs > 0L) prefs.setCurrentMusicDurationMs(durationMs)
        }

        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "playback"
        const val PLAYBACK_NOTIFICATION_ID = 1001
        const val SESSION_ACTIVITY_REQUEST_CODE = 1001
    }
}
