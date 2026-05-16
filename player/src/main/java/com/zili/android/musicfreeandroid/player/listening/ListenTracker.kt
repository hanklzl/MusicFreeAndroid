package com.zili.android.musicfreeandroid.player.listening

import androidx.media3.common.Player
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.util.splitArtists
import com.zili.android.musicfreeandroid.data.db.dao.ListenStatsDao
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventArtistEntity
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventEntity
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ListenTracker(
    private val dao: ListenStatsDao,
    internal val nowMs: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    companion object {
        const val THRESHOLD_MS: Long = 30_000L
        const val COMPLETED_TAIL_TOLERANCE_MS: Long = 5_000L
    }

    private data class Session(
        val item: MusicItem,
        var resumeWall: Long?,
        var accumulatedMs: Long,
        var lastEventWall: Long,
        var endedNaturally: Boolean = false,
    )

    private var current: Session? = null

    fun onMediaItemTransition(newItem: MusicItem?, @Suppress("UNUSED_PARAMETER") reason: Int) {
        flushIfQualifies(reason = "transition")
        current = newItem?.let {
            Session(it, resumeWall = null, accumulatedMs = 0, lastEventWall = nowMs())
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean, fallbackItem: MusicItem?) {
        val s = current ?: fallbackItem?.let {
            Session(it, resumeWall = null, accumulatedMs = 0, lastEventWall = nowMs())
                .also { sess -> current = sess }
        } ?: return
        val now = nowMs()
        if (isPlaying) {
            if (s.resumeWall == null) s.resumeWall = now
        } else {
            s.resumeWall?.let { s.accumulatedMs += (now - it) }
            s.resumeWall = null
        }
        s.lastEventWall = now
    }

    fun onPositionDiscontinuity(reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        val s = current ?: return
        val now = nowMs()
        val wasPlaying = s.resumeWall != null
        s.resumeWall?.let { s.accumulatedMs += (now - it) }
        s.resumeWall = if (wasPlaying) now else null
        s.lastEventWall = now
    }

    fun onTrackEnded(@Suppress("UNUSED_PARAMETER") item: MusicItem?) {
        val s = current ?: return
        val now = nowMs()
        s.resumeWall?.let { s.accumulatedMs += (now - it); s.resumeWall = null }
        s.endedNaturally = true
        s.lastEventWall = now
        flushIfQualifies(reason = "ended")
        current = null
    }

    /** 用户清除前调它把当前 session 落库（如阈值满足）。 */
    fun flushCurrentSession() {
        flushIfQualifies(reason = "external_flush")
    }

    private fun flushIfQualifies(reason: String) {
        val s = current ?: return
        val now = nowMs()
        s.resumeWall?.let { s.accumulatedMs += (now - it); s.resumeWall = null }
        s.lastEventWall = now

        if (s.accumulatedMs < THRESHOLD_MS) {
            MfLog.detail(
                LogCategory.PLAYER, "listen_event_skipped_below_threshold",
                mapOf(
                    "accumulatedMs" to s.accumulatedMs,
                    "durationMs" to s.item.duration,
                    "reason" to reason,
                ),
            )
            return
        }

        val (lang, genre) = ListenDimExtractor.extract(s.item.raw)
        val artists = splitArtists(s.item.artist)
        val primary = artists.firstOrNull().orEmpty()
        val mergeKey = "${s.item.title.trim().lowercase()}|${primary.trim().lowercase()}"
        val durationMs = s.item.duration
        val completedBoundary = durationMs - COMPLETED_TAIL_TOLERANCE_MS
        val completed = s.endedNaturally || (durationMs > 0 && s.accumulatedMs >= completedBoundary)
        val event = ListenEventEntity(
            playedAtMs = s.lastEventWall,
            musicId = s.item.id, platform = s.item.platform,
            title = s.item.title, artistRaw = s.item.artist,
            album = s.item.album, artwork = s.item.artwork,
            durationMs = durationMs,
            playedSeconds = (s.accumulatedMs / 1000).toInt(),
            completed = completed,
            language = lang, genre = genre,
            mergeKey = mergeKey,
        )
        val artistRows = artists.mapIndexed { i, name ->
            ListenEventArtistEntity(eventId = 0, artistName = name, artistOrder = i)
        }
        scope.launch {
            runCatching { dao.insertEventWithArtists(event, artistRows) }
                .onSuccess {
                    MfLog.detail(
                        LogCategory.PLAYER, "listen_event_inserted",
                        mapOf(
                            "musicId" to event.musicId,
                            "platform" to event.platform,
                            "playedSeconds" to event.playedSeconds,
                            "completed" to event.completed,
                            "durationMs" to event.durationMs,
                        ),
                    )
                }
                .onFailure { MfLog.error(LogCategory.PLAYER, "listen_event_insert_failed", it) }
        }
    }
}
