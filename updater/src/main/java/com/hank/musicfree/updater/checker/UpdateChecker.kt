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

    fun checkOnLaunch() {
        check(respectSkip = true)
    }

    fun checkManually() {
        check(respectSkip = false)
    }

    private fun check(respectSkip: Boolean) {
        scope.launch {
            mutex.withLock {
                MfLog.trace(
                    category = LogCategory.UPDATE,
                    event = "update_check_start",
                    fields = mapOf("respectSkip" to respectSkip),
                )
                _state.value = UpdateState.Checking
                val info: UpdateInfo = client.fetchLatest()
                    ?: run {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "update_check_failed",
                            fields = mapOf("cause" to UpdateError.Network.name),
                        )
                        _state.value = UpdateState.Failed(update = null, cause = UpdateError.Network)
                        return@withLock
                    }
                if (info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION || info.variants.isEmpty()) {
                    MfLog.error(
                        category = LogCategory.UPDATE,
                        event = "update_check_failed",
                        fields = mapOf(
                            "cause" to UpdateError.SchemaUnsupported.name,
                            "remoteSchema" to info.schemaVersion,
                            "supportedSchema" to UpdateInfo.SUPPORTED_SCHEMA_VERSION,
                            "variantsCount" to info.variants.size,
                        ),
                    )
                    _state.value = UpdateState.Failed(update = null, cause = UpdateError.SchemaUnsupported)
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
                            fields = mapOf(
                                "outcome" to "up_to_date",
                                "versionCode" to localCode,
                                "respectSkip" to respectSkip,
                            ),
                        )
                    }
                    VersionCompare.Outcome.Unsupported -> {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "update_check_failed",
                            fields = mapOf("cause" to UpdateError.SchemaUnsupported.name),
                        )
                        _state.value = UpdateState.Failed(update = null, cause = UpdateError.SchemaUnsupported)
                    }
                    VersionCompare.Outcome.NewerAvailable -> {
                        val resolved = abiResolver.resolve(info)
                            ?: run {
                                MfLog.error(
                                    category = LogCategory.UPDATE,
                                    event = "update_check_failed",
                                    fields = mapOf(
                                        "cause" to UpdateError.UnsupportedAbi.name,
                                        "variants" to info.variants.keys.joinToString(","),
                                    ),
                                )
                                _state.value = UpdateState.Failed(update = null, cause = UpdateError.UnsupportedAbi)
                                return@withLock
                            }
                        prefs.setLastCheckedAt(now())
                        prefs.setLastSeenVersion(info.version)
                        val skip = if (respectSkip) prefs.getSkipVersion() else null
                        val isSkipped = skip != null && skip == info.version
                        _state.value = UpdateState.Available(update = resolved, skipped = isSkipped)
                        MfLog.trace(
                            category = LogCategory.UPDATE,
                            event = "update_check_complete",
                            fields = mapOf(
                                "outcome" to "newer_available",
                                "versionCode" to info.versionCode,
                                "abi" to resolved.abi,
                                "respectSkip" to respectSkip,
                                "result" to if (isSkipped) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
                            ),
                        )
                    }
                }
            }
        }
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

    fun transitionAvailable(update: ResolvedUpdate, skipped: Boolean) {
        _state.value = UpdateState.Available(update, skipped)
    }
}
