package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.ListeningPreferenceRepository
import com.hank.musicfree.core.util.Clock
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject

data class RecommendationGenerationResult(
    val snapshot: DailyRecommendationSnapshot,
    val availablePluginCount: Int,
    val fromCache: Boolean,
)

class TodayRecommendationGenerator @Inject constructor(
    private val preferenceRepository: ListeningPreferenceRepository,
    private val queryPlanner: RecommendationQueryPlanner,
    private val candidateFetcher: PluginCandidateFetcher,
    private val ranker: PlaylistRanker,
    private val snapshotStore: TodayRecommendationSnapshotStore,
    private val clock: Clock,
) {
    suspend fun generate(date: LocalDate, forceRefresh: Boolean): RecommendationGenerationResult {
        val startedAt = System.nanoTime()
        val profile = preferenceRepository.buildProfile()
        MfLog.detail(
            category = LogCategory.HOME,
            event = "recommend_profile_built",
            fields = mapOf(
                "operation" to "recommend_profile_build",
                "result" to "success",
                "distinctSongCount" to profile.distinctSongCount,
                "confidence" to profile.confidence.name.lowercase(),
                "durationMs" to elapsedMs(startedAt),
            ),
        )
        val pluginContext = candidateFetcher.currentPluginContext()
        val sourceSignature = sha256("${profile.signature}|${pluginContext.signature}")
        if (!forceRefresh) {
            snapshotStore.readCurrent(date, sourceSignature)?.let { cached ->
                return RecommendationGenerationResult(
                    snapshot = cached,
                    availablePluginCount = pluginContext.availablePluginCount,
                    fromCache = true,
                )
            }
        }

        val queries = queryPlanner.plan(profile)
        val fetch = candidateFetcher.fetch(profile, queries)
        val ranked = ranker.rank(
            candidates = fetch.candidates,
            profile = profile,
            recentlyExposedKeys = snapshotStore.recentExposureKeys(),
        )
        MfLog.detail(
            category = LogCategory.HOME,
            event = "recommend_rank_finished",
            fields = mapOf(
                "operation" to "recommend_rank",
                "result" to "success",
                "candidateCount" to fetch.candidates.size,
                "count" to ranked.size,
            ),
        )
        val now = clock.now()
        val snapshot = DailyRecommendationSnapshot(
            date = date,
            profileSignature = profile.signature,
            confidence = profile.confidence,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            items = ranked,
        )
        snapshotStore.write(snapshot, sourceSignature)
        return RecommendationGenerationResult(
            snapshot = snapshot,
            availablePluginCount = fetch.availablePluginCount,
            fromCache = false,
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L
}
