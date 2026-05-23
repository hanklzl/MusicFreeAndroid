package com.hank.musicfree.updater.checker

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.api.UpdateClient
import com.hank.musicfree.updater.model.UpdateInfo
import com.hank.musicfree.updater.store.UpdatePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class UpdateChecker(
    private val client: UpdateClient,
    private val prefs: UpdatePreferences,
    private val abiResolver: AbiResolver,
    private val localCode: Long,
    private val localName: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val mutex = Mutex()

    fun checkOnLaunch(startupFields: Map<String, Any?> = emptyMap()) {
        check(
            respectSkip = true,
            startupFields = startupFields,
            source = UpdateCheckSource.Launch,
        )
    }

    fun checkManually() {
        check(
            respectSkip = false,
            startupFields = emptyMap(),
            source = UpdateCheckSource.Manual,
        )
    }

    private fun check(
        respectSkip: Boolean,
        startupFields: Map<String, Any?>,
        source: UpdateCheckSource,
    ) {
        scope.launch {
            val startedAtNano = System.nanoTime()
            val baseFields = startupFields + ("respectSkip" to respectSkip)
            mutex.withLock {
                MfLog.trace(
                    category = LogCategory.UPDATE,
                    event = "update_check_start",
                    fields = baseFields,
                )
                _state.value = UpdateState.Checking
                val info: UpdateInfo = runCatching { client.fetchLatest() }.getOrElse {
                    MfLog.error(
                        category = LogCategory.UPDATE,
                        event = "update_check_failed",
                        throwable = it,
                        fields = baseFields + ("cause" to UpdateError.Network.name),
                    )
                    _state.value = UpdateState.Failed(update = null, cause = UpdateError.Network)
                    logStartupFlowTerminal(
                        startupFields = startupFields,
                        startedAtNano = startedAtNano,
                        event = "startup_flow_failed",
                        result = LogFields.Result.FAILURE,
                        reason = UpdateError.Network.name,
                    )
                    return@withLock
                }
                    ?: run {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "update_check_failed",
                            fields = baseFields + ("cause" to UpdateError.Network.name),
                        )
                        _state.value = UpdateState.Failed(update = null, cause = UpdateError.Network)
                        logStartupFlowTerminal(
                            startupFields = startupFields,
                            startedAtNano = startedAtNano,
                            event = "startup_flow_failed",
                            result = LogFields.Result.FAILURE,
                            reason = UpdateError.Network.name,
                        )
                        return@withLock
                    }
                if (info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION || info.variants.isEmpty()) {
                    MfLog.error(
                        category = LogCategory.UPDATE,
                        event = "update_check_failed",
                        fields = baseFields + mapOf(
                            "cause" to UpdateError.SchemaUnsupported.name,
                            "remoteSchema" to info.schemaVersion,
                            "supportedSchema" to UpdateInfo.SUPPORTED_SCHEMA_VERSION,
                            "variantsCount" to info.variants.size,
                        ),
                    )
                    _state.value = UpdateState.Failed(update = null, cause = UpdateError.SchemaUnsupported)
                    logStartupFlowTerminal(
                        startupFields = startupFields,
                        startedAtNano = startedAtNano,
                        event = "startup_flow_failed",
                        result = LogFields.Result.FAILURE,
                        reason = UpdateError.SchemaUnsupported.name,
                        extraFields = mapOf(
                            "remoteSchema" to info.schemaVersion,
                            "supportedSchema" to UpdateInfo.SUPPORTED_SCHEMA_VERSION,
                            "variantsCount" to info.variants.size,
                        ),
                    )
                    return@withLock
                }
                val outcome = VersionCompare.compare(
                    localCode = localCode,
                    localName = localName,
                    remoteCode = info.versionCode,
                    remoteName = info.version,
                )
                when (outcome) {
                    VersionCompare.Outcome.UpToDate -> {
                        prefs.clearSkipVersion()
                        prefs.setLastCheckedAt(now())
                        _state.value = UpdateState.UpToDate(now())
                        MfLog.trace(
                            category = LogCategory.UPDATE,
                            event = "update_check_complete",
                            fields = baseFields + mapOf(
                                "outcome" to "up_to_date",
                                "versionCode" to localCode,
                            ),
                        )
                        logStartupFlowTerminal(
                            startupFields = startupFields,
                            startedAtNano = startedAtNano,
                            event = "startup_flow_complete",
                            result = LogFields.Result.SUCCESS,
                            reason = "up_to_date",
                            extraFields = mapOf("versionCode" to localCode),
                        )
                    }
                    VersionCompare.Outcome.Unsupported -> {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "update_check_failed",
                            fields = baseFields + ("cause" to UpdateError.SchemaUnsupported.name),
                        )
                        _state.value = UpdateState.Failed(update = null, cause = UpdateError.SchemaUnsupported)
                        logStartupFlowTerminal(
                            startupFields = startupFields,
                            startedAtNano = startedAtNano,
                            event = "startup_flow_failed",
                            result = LogFields.Result.FAILURE,
                            reason = UpdateError.SchemaUnsupported.name,
                        )
                    }
                    VersionCompare.Outcome.NewerAvailable -> {
                        val resolved = abiResolver.resolve(info)
                            ?: run {
                                MfLog.error(
                                    category = LogCategory.UPDATE,
                                    event = "update_check_failed",
                                    fields = baseFields + mapOf(
                                        "cause" to UpdateError.UnsupportedAbi.name,
                                        "variants" to info.variants.keys.joinToString(","),
                                    ),
                                )
                                _state.value = UpdateState.Failed(update = null, cause = UpdateError.UnsupportedAbi)
                                logStartupFlowTerminal(
                                    startupFields = startupFields,
                                    startedAtNano = startedAtNano,
                                    event = "startup_flow_failed",
                                    result = LogFields.Result.FAILURE,
                                    reason = UpdateError.UnsupportedAbi.name,
                                    extraFields = mapOf("variants" to info.variants.keys.joinToString(",")),
                                )
                                return@withLock
                            }
                        prefs.setLastCheckedAt(now())
                        prefs.setLastSeenVersion(info.version)
                        val skip = if (respectSkip) prefs.getSkipVersion() else null
                        val isSkipped = skip != null && skip == info.version
                        _state.value = UpdateState.Available(
                            update = resolved,
                            skipped = isSkipped,
                            source = source,
                        )
                        MfLog.trace(
                            category = LogCategory.UPDATE,
                            event = "update_check_complete",
                            fields = baseFields + mapOf(
                                "outcome" to "newer_available",
                                "versionCode" to info.versionCode,
                                "abi" to resolved.abi,
                                "result" to if (isSkipped) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
                            ),
                        )
                        logStartupFlowTerminal(
                            startupFields = startupFields,
                            startedAtNano = startedAtNano,
                            event = if (isSkipped) "startup_flow_skipped" else "startup_flow_complete",
                            result = if (isSkipped) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
                            reason = if (isSkipped) "version_skipped" else "newer_available",
                            extraFields = mapOf(
                                "versionCode" to info.versionCode,
                                "abi" to resolved.abi,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun logStartupFlowTerminal(
        startupFields: Map<String, Any?>,
        startedAtNano: Long,
        event: String,
        result: String,
        reason: String,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        if (startupFields.isEmpty()) return
        MfLog.detail(
            category = LogCategory.UPDATE,
            event = event,
            fields = startupFields + extraFields + mapOf(
                "durationMs" to ((System.nanoTime() - startedAtNano) / 1_000_000L).coerceAtLeast(0L),
                "result" to result,
                "reason" to reason,
            ),
        )
    }

    suspend fun markSkipped(update: ResolvedUpdate) {
        prefs.setSkipVersion(update.info.version)
        _state.value = UpdateState.Available(update = update, skipped = true)
    }

    fun transitionDownloading(update: ResolvedUpdate, progress: Float, bytes: Long, total: Long) {
        _state.value = UpdateState.Downloading(update, progress, bytes, total)
    }

    fun transitionReady(update: ResolvedUpdate, file: File) {
        _state.value = UpdateState.ReadyToInstall(update, file)
    }

    fun transitionFailed(update: ResolvedUpdate?, cause: UpdateError) {
        _state.value = UpdateState.Failed(update, cause)
    }

    fun transitionAvailable(
        update: ResolvedUpdate,
        skipped: Boolean,
        source: UpdateCheckSource = UpdateCheckSource.Manual,
    ) {
        _state.value = UpdateState.Available(update, skipped, source)
    }
}
