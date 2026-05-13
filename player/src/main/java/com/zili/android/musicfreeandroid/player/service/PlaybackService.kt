package com.zili.android.musicfreeandroid.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import com.zili.android.musicfreeandroid.core.R as CoreR
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.player.R
import com.zili.android.musicfreeandroid.player.source.HeaderInjectingDataSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var headerInjectingFactory: HeaderInjectingDataSourceFactory
    @Inject lateinit var playbackRuntimeSettings: PlaybackRuntimeSettings

    private var mediaSession: MediaSession? = null
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
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(PlaybackNotificationActions.mediaButtonPreferences())
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
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    @AndroidXOptIn(markerClass = [UnstableApi::class])
    override fun onCreate() {
        super.onCreate()
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "playback_service_created",
            fields = mapOf(
                "status" to "start",
            ),
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
            .setMediaButtonPreferences(PlaybackNotificationActions.mediaButtonPreferences())
            .setCallback(playbackSessionCallback)
            .build()
        applyAudioFocusPolicy(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "playback_service_destroyed",
            fields = mapOf(
                "status" to "start",
            ),
        )
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

        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "playback"
        const val PLAYBACK_NOTIFICATION_ID = 1001
        const val SESSION_ACTIVITY_REQUEST_CODE = 1001
    }
}
