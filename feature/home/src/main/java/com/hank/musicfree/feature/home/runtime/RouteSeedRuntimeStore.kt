package com.hank.musicfree.feature.home.runtime

import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class RouteSeedEntry(
    val key: String,
    val payload: JsonElement,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class RouteSeedRuntimeState(
    val keys: Set<String> = emptySet(),
    val restoring: Boolean = false,
    val lastFailureReason: String? = null,
)

fun interface RouteSeedClock {
    fun nowEpochMs(): Long
}

interface RouteSeedRuntimeAccess {
    fun put(key: String, payloadJson: String, ttlMs: Long): RouteSeedEntry
    fun resolve(key: String?): RouteSeedEntry?
    fun consume(key: String?): RouteSeedEntry? = resolve(key)
    fun clear()
}

@Singleton
class RouteSeedRuntimeStore internal constructor(
    private val snapshotStore: SnapshotStore,
    private val json: Json,
    private val clock: RouteSeedClock,
    private val persistScope: CoroutineScope,
) : RuntimeStore<RouteSeedRuntimeState>,
    RouteSeedRuntimeAccess {

    @Inject
    constructor(snapshotStore: SnapshotStore) : this(
        snapshotStore = snapshotStore,
        json = Json { ignoreUnknownKeys = true },
        clock = RouteSeedClock { System.currentTimeMillis() },
        persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    override val storeName: String = STORE_NAME
    private val entries = ConcurrentHashMap<String, RouteSeedEntry>()
    private val persistLocks = ConcurrentHashMap<String, Mutex>()
    private val _state = MutableStateFlow(RouteSeedRuntimeState())
    override val state: StateFlow<RouteSeedRuntimeState> = _state.asStateFlow()

    init {
        RouteSeedRuntimeStoreProvider.install(this)
    }

    override fun put(key: String, payloadJson: String, ttlMs: Long): RouteSeedEntry {
        val now = clock.nowEpochMs()
        val entry = RouteSeedEntry(
            key = key,
            payload = json.parseToJsonElement(payloadJson),
            updatedAtEpochMs = now,
            expiresAtEpochMs = now + ttlMs.coerceAtLeast(0L),
        )
        entries[key] = entry
        publishState(restoring = false, lastFailureReason = null)
        persistScope.launch {
            persistEntry(entry)
        }
        return entry
    }

    override fun resolve(key: String?): RouteSeedEntry? {
        if (key.isNullOrBlank()) return null
        val entry = entries[key] ?: return null
        if (entry.expiresAtEpochMs <= clock.nowEpochMs()) {
            entries.remove(key)
            publishState(restoring = false, lastFailureReason = null)
            return null
        }
        return entry
    }

    override suspend fun restore(): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        _state.value = _state.value.copy(restoring = true, lastFailureReason = null)
        return try {
            val now = clock.nowEpochMs()
            val keys = snapshotStore.keys(NAMESPACE, RESTORE_LIMIT)
            var restored = 0
            var skipped = 0
            for (key in keys) {
                val snapshot = snapshotStore.read(NAMESPACE, key) ?: continue
                if (snapshot.isExpired(now)) {
                    skipped += 1
                    snapshotStore.delete(NAMESPACE, key)
                    continue
                }
                runCatching {
                    entries[key] = RouteSeedEntry(
                        key = key,
                        payload = json.parseToJsonElement(snapshot.payloadJson),
                        updatedAtEpochMs = snapshot.updatedAtEpochMs,
                        expiresAtEpochMs = snapshot.expiresAtEpochMs ?: (now + DEFAULT_TTL_MS),
                    )
                }.onSuccess {
                    restored += 1
                }.onFailure { error ->
                    skipped += 1
                    snapshotStore.delete(NAMESPACE, key)
                    logRestoreFailed(
                        key = key,
                        reason = "invalid_payload",
                        durationMs = elapsedMs(startedAt),
                        error = error,
                    )
                }
            }
            publishState(restoring = false, lastFailureReason = null)
            if (restored == 0) {
                MfLog.detail(
                    category = LogCategory.RUNTIME,
                    event = "route_seed_restore_skipped",
                    fields = restoreFields(
                        key = null,
                        result = LogFields.Result.SKIPPED,
                        count = 0,
                        durationMs = elapsedMs(startedAt),
                        reason = "empty_route_seeds",
                        skippedCount = skipped,
                    ),
                )
                RuntimeRestoreResult.Skipped("empty_route_seeds")
            } else {
                MfLog.detail(
                    category = LogCategory.RUNTIME,
                    event = "route_seed_restore_success",
                    fields = restoreFields(
                        key = null,
                        result = LogFields.Result.SUCCESS,
                        count = restored,
                        durationMs = elapsedMs(startedAt),
                        reason = null,
                        skippedCount = skipped,
                    ),
                )
                RuntimeRestoreResult.Restored
            }
        } catch (error: CancellationException) {
            publishState(restoring = false, lastFailureReason = null)
            throw error
        } catch (error: Throwable) {
            val reason = "restore_failed"
            publishState(restoring = false, lastFailureReason = reason)
            logRestoreFailed(
                key = null,
                reason = reason,
                durationMs = elapsedMs(startedAt),
                error = error,
            )
            RuntimeRestoreResult.Failed(reason, error)
        }
    }

    override suspend fun persist() {
        entries.values.forEach { persistEntry(it) }
        snapshotStore.pruneNamespace(NAMESPACE, MAX_SNAPSHOTS)
    }

    override suspend fun prune(nowEpochMs: Long) {
        val expiredKeys = entries.values
            .filter { it.expiresAtEpochMs <= nowEpochMs }
            .map { it.key }
        expiredKeys.forEach { entries.remove(it) }
        snapshotStore.deleteExpired(NAMESPACE, nowEpochMs)
        publishState(restoring = false, lastFailureReason = null)
    }

    override fun clear() {
        val keys = entries.keys.toList()
        entries.clear()
        publishState(restoring = false, lastFailureReason = null)
        persistScope.launch {
            keys.forEach { snapshotStore.delete(NAMESPACE, it) }
        }
    }

    private suspend fun persistEntry(entry: RouteSeedEntry) {
        val startedAt = System.nanoTime()
        try {
            val lock = persistLocks.getOrPut(entry.key) { Mutex() }
            lock.withLock {
                if (entries[entry.key] != entry) {
                    return
                }
                snapshotStore.write(
                    RuntimeSnapshot(
                        namespace = NAMESPACE,
                        key = entry.key,
                        snapshotVersion = SNAPSHOT_VERSION,
                        sourceSignature = SOURCE_SIGNATURE,
                        createdAtEpochMs = entry.updatedAtEpochMs,
                        updatedAtEpochMs = entry.updatedAtEpochMs,
                        expiresAtEpochMs = entry.expiresAtEpochMs,
                        payloadJson = entry.payload.toString(),
                    ),
                )
                snapshotStore.pruneNamespace(NAMESPACE, MAX_SNAPSHOTS)
                MfLog.detail(
                    category = LogCategory.RUNTIME,
                    event = "route_seed_persist_success",
                    fields = baseFields(
                        operation = OPERATION_PERSIST,
                        key = entry.key,
                        result = LogFields.Result.SUCCESS,
                        count = 1,
                        durationMs = elapsedMs(startedAt),
                        reason = null,
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.RUNTIME,
                event = "route_seed_persist_failed",
                throwable = error,
                fields = baseFields(
                    operation = OPERATION_PERSIST,
                    key = entry.key,
                    result = LogFields.Result.FAILURE,
                    count = 0,
                    durationMs = elapsedMs(startedAt),
                    reason = "persist_failed",
                ),
            )
        }
    }

    private fun logRestoreFailed(key: String?, reason: String, durationMs: Long, error: Throwable) {
        MfLog.error(
            category = LogCategory.RUNTIME,
            event = "route_seed_restore_failed",
            throwable = error,
            fields = restoreFields(
                key = key,
                result = LogFields.Result.FAILURE,
                count = 0,
                durationMs = durationMs,
                reason = reason,
                skippedCount = null,
            ),
        )
    }

    private fun restoreFields(
        key: String?,
        result: String,
        count: Int,
        durationMs: Long,
        reason: String?,
        skippedCount: Int?,
    ): Map<String, Any?> = buildMap {
        putAll(
            baseFields(
                operation = OPERATION_RESTORE,
                key = key,
                result = result,
                count = count,
                durationMs = durationMs,
                reason = reason,
            ),
        )
        if (skippedCount != null) {
            put("skippedCount", skippedCount)
        }
    }

    private fun baseFields(
        operation: String,
        key: String?,
        result: String,
        count: Int,
        durationMs: Long,
        reason: String?,
    ): Map<String, Any?> = buildMap {
        put("store", STORE_NAME)
        put("operation", operation)
        put("key", key ?: KEY_BATCH)
        put("result", result)
        put("count", count)
        put("durationMs", durationMs)
        reason?.let { put("reason", it) }
    }

    private fun publishState(restoring: Boolean, lastFailureReason: String?) {
        _state.value = RouteSeedRuntimeState(
            keys = entries.keys.toSet(),
            restoring = restoring,
            lastFailureReason = lastFailureReason,
        )
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    companion object {
        const val STORE_NAME = "route_seed"
        const val NAMESPACE = "route_seed"
        const val DEFAULT_TTL_MS = 60 * 60 * 1000L
        private const val SNAPSHOT_VERSION = 1
        private const val SOURCE_SIGNATURE = "route_seed:v1"
        private const val RESTORE_LIMIT = 200
        private const val MAX_SNAPSHOTS = 200
        private const val KEY_BATCH = "route_seed:batch"
        private const val OPERATION_RESTORE = "route_seed_restore"
        private const val OPERATION_PERSIST = "route_seed_persist"
    }
}

object RouteSeedRuntimeStoreProvider {
    @Volatile
    var current: RouteSeedRuntimeAccess = InMemoryRouteSeedRuntimeAccess()
        private set

    fun install(access: RouteSeedRuntimeAccess) {
        current = access
    }

    internal fun resetForTest() {
        current = InMemoryRouteSeedRuntimeAccess()
    }
}

private class InMemoryRouteSeedRuntimeAccess : RouteSeedRuntimeAccess {
    private val json = Json { ignoreUnknownKeys = true }
    private val entries = ConcurrentHashMap<String, RouteSeedEntry>()

    override fun put(key: String, payloadJson: String, ttlMs: Long): RouteSeedEntry {
        val now = System.currentTimeMillis()
        val entry = RouteSeedEntry(
            key = key,
            payload = json.parseToJsonElement(payloadJson),
            updatedAtEpochMs = now,
            expiresAtEpochMs = now + ttlMs.coerceAtLeast(0L),
        )
        entries[key] = entry
        return entry
    }

    override fun resolve(key: String?): RouteSeedEntry? {
        if (key.isNullOrBlank()) return null
        val entry = entries[key] ?: return null
        if (entry.expiresAtEpochMs <= System.currentTimeMillis()) {
            entries.remove(key)
            return null
        }
        return entry
    }

    override fun clear() {
        entries.clear()
    }
}

internal fun routeSeedPayload(vararg fields: Pair<String, Any?>): String =
    buildJsonObject {
        fields.forEach { (name, value) ->
            put(name, value.toJsonElement())
        }
    }.toString()

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonObject.stringOrNull(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.requiredString(name: String): String =
    requireNotNull(stringOrNull(name)) { "Missing route seed field: $name" }

internal fun JsonObject.intOrNull(name: String): Int? =
    this[name]?.jsonPrimitive?.intOrNull

internal fun JsonObject.longOrNull(name: String): Long? =
    this[name]?.jsonPrimitive?.longOrNull

internal fun JsonObject.rawMap(): Map<String, Any?> =
    (this["raw"] as? JsonObject)?.toAnyMap().orEmpty()

internal fun JsonObject.qualitiesMapOrNull(): Map<PlayQuality, QualityInfo>? {
    val source = this["qualities"] as? JsonObject ?: return null
    return source.mapNotNull { (qualityName, value) ->
        val quality = runCatching { PlayQuality.valueOf(qualityName) }.getOrNull() ?: return@mapNotNull null
        val obj = value as? JsonObject ?: return@mapNotNull null
        quality to QualityInfo(
            url = obj.stringOrNull("url"),
            size = obj.longOrNull("size"),
        )
    }.toMap().takeIf { it.isNotEmpty() }
}

internal fun qualitiesToJson(qualities: Map<PlayQuality, QualityInfo>?): JsonElement =
    qualities?.let { source ->
        buildJsonObject {
            source.forEach { (quality, info) ->
                put(
                    quality.name,
                    buildJsonObject {
                        put("url", info.url.toJsonElement())
                        put("size", info.size.toJsonElement())
                    },
                )
            }
        }
    } ?: JsonNull

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject {
        this@toJsonElement.forEach { (key, value) ->
            put(key.toString(), value.toJsonElement())
        }
    }
    is Iterable<*> -> buildJsonArray {
        this@toJsonElement.forEach { add(it.toJsonElement()) }
    }
    is Array<*> -> buildJsonArray {
        this@toJsonElement.forEach { add(it.toJsonElement()) }
    }
    else -> JsonPrimitive(toString())
}

private fun JsonObject.toAnyMap(): Map<String, Any?> =
    entries.associate { (key, value) -> key to value.toAnyValue() }

private fun JsonArray.toAnyList(): List<Any?> =
    map { it.toAnyValue() }

private fun JsonElement.toAnyValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> toAnyMap()
    is JsonArray -> toAnyList()
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> contentOrNull
    }
}
