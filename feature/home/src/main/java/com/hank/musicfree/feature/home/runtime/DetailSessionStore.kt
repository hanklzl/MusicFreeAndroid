package com.hank.musicfree.feature.home.runtime

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.timedSuspend
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.ArtistItemBase
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.PluginManager
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object DetailRouteTypes {
    const val PLUGIN_SHEET = "plugin_sheet"
    const val TOP_LIST = "top_list"
    const val ALBUM = "album"
    const val ARTIST = "artist"
}

@Serializable
data class DetailSessionSnapshot(
    val routeType: String,
    val platform: String,
    val id: String,
    val headerJson: String,
    val rawJson: String?,
    val page: Int,
    val isEnd: Boolean,
    val itemCount: Int,
    val itemsJson: String,
)

data class DetailSessionStoreState(
    val sessions: Map<String, DetailSessionEntry> = emptyMap(),
)

data class DetailSessionEntry(
    val key: String,
    val routeType: String,
    val platform: String,
    val itemId: String,
    val fallbackTitle: String,
    val header: DetailSessionHeader,
    val items: List<MusicItem> = emptyList(),
    val page: Int = 0,
    val isEnd: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
    val needsRefresh: Boolean = false,
    val generation: Long = 0L,
    val restoreReason: String? = null,
) {
    val title: String
        get() = header.title ?: fallbackTitle
}

sealed interface DetailSessionHeader {
    val platform: String
    val id: String
    val title: String?
    val raw: Map<String, Any?>

    data class Sheet(val item: MusicSheetItemBase) : DetailSessionHeader {
        override val platform: String get() = item.platform
        override val id: String get() = item.id
        override val title: String? get() = item.title
        override val raw: Map<String, Any?> get() = item.raw
    }

    data class TopList(val item: MusicSheetItemBase) : DetailSessionHeader {
        override val platform: String get() = item.platform
        override val id: String get() = item.id
        override val title: String? get() = item.title
        override val raw: Map<String, Any?> get() = item.raw
    }

    data class Album(val item: AlbumItemBase) : DetailSessionHeader {
        override val platform: String get() = item.platform
        override val id: String get() = item.id
        override val title: String? get() = item.title
        override val raw: Map<String, Any?> get() = item.raw
    }

    data class Artist(val item: ArtistItemBase) : DetailSessionHeader {
        override val platform: String get() = item.platform
        override val id: String get() = item.id
        override val title: String? get() = item.name
        override val raw: Map<String, Any?> get() = item.raw
    }
}

data class DetailSessionRequest(
    val key: String,
    val routeType: String,
    val platform: String,
    val itemId: String,
    val seed: DetailSessionHeader,
    val fallbackTitle: String,
)

data class DetailLoadRequest(
    val key: String,
    val routeType: String,
    val platform: String,
    val itemId: String,
    val header: DetailSessionHeader,
    val page: Int,
)

data class DetailLoadResult(
    val header: DetailSessionHeader,
    val items: List<MusicItem>,
    val isEnd: Boolean,
)

fun interface DetailSessionClock {
    fun nowEpochMs(): Long
}

interface DetailSessionGateway {
    suspend fun load(request: DetailLoadRequest): DetailLoadResult?
}

fun interface DetailPluginSignatureProvider {
    fun currentSignature(platform: String): String
}

@Singleton
class DetailSessionStore internal constructor(
    private val snapshotStore: SnapshotStore,
    private val gateway: DetailSessionGateway,
    private val signatureProvider: DetailPluginSignatureProvider,
    private val json: Json,
    private val clock: DetailSessionClock,
) : RuntimeStore<DetailSessionStoreState> {

    @Inject
    constructor(
        snapshotStore: SnapshotStore,
        gateway: DetailSessionGateway,
        signatureProvider: DetailPluginSignatureProvider,
    ) : this(
        snapshotStore = snapshotStore,
        gateway = gateway,
        signatureProvider = signatureProvider,
        json = Json { ignoreUnknownKeys = true },
        clock = DetailSessionClock { System.currentTimeMillis() },
    )

    override val storeName: String = STORE_NAME
    override val restoreOnStartup: Boolean = false
    private val _state = MutableStateFlow(DetailSessionStoreState())
    override val state: StateFlow<DetailSessionStoreState> = _state.asStateFlow()
    private val pendingSourceSignatures = mutableMapOf<String, String>()

    override suspend fun restore(): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        var restored = 0
        return try {
            val now = clock.nowEpochMs()
            for (key in snapshotStore.keys(NAMESPACE, SNAPSHOT_CAPACITY)) {
                val snapshot = snapshotStore.read(NAMESPACE, key) ?: continue
                if (snapshot.isExpired(now)) {
                    snapshotStore.delete(NAMESPACE, key)
                    continue
                }
                restoreSnapshot(snapshot, startedAt)
                restored += 1
            }
            if (restored == 0) {
                logRestore(
                    event = "detail_session_restore_skipped",
                    key = KEY_BATCH,
                    routeType = "",
                    platform = "",
                    itemId = "",
                    page = null,
                    generation = 0,
                    count = 0,
                    result = LogFields.Result.SKIPPED,
                    reason = "empty_detail_session",
                    durationMs = elapsedMs(startedAt),
                )
                RuntimeRestoreResult.Skipped("empty_detail_session")
            } else {
                RuntimeRestoreResult.Restored
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logFailure(
                event = "detail_session_restore_failed",
                throwable = error,
                key = KEY_BATCH,
                routeType = "",
                platform = "",
                itemId = "",
                page = null,
                generation = 0,
                count = 0,
                reason = "restore_failed",
                durationMs = elapsedMs(startedAt),
            )
            RuntimeRestoreResult.Failed("restore_failed", error)
        }
    }

    suspend fun restore(key: String): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        return try {
            val snapshot = snapshotStore.read(NAMESPACE, key)
            if (snapshot == null || snapshot.isExpired(clock.nowEpochMs())) {
                if (snapshot != null) {
                    snapshotStore.delete(NAMESPACE, key)
                }
                logRestore(
                    event = "detail_session_restore_skipped",
                    key = key,
                    routeType = "",
                    platform = "",
                    itemId = "",
                    page = null,
                    generation = 0,
                    count = 0,
                    result = LogFields.Result.SKIPPED,
                    reason = "empty_detail_session",
                    durationMs = elapsedMs(startedAt),
                )
                return RuntimeRestoreResult.Skipped("empty_detail_session")
            }
            restoreSnapshot(snapshot, startedAt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logFailure(
                event = "detail_session_restore_failed",
                throwable = error,
                key = key,
                routeType = "",
                platform = "",
                itemId = "",
                page = null,
                generation = 0,
                count = 0,
                reason = "restore_failed",
                durationMs = elapsedMs(startedAt),
            )
            RuntimeRestoreResult.Failed("restore_failed", error)
        }
    }

    override suspend fun persist() {
        state.value.sessions.keys.forEach { persistKey(it) }
    }

    override suspend fun prune(nowEpochMs: Long) {
        snapshotStore.deleteExpired(NAMESPACE, nowEpochMs)
        snapshotStore.pruneNamespace(NAMESPACE, SNAPSHOT_CAPACITY)
    }

    suspend fun loadInitial(request: DetailSessionRequest, forceRefresh: Boolean = false) {
        if (!forceRefresh && state.value.sessions[request.key] == null) {
            restore(request.key)
        }
        validateRestoredSignature(request.key)
        val current = state.value.sessions[request.key]
        if (!forceRefresh && current != null && current.items.isNotEmpty() && !current.needsRefresh) {
            return
        }
        val generation = (current?.generation ?: 0L) + 1L
        val loadingEntry = (current ?: request.toEntry()).copy(
            loading = true,
            loadingMore = false,
            errorMessage = null,
            generation = generation,
        )
        putEntry(loadingEntry)
        loadPage(
            request = DetailLoadRequest(
                key = request.key,
                routeType = request.routeType,
                platform = request.platform,
                itemId = request.itemId,
                header = request.seed,
                page = 1,
            ),
            generation = generation,
            append = false,
        )
    }

    suspend fun loadMore(key: String) {
        val current = state.value.sessions[key] ?: return
        if (current.loading || current.loadingMore || current.isEnd || current.items.isEmpty()) return

        val generation = current.generation + 1L
        putEntry(current.copy(loadingMore = true, errorMessage = null, generation = generation))
        loadPage(
            request = DetailLoadRequest(
                key = key,
                routeType = current.routeType,
                platform = current.platform,
                itemId = current.itemId,
                header = current.header,
                page = current.page + 1,
            ),
            generation = generation,
            append = true,
        )
    }

    fun session(key: String): DetailSessionEntry? = state.value.sessions[key]

    private suspend fun loadPage(request: DetailLoadRequest, generation: Long, append: Boolean) {
        val startedAt = System.nanoTime()
        try {
            val (result, durationMs) = timedSuspend { gateway.load(request) }
            val current = state.value.sessions[request.key]
            if (current == null || current.generation != generation) {
                logStale(request, generation, elapsedMs(startedAt))
                return
            }
            if (result == null) {
                putEntry(
                    current.copy(
                        loading = false,
                        loadingMore = false,
                        errorMessage = "加载详情失败",
                    ),
                )
                logFailure(
                    event = "detail_session_load_failed",
                    throwable = null,
                    key = request.key,
                    routeType = request.routeType,
                    platform = request.platform,
                    itemId = request.itemId,
                    page = request.page,
                    generation = generation,
                    count = 0,
                    reason = LogFields.Reason.UNKNOWN,
                    durationMs = elapsedMs(startedAt),
                )
                return
            }

            val nextItems = if (append) current.items + result.items else result.items
            val next = current.copy(
                header = result.header,
                items = nextItems,
                page = request.page,
                isEnd = result.isEnd,
                loading = false,
                loadingMore = false,
                errorMessage = null,
                needsRefresh = false,
                restoreReason = null,
            )
            putEntry(next)
            logRestore(
                event = if (append) "detail_session_page_success" else "detail_session_load_success",
                key = request.key,
                routeType = request.routeType,
                platform = request.platform,
                itemId = request.itemId,
                page = request.page,
                generation = generation,
                count = result.items.size,
                result = LogFields.Result.SUCCESS,
                reason = null,
                durationMs = durationMs,
            )
            persistKey(request.key)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val current = state.value.sessions[request.key]
            if (current == null || current.generation != generation) {
                logStale(request, generation, elapsedMs(startedAt))
                return
            }
            putEntry(
                current.copy(
                    loading = false,
                    loadingMore = false,
                    errorMessage = error.message ?: "加载详情失败",
                ),
            )
            logFailure(
                event = "detail_session_load_failed",
                throwable = error,
                key = request.key,
                routeType = request.routeType,
                platform = request.platform,
                itemId = request.itemId,
                page = request.page,
                generation = generation,
                count = 0,
                reason = "load_failed",
                durationMs = elapsedMs(startedAt),
            )
        }
    }

    private suspend fun persistKey(key: String) {
        val startedAt = System.nanoTime()
        val entry = state.value.sessions[key] ?: return
        if (entry.items.isEmpty() || entry.needsRefresh) return
        try {
            val now = clock.nowEpochMs()
            val snapshot = entry.toSnapshot(json)
            snapshotStore.write(
                RuntimeSnapshot(
                    namespace = NAMESPACE,
                    key = key,
                    snapshotVersion = SNAPSHOT_VERSION,
                    sourceSignature = signatureProvider.currentSignature(entry.platform),
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    expiresAtEpochMs = now + SNAPSHOT_TTL_MS,
                    payloadJson = json.encodeToString(snapshot),
                ),
            )
            snapshotStore.pruneNamespace(NAMESPACE, SNAPSHOT_CAPACITY)
            logRestore(
                event = "detail_session_persist_success",
                key = key,
                routeType = entry.routeType,
                platform = entry.platform,
                itemId = entry.itemId,
                page = entry.page,
                generation = entry.generation,
                count = entry.items.size,
                result = LogFields.Result.SUCCESS,
                reason = null,
                durationMs = elapsedMs(startedAt),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logFailure(
                event = "detail_session_persist_failed",
                throwable = error,
                key = key,
                routeType = entry.routeType,
                platform = entry.platform,
                itemId = entry.itemId,
                page = entry.page,
                generation = entry.generation,
                count = 0,
                reason = "persist_failed",
                durationMs = elapsedMs(startedAt),
            )
        }
    }

    private fun restoreSnapshot(snapshot: RuntimeSnapshot, startedAt: Long): RuntimeRestoreResult {
        val payload = json.decodeFromString<DetailSessionSnapshot>(snapshot.payloadJson)
        val currentSignature = signatureProvider.currentSignature(payload.platform)
        if (currentSignature.isNotBlank() && snapshot.sourceSignature != currentSignature) {
            val staleEntry = payload.toEntry(
                key = snapshot.key,
                json = json,
                items = emptyList(),
                page = 0,
                isEnd = false,
                needsRefresh = true,
                restoreReason = "plugin_signature_changed",
            )
            pendingSourceSignatures.remove(snapshot.key)
            putEntry(staleEntry)
            logRestore(
                event = "detail_session_restore_stale",
                key = snapshot.key,
                routeType = payload.routeType,
                platform = payload.platform,
                itemId = payload.id,
                page = payload.page,
                generation = staleEntry.generation,
                count = 0,
                result = LogFields.Result.STALE,
                reason = "plugin_signature_changed",
                durationMs = elapsedMs(startedAt),
            )
            return RuntimeRestoreResult.Stale("plugin_signature_changed")
        }

        if (currentSignature.isBlank() && snapshot.sourceSignature.isNotBlank()) {
            pendingSourceSignatures[snapshot.key] = snapshot.sourceSignature
        } else {
            pendingSourceSignatures.remove(snapshot.key)
        }
        val pending = pendingSourceSignatures.containsKey(snapshot.key)
        val entry = payload.toEntry(
            key = snapshot.key,
            json = json,
            items = json.decodeMusicItems(payload.itemsJson),
            page = payload.page,
            isEnd = payload.isEnd,
            needsRefresh = false,
            restoreReason = if (pending) "plugin_signature_pending" else null,
        )
        putEntry(entry)
        logRestore(
            event = "detail_session_restore_success",
            key = snapshot.key,
            routeType = payload.routeType,
            platform = payload.platform,
            itemId = payload.id,
            page = payload.page,
            generation = entry.generation,
            count = entry.items.size,
            result = LogFields.Result.SUCCESS,
            reason = entry.restoreReason,
            durationMs = elapsedMs(startedAt),
        )
        return RuntimeRestoreResult.Restored
    }

    private fun validateRestoredSignature(key: String) {
        val restoredSignature = pendingSourceSignatures[key] ?: return
        val entry = state.value.sessions[key] ?: return
        val currentSignature = signatureProvider.currentSignature(entry.platform)
        if (currentSignature.isBlank()) return
        pendingSourceSignatures.remove(key)
        if (restoredSignature == currentSignature) {
            if (entry.restoreReason == "plugin_signature_pending") {
                putEntry(entry.copy(restoreReason = null))
            }
            return
        }
        val stale = entry.copy(
            items = emptyList(),
            page = 0,
            isEnd = false,
            loading = false,
            loadingMore = false,
            needsRefresh = true,
            restoreReason = "plugin_signature_changed",
        )
        putEntry(stale)
        logRestore(
            event = "detail_session_restore_stale",
            key = key,
            routeType = entry.routeType,
            platform = entry.platform,
            itemId = entry.itemId,
            page = entry.page,
            generation = entry.generation,
            count = 0,
            result = LogFields.Result.STALE,
            reason = "plugin_signature_changed",
            durationMs = 0,
        )
    }

    private fun putEntry(entry: DetailSessionEntry) {
        _state.update { current ->
            current.copy(sessions = current.sessions + (entry.key to entry))
        }
    }

    private fun logStale(request: DetailLoadRequest, generation: Long, durationMs: Long) {
        MfLog.detail(
            category = LogCategory.HOME,
            event = "detail_session_result_stale",
            fields = logFields(
                key = request.key,
                operation = "detail_session_result_drop",
                routeType = request.routeType,
                platform = request.platform,
                itemId = request.itemId,
                page = request.page,
                generation = generation,
                count = 0,
                durationMs = durationMs,
                result = LogFields.Result.STALE,
                reason = LogFields.Reason.STALE_GENERATION,
            ),
        )
    }

    private fun logRestore(
        event: String,
        key: String,
        routeType: String,
        platform: String,
        itemId: String,
        page: Int?,
        generation: Long,
        count: Int,
        result: String,
        reason: String?,
        durationMs: Long,
    ) {
        MfLog.detail(
            category = LogCategory.HOME,
            event = event,
            fields = logFields(
                key = key,
                operation = operationForEvent(event),
                routeType = routeType,
                platform = platform,
                itemId = itemId,
                page = page,
                generation = generation,
                count = count,
                durationMs = durationMs,
                result = result,
                reason = reason,
            ),
        )
    }

    private fun logFailure(
        event: String,
        throwable: Throwable?,
        key: String,
        routeType: String,
        platform: String,
        itemId: String,
        page: Int?,
        generation: Long,
        count: Int,
        reason: String,
        durationMs: Long,
    ) {
        MfLog.error(
            category = LogCategory.HOME,
            event = event,
            throwable = throwable,
            fields = logFields(
                key = key,
                operation = operationForEvent(event),
                routeType = routeType,
                platform = platform,
                itemId = itemId,
                page = page,
                generation = generation,
                count = count,
                durationMs = durationMs,
                result = LogFields.Result.FAILURE,
                reason = reason,
            ),
        )
    }

    private fun logFields(
        key: String,
        operation: String,
        routeType: String,
        platform: String,
        itemId: String,
        page: Int?,
        generation: Long,
        count: Int,
        durationMs: Long,
        result: String,
        reason: String?,
    ): Map<String, Any?> = buildMap {
        put("store", STORE_NAME)
        put("operation", operation)
        put("key", key)
        put("routeType", routeType)
        put("platform", platform)
        put("itemId", itemId)
        if (page != null) put("page", page)
        put("generation", generation)
        put("count", count)
        put("durationMs", durationMs)
        put("result", result)
        reason?.let { put("reason", it) }
    }

    private fun operationForEvent(event: String): String = when {
        event.contains("_restore_") -> OPERATION_RESTORE
        event.contains("_persist_") -> OPERATION_PERSIST
        event.contains("_page_") -> OPERATION_LOAD_MORE
        event.contains("_load_") -> OPERATION_LOAD_INITIAL
        else -> event
    }

    private fun DetailSessionRequest.toEntry(): DetailSessionEntry = DetailSessionEntry(
        key = key,
        routeType = routeType,
        platform = platform,
        itemId = itemId,
        fallbackTitle = fallbackTitle,
        header = seed,
    )

    private fun DetailSessionSnapshot.toEntry(
        key: String,
        json: Json,
        items: List<MusicItem>,
        page: Int,
        isEnd: Boolean,
        needsRefresh: Boolean,
        restoreReason: String?,
    ): DetailSessionEntry = DetailSessionEntry(
        key = key,
        routeType = routeType,
        platform = platform,
        itemId = id,
        fallbackTitle = fallbackTitle(routeType),
        header = decodeHeader(routeType, headerJson, json),
        items = items,
        page = page,
        isEnd = isEnd,
        needsRefresh = needsRefresh,
        restoreReason = restoreReason,
    )

    private fun DetailSessionEntry.toSnapshot(json: Json): DetailSessionSnapshot = DetailSessionSnapshot(
        routeType = routeType,
        platform = platform,
        id = itemId,
        headerJson = header.toSnapshotJson(json),
        rawJson = header.raw.toJsonElement().toString(),
        page = page,
        isEnd = isEnd,
        itemCount = items.size,
        itemsJson = json.encodeMusicItems(items),
    )

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    companion object {
        const val STORE_NAME = "detail_session"
        const val NAMESPACE = "detail_session"
        private const val KEY_BATCH = "detail_session:batch"
        private const val SNAPSHOT_VERSION = 1
        private const val SNAPSHOT_TTL_MS = 72 * 60 * 60 * 1000L
        private const val SNAPSHOT_CAPACITY = 20
        private const val OPERATION_RESTORE = "detail_session_restore"
        private const val OPERATION_PERSIST = "detail_session_persist"
        private const val OPERATION_LOAD_INITIAL = "detail_session_load_initial"
        private const val OPERATION_LOAD_MORE = "detail_session_load_more"
    }
}

@Singleton
class PluginManagerDetailSessionGateway @Inject constructor(
    private val pluginManager: PluginManager,
) : DetailSessionGateway,
    DetailPluginSignatureProvider {

    override suspend fun load(request: DetailLoadRequest): DetailLoadResult? {
        val plugin = pluginManager.getPlugin(request.platform) ?: error("plugin_missing")
        return when (request.routeType) {
            DetailRouteTypes.PLUGIN_SHEET -> {
                val header = (request.header as? DetailSessionHeader.Sheet)?.item
                    ?: error("invalid_plugin_sheet_header")
                plugin.getMusicSheetInfo(header, request.page)?.let {
                    DetailLoadResult(
                        header = DetailSessionHeader.Sheet(it.sheetItem ?: header),
                        items = it.musicList,
                        isEnd = it.isEnd,
                    )
                }
            }
            DetailRouteTypes.TOP_LIST -> {
                val header = (request.header as? DetailSessionHeader.TopList)?.item
                    ?: error("invalid_top_list_header")
                plugin.getTopListDetail(header, request.page)?.let {
                    DetailLoadResult(
                        header = DetailSessionHeader.TopList(it.topListItem ?: header),
                        items = it.musicList,
                        isEnd = it.isEnd,
                    )
                }
            }
            DetailRouteTypes.ALBUM -> {
                val header = (request.header as? DetailSessionHeader.Album)?.item
                    ?: error("invalid_album_header")
                plugin.getAlbumInfo(header, request.page)?.let {
                    DetailLoadResult(
                        header = DetailSessionHeader.Album(it.albumItem ?: header),
                        items = it.musicList,
                        isEnd = it.isEnd,
                    )
                }
            }
            DetailRouteTypes.ARTIST -> {
                val header = (request.header as? DetailSessionHeader.Artist)?.item
                    ?: error("invalid_artist_header")
                plugin.getArtistWorks(header, request.page, type = "music")?.let {
                    DetailLoadResult(
                        header = DetailSessionHeader.Artist(header),
                        items = it.musicList,
                        isEnd = it.isEnd,
                    )
                }
            }
            else -> error("unsupported_route_type:${request.routeType}")
        }
    }

    override fun currentSignature(platform: String): String =
        pluginManager.plugins.value
            .map { it.info }
            .firstOrNull { it.platform == platform }
            ?.signaturePart()
            .orEmpty()
}

private fun PluginInfo.signaturePart(): String =
    listOf(
        platform,
        version.orEmpty(),
        hash.orEmpty(),
        supportedSearchType.sorted().joinToString(","),
        supportedMethods.sorted().joinToString(","),
    ).joinToString(":")

private fun fallbackTitle(routeType: String): String = when (routeType) {
    DetailRouteTypes.PLUGIN_SHEET -> "歌单详情"
    DetailRouteTypes.TOP_LIST -> "榜单详情"
    DetailRouteTypes.ALBUM -> "专辑详情"
    DetailRouteTypes.ARTIST -> "歌手详情"
    else -> "详情"
}

internal fun DetailSessionHeader.toSnapshotJson(json: Json): String = when (this) {
    is DetailSessionHeader.Sheet -> item.toJsonObject().toString()
    is DetailSessionHeader.TopList -> item.toJsonObject().toString()
    is DetailSessionHeader.Album -> item.toJsonObject().toString()
    is DetailSessionHeader.Artist -> item.toJsonObject().toString()
}

private fun decodeHeader(routeType: String, headerJson: String, json: Json): DetailSessionHeader {
    val obj = json.parseToJsonElement(headerJson).jsonObject
    return when (routeType) {
        DetailRouteTypes.PLUGIN_SHEET -> DetailSessionHeader.Sheet(obj.toMusicSheetItemBase())
        DetailRouteTypes.TOP_LIST -> DetailSessionHeader.TopList(obj.toMusicSheetItemBase())
        DetailRouteTypes.ALBUM -> DetailSessionHeader.Album(obj.toAlbumItemBase())
        DetailRouteTypes.ARTIST -> DetailSessionHeader.Artist(obj.toArtistItemBase())
        else -> error("unsupported_route_type:$routeType")
    }
}

internal fun Json.encodeMusicItems(items: List<MusicItem>): String =
    buildJsonArray {
        items.forEach { add(it.toJsonObject()) }
    }.toString()

private fun Json.decodeMusicItems(payloadJson: String): List<MusicItem> =
    parseToJsonElement(payloadJson).jsonArray.mapNotNull { element ->
        (element as? JsonObject)?.toMusicItem()
    }

private fun MusicItem.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("title", title.json())
    put("artist", artist.json())
    put("album", album.json())
    put("duration", duration.json())
    put("url", url.json())
    put("artwork", artwork.json())
    put("raw", raw.toJsonElement())
    put("addedAt", addedAt.json())
    put("localPath", localPath.json())
}

private fun MusicSheetItemBase.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("title", title.json())
    put("artist", artist.json())
    put("description", description.json())
    put("coverImg", coverImg.json())
    put("artwork", artwork.json())
    put("worksNum", worksNum.json())
    put("raw", raw.toJsonElement())
}

private fun AlbumItemBase.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("title", title.json())
    put("date", date.json())
    put("artist", artist.json())
    put("description", description.json())
    put("artwork", artwork.json())
    put("worksNum", worksNum.json())
    put("raw", raw.toJsonElement())
}

private fun ArtistItemBase.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id.json())
    put("platform", platform.json())
    put("name", name.json())
    put("avatar", avatar.json())
    put("fans", fans.json())
    put("description", description.json())
    put("worksNum", worksNum.json())
    put("raw", raw.toJsonElement())
}

private fun JsonObject.toMusicItem(): MusicItem = MusicItem(
    id = detailRequiredString("id"),
    platform = detailRequiredString("platform"),
    title = detailRequiredString("title"),
    artist = detailRequiredString("artist"),
    album = detailStringOrNull("album"),
    duration = detailLongOrNull("duration") ?: 0L,
    url = detailStringOrNull("url"),
    artwork = detailStringOrNull("artwork"),
    qualities = null,
    raw = detailRawMap(),
    addedAt = detailLongOrNull("addedAt") ?: 0L,
    localPath = detailStringOrNull("localPath"),
)

private fun JsonObject.toMusicSheetItemBase(): MusicSheetItemBase = MusicSheetItemBase(
    id = detailRequiredString("id"),
    platform = detailRequiredString("platform"),
    title = detailStringOrNull("title"),
    artist = detailStringOrNull("artist"),
    description = detailStringOrNull("description"),
    coverImg = detailStringOrNull("coverImg"),
    artwork = detailStringOrNull("artwork"),
    worksNum = detailIntOrNull("worksNum"),
    raw = detailRawMap(),
)

private fun JsonObject.toAlbumItemBase(): AlbumItemBase = AlbumItemBase(
    id = detailRequiredString("id"),
    platform = detailRequiredString("platform"),
    title = detailStringOrNull("title"),
    date = detailStringOrNull("date"),
    artist = detailStringOrNull("artist"),
    description = detailStringOrNull("description"),
    artwork = detailStringOrNull("artwork"),
    worksNum = detailIntOrNull("worksNum"),
    raw = detailRawMap(),
)

private fun JsonObject.toArtistItemBase(): ArtistItemBase = ArtistItemBase(
    id = detailRequiredString("id"),
    platform = detailRequiredString("platform"),
    name = detailStringOrNull("name"),
    avatar = detailStringOrNull("avatar"),
    fans = detailIntOrNull("fans"),
    description = detailStringOrNull("description"),
    worksNum = detailIntOrNull("worksNum"),
    raw = detailRawMap(),
)

private fun JsonObject.detailStringOrNull(name: String): String? =
    this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.detailRequiredString(name: String): String =
    requireNotNull(detailStringOrNull(name)) { "Missing detail session field: $name" }

private fun JsonObject.detailIntOrNull(name: String): Int? =
    this[name]?.jsonPrimitive?.intOrNull

private fun JsonObject.detailLongOrNull(name: String): Long? =
    this[name]?.jsonPrimitive?.longOrNull

private fun JsonObject.detailRawMap(): Map<String, Any?> =
    (this["raw"] as? JsonObject)?.toAnyMap().orEmpty()

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

private fun String?.json(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Number?.json(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

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

@Suppress("unused")
private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(16)
}
