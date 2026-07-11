package com.hank.musicfree.data.repository.recommendation

import com.hank.musicfree.core.util.Clock
import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.data.repository.listenstats.model.RecommendationListenEvent
import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningPreferenceRepository @Inject constructor(
    private val dao: ListenStatsDao,
    private val builder: PreferenceProfileBuilder,
    private val clock: Clock,
) {
    suspend fun buildProfile(
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        maxEvents: Int = DEFAULT_MAX_EVENTS,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PreferenceProfile {
        val now = clock.now()
        val start = Instant.ofEpochMilli(now)
            .atZone(zoneId)
            .toLocalDate()
            .minusDays(windowDays.coerceAtLeast(1).toLong() - 1L)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val events = dao.preferenceEvents(
            startMs = start,
            endMs = now + 1L,
            limit = maxEvents.coerceIn(1, MAX_EVENTS_LIMIT),
        ).map { event ->
            RecommendationListenEvent(
                playedAtMs = event.playedAtMs,
                musicId = event.musicId,
                platform = event.platform,
                title = event.title,
                artistRaw = event.artistRaw,
                playedSeconds = event.playedSeconds,
                completed = event.completed,
                language = event.language,
                genre = event.genre,
            )
        }
        return builder.build(events, now)
    }

    private companion object {
        const val DEFAULT_WINDOW_DAYS = 30
        const val DEFAULT_MAX_EVENTS = 5_000
        const val MAX_EVENTS_LIMIT = 10_000
    }
}
