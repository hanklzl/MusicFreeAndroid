package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.feature.home.runtime.intOrNull
import com.hank.musicfree.feature.home.runtime.rawMap
import com.hank.musicfree.feature.home.runtime.routeSeedPayload
import com.hank.musicfree.feature.home.runtime.stringOrNull
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Singleton
class TodayRecommendationSnapshotStore internal constructor(
    private val snapshotStore: SnapshotStore,
    private val nowEpochMs: () -> Long,
) {
    @Inject
    constructor(snapshotStore: SnapshotStore) : this(snapshotStore, System::currentTimeMillis)

    suspend fun readCurrent(
        date: LocalDate,
        expectedSourceSignature: String,
    ): DailyRecommendationSnapshot? {
        val runtime = snapshotStore.read(NAMESPACE, date.toString()) ?: return null
        if (runtime.snapshotVersion != SNAPSHOT_VERSION ||
            runtime.sourceSignature != expectedSourceSignature ||
            runtime.isExpired(nowEpochMs())
        ) {
            return null
        }
        return decodeSafely(runtime)
    }

    suspend fun readLatestFallback(beforeOrOnDate: LocalDate): DailyRecommendationSnapshot? {
        val candidates = snapshotStore.keys(NAMESPACE, MAX_SNAPSHOTS)
            .mapNotNull { key -> runCatching { LocalDate.parse(key) }.getOrNull()?.let { it to key } }
            .filter { (date) -> !date.isAfter(beforeOrOnDate) }
            .sortedByDescending { it.first }
        for ((_, key) in candidates) {
            val runtime = snapshotStore.read(NAMESPACE, key) ?: continue
            if (runtime.snapshotVersion != SNAPSHOT_VERSION || runtime.isExpired(nowEpochMs())) continue
            decodeSafely(runtime)?.let { return it }
        }
        return null
    }

    suspend fun recentExposureKeys(limitDays: Int = 7): Set<String> {
        val result = linkedSetOf<String>()
        snapshotStore.keys(NAMESPACE, limitDays.coerceAtLeast(0)).forEach { key ->
            val runtime = snapshotStore.read(NAMESPACE, key) ?: return@forEach
            if (runtime.snapshotVersion == SNAPSHOT_VERSION && !runtime.isExpired(nowEpochMs())) {
                decodeSafely(runtime)?.items?.mapTo(result, RecommendedSheet::key)
            }
        }
        return result
    }

    suspend fun write(snapshot: DailyRecommendationSnapshot, sourceSignature: String) {
        val startedAt = System.nanoTime()
        try {
            snapshotStore.write(
                RuntimeSnapshot(
                    namespace = NAMESPACE,
                    key = snapshot.date.toString(),
                    snapshotVersion = SNAPSHOT_VERSION,
                    sourceSignature = sourceSignature,
                    createdAtEpochMs = snapshot.createdAtEpochMs,
                    updatedAtEpochMs = snapshot.updatedAtEpochMs,
                    expiresAtEpochMs = nowEpochMs() + TTL_MS,
                    payloadJson = encode(snapshot),
                ),
            )
            snapshotStore.pruneNamespace(NAMESPACE, MAX_SNAPSHOTS)
            MfLog.detail(
                category = LogCategory.HOME,
                event = "recommend_snapshot_saved",
                fields = mapOf(
                    "operation" to "recommend_snapshot_save",
                    "result" to LogFields.Result.SUCCESS,
                    "date" to snapshot.date.toString(),
                    "count" to snapshot.items.size,
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.HOME,
                event = "recommend_snapshot_save_failed",
                throwable = error,
                fields = mapOf(
                    "operation" to "recommend_snapshot_save",
                    "result" to LogFields.Result.FAILURE,
                    "date" to snapshot.date.toString(),
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
            throw error
        }
    }

    private fun encode(snapshot: DailyRecommendationSnapshot): String = buildJsonObject {
        put("date", kotlinx.serialization.json.JsonPrimitive(snapshot.date.toString()))
        put("profileSignature", kotlinx.serialization.json.JsonPrimitive(snapshot.profileSignature))
        put("confidence", kotlinx.serialization.json.JsonPrimitive(snapshot.confidence.name))
        put("createdAtEpochMs", kotlinx.serialization.json.JsonPrimitive(snapshot.createdAtEpochMs))
        put("updatedAtEpochMs", kotlinx.serialization.json.JsonPrimitive(snapshot.updatedAtEpochMs))
        put(
            "items",
            buildJsonArray {
                snapshot.items.forEach { item ->
                    add(
                        buildJsonObject {
                            put("reason", kotlinx.serialization.json.JsonPrimitive(item.reason))
                            put("score", kotlinx.serialization.json.JsonPrimitive(item.score))
                            put(
                                "sheet",
                                Json.parseToJsonElement(
                                    routeSeedPayload(
                                        "id" to item.sheet.id,
                                        "platform" to item.sheet.platform,
                                        "title" to item.sheet.title,
                                        "artist" to item.sheet.artist,
                                        "description" to item.sheet.description,
                                        "coverImg" to item.sheet.coverImg,
                                        "artwork" to item.sheet.artwork,
                                        "worksNum" to item.sheet.worksNum,
                                        "raw" to item.sheet.raw,
                                    ),
                                ),
                            )
                        },
                    )
                }
            },
        )
    }.toString()

    private fun decodeSafely(runtime: RuntimeSnapshot): DailyRecommendationSnapshot? = try {
        val root = Json.parseToJsonElement(runtime.payloadJson).jsonObject
        val items = (root["items"] as? JsonArray).orEmpty().map { element ->
            val item = element.jsonObject
            val sheet = item.getValue("sheet").jsonObject
            RecommendedSheet(
                sheet = MusicSheetItemBase(
                    id = requireNotNull(sheet.stringOrNull("id")),
                    platform = requireNotNull(sheet.stringOrNull("platform")),
                    title = sheet.stringOrNull("title"),
                    artist = sheet.stringOrNull("artist"),
                    description = sheet.stringOrNull("description"),
                    coverImg = sheet.stringOrNull("coverImg"),
                    artwork = sheet.stringOrNull("artwork"),
                    worksNum = sheet.intOrNull("worksNum"),
                    raw = sheet.rawMap(),
                ),
                reason = requireNotNull(item.stringOrNull("reason")),
                score = item["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }
        DailyRecommendationSnapshot(
            date = LocalDate.parse(requireNotNull(root.stringOrNull("date"))),
            profileSignature = requireNotNull(root.stringOrNull("profileSignature")),
            confidence = ProfileConfidence.valueOf(requireNotNull(root.stringOrNull("confidence"))),
            createdAtEpochMs = root["createdAtEpochMs"]?.jsonPrimitive?.longOrNull
                ?: runtime.createdAtEpochMs,
            updatedAtEpochMs = root["updatedAtEpochMs"]?.jsonPrimitive?.longOrNull
                ?: runtime.updatedAtEpochMs,
            items = items,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        MfLog.error(
            category = LogCategory.HOME,
            event = "recommend_snapshot_restore_failed",
            throwable = error,
            fields = mapOf(
                "operation" to "recommend_snapshot_restore",
                "result" to LogFields.Result.FAILURE,
                "key" to runtime.key,
                "reason" to "invalid_payload",
            ),
        )
        null
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L

    companion object {
        const val NAMESPACE = "today_recommendation"
        private const val SNAPSHOT_VERSION = 1
        private const val MAX_SNAPSHOTS = 8
        private const val TTL_MS = 8L * 24L * 60L * 60L * 1_000L
    }
}
