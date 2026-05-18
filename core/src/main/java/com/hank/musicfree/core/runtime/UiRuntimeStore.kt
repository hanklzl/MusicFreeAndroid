package com.hank.musicfree.core.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Singleton
class UiRuntimeStore internal constructor(
    private val snapshotStore: SnapshotStore,
    private val json: Json = DefaultJson,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val debounceMs: Long = DefaultDebounceMs,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) : RuntimeStore<UiRuntimeState> {
    @Inject
    constructor(snapshotStore: SnapshotStore) : this(
        snapshotStore = snapshotStore,
        json = DefaultJson,
        dispatcher = Dispatchers.IO,
        clock = { System.currentTimeMillis() },
        debounceMs = DefaultDebounceMs,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    override val storeName: String = Namespace

    private val _state = MutableStateFlow(UiRuntimeState())
    override val state: StateFlow<UiRuntimeState> = _state.asStateFlow()

    private var persistJob: Job? = null

    override suspend fun restore(): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        return try {
            val snapshot = withContext(dispatcher) {
                snapshotStore.read(Namespace, Key)
            }
            if (snapshot == null) {
                _state.value = UiRuntimeState(restoreAttempted = true)
                logDetail(
                    event = "ui_runtime_restore_skipped",
                    state = _state.value,
                    result = LogFields.Result.SKIPPED,
                    reason = "empty_ui_snapshot",
                    durationMs = elapsedMs(startedAt),
                )
                return RuntimeRestoreResult.Skipped("empty_ui_snapshot")
            }

            val payload = json.decodeFromString<UiRuntimeSnapshot>(snapshot.payloadJson)
            val playerView = payload.playerView.toPlayerViewOrNull()
            val restoredState = UiRuntimeState(
                homeTab = payload.homeTab.normalized(),
                searchTab = payload.searchTab.normalized(),
                playerView = playerView ?: PlayerView.COVER,
                restoreAttempted = true,
                restored = true,
                lastFailureReason = null,
            )
            _state.value = restoredState

            if (playerView == null && !payload.playerView.isNullOrBlank()) {
                logDetail(
                    event = "ui_runtime_restore_stale",
                    state = restoredState,
                    result = LogFields.Result.STALE,
                    reason = "unknown_player_view",
                    durationMs = elapsedMs(startedAt),
                )
                RuntimeRestoreResult.Stale("unknown_player_view")
            } else {
                logDetail(
                    event = "ui_runtime_restore_success",
                    state = restoredState,
                    result = LogFields.Result.SUCCESS,
                    reason = null,
                    durationMs = elapsedMs(startedAt),
                )
                RuntimeRestoreResult.Restored
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SerializationException) {
            restoreFailed(startedAt, "deserialize_failed", error)
        } catch (error: Throwable) {
            restoreFailed(startedAt, "restore_failed", error)
        }
    }

    fun setHomeTab(tab: String?) {
        _state.value = _state.value.copy(
            homeTab = tab.normalized(),
            lastFailureReason = null,
        )
        schedulePersist()
    }

    fun setSearchTab(tab: String?) {
        _state.value = _state.value.copy(
            searchTab = tab.normalized(),
            lastFailureReason = null,
        )
        schedulePersist()
    }

    fun setPlayerView(view: PlayerView) {
        _state.value = _state.value.copy(
            playerView = view,
            lastFailureReason = null,
        )
        schedulePersist()
    }

    override suspend fun persist() {
        val startedAt = System.nanoTime()
        val current = _state.value
        try {
            val now = clock()
            val payload = UiRuntimeSnapshot(
                homeTab = current.homeTab,
                searchTab = current.searchTab,
                playerView = current.playerView.name,
            )
            withContext(dispatcher) {
                snapshotStore.write(
                    RuntimeSnapshot(
                        namespace = Namespace,
                        key = Key,
                        snapshotVersion = SnapshotVersion,
                        sourceSignature = SourceSignature,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        expiresAtEpochMs = null,
                        payloadJson = json.encodeToString(payload),
                    ),
                )
                snapshotStore.pruneNamespace(Namespace, KeepLatest)
            }
            logDetail(
                event = "ui_runtime_persist_success",
                state = current,
                result = LogFields.Result.SUCCESS,
                reason = null,
                durationMs = elapsedMs(startedAt),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val failedState = _state.value.copy(lastFailureReason = "persist_failed")
            _state.value = failedState
            MfLog.error(
                category = LogCategory.RUNTIME,
                event = "ui_runtime_persist_failed",
                throwable = error,
                fields = baseFields(
                    state = failedState,
                    result = LogFields.Result.FAILURE,
                    reason = "persist_failed",
                    durationMs = elapsedMs(startedAt),
                ),
            )
        }
    }

    override suspend fun prune(nowEpochMs: Long) {
        withContext(dispatcher) {
            snapshotStore.pruneNamespace(Namespace, KeepLatest)
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch(dispatcher) {
            if (debounceMs > 0) delay(debounceMs)
            persist()
        }
    }

    private fun restoreFailed(
        startedAt: Long,
        reason: String,
        error: Throwable,
    ): RuntimeRestoreResult {
        val failedState = _state.value.copy(
            restoreAttempted = true,
            restored = false,
            lastFailureReason = reason,
        )
        _state.value = failedState
        MfLog.error(
            category = LogCategory.RUNTIME,
            event = "ui_runtime_restore_failed",
            throwable = error,
            fields = baseFields(
                state = failedState,
                result = LogFields.Result.FAILURE,
                reason = reason,
                durationMs = elapsedMs(startedAt),
            ),
        )
        return RuntimeRestoreResult.Failed(reason, error)
    }

    private fun logDetail(
        event: String,
        state: UiRuntimeState,
        result: String,
        reason: String?,
        durationMs: Long,
    ) {
        MfLog.detail(
            category = LogCategory.RUNTIME,
            event = event,
            fields = baseFields(
                state = state,
                result = result,
                reason = reason,
                durationMs = durationMs,
            ),
        )
    }

    private fun baseFields(
        state: UiRuntimeState,
        result: String,
        reason: String?,
        durationMs: Long,
    ): Map<String, Any?> = buildMap {
        put("store", Namespace)
        put("key", Key)
        put("homeTab", state.homeTab.orEmpty())
        put("searchTab", state.searchTab.orEmpty())
        put("playerView", state.playerView.name)
        put("durationMs", durationMs)
        put("result", result)
        if (reason != null) put("reason", reason)
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    companion object {
        private const val Namespace = "ui_runtime"
        private val Key = RuntimeStoreKey.singleton(Namespace).value
        private const val SnapshotVersion = 1
        private const val SourceSignature = "ui_runtime:v1"
        private const val KeepLatest = 1
        private const val DefaultDebounceMs = 250L
        private val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        internal fun decodeSnapshotPayload(payloadJson: String): UiRuntimeSnapshot =
            DefaultJson.decodeFromString(payloadJson)
    }
}

@Composable
fun rememberUiRuntimeStore(): UiRuntimeStore {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            UiRuntimeStoreEntryPoint::class.java,
        ).uiRuntimeStore()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface UiRuntimeStoreEntryPoint {
    fun uiRuntimeStore(): UiRuntimeStore
}

private fun String?.normalized(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.toPlayerViewOrNull(): PlayerView? {
    val normalized = normalized() ?: return PlayerView.COVER
    return PlayerView.entries.firstOrNull { it.name == normalized }
}
