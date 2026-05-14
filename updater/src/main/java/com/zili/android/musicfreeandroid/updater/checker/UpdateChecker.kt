package com.zili.android.musicfreeandroid.updater.checker

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.api.UpdateClient
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import com.zili.android.musicfreeandroid.updater.store.UpdatePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UpdateChecker(
    private val client: UpdateClient,
    private val prefs: UpdatePreferences,
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
                        _state.value = UpdateState.Failed(info = null, cause = UpdateError.Network)
                        return@withLock
                    }
                if (info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION) {
                    MfLog.error(
                        category = LogCategory.UPDATE,
                        event = "update_check_failed",
                        fields = mapOf(
                            "cause" to UpdateError.SchemaUnsupported.name,
                            "remoteSchema" to info.schemaVersion,
                            "supportedSchema" to UpdateInfo.SUPPORTED_SCHEMA_VERSION,
                        ),
                    )
                    _state.value = UpdateState.Failed(info = info, cause = UpdateError.SchemaUnsupported)
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
                        _state.value = UpdateState.Failed(info = info, cause = UpdateError.SchemaUnsupported)
                    }
                    VersionCompare.Outcome.NewerAvailable -> {
                        prefs.setLastCheckedAt(now())
                        prefs.setLastSeenVersion(info.version)
                        val skip = if (respectSkip) prefs.getSkipVersion() else null
                        val isSkipped = skip != null && skip == info.version
                        _state.value = UpdateState.Available(info = info, skipped = isSkipped)
                        MfLog.trace(
                            category = LogCategory.UPDATE,
                            event = "update_check_complete",
                            fields = mapOf(
                                "outcome" to "newer_available",
                                "versionCode" to info.versionCode,
                                "respectSkip" to respectSkip,
                                "result" to if (isSkipped) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
                            ),
                        )
                    }
                }
            }
        }
    }

    suspend fun markSkipped(info: UpdateInfo) {
        prefs.setSkipVersion(info.version)
        _state.value = UpdateState.Available(info = info, skipped = true)
    }

    fun transitionDownloading(info: UpdateInfo, progress: Float, bytes: Long, total: Long) {
        _state.value = UpdateState.Downloading(info, progress, bytes, total)
    }

    fun transitionReady(info: UpdateInfo, file: java.io.File) {
        _state.value = UpdateState.ReadyToInstall(info, file)
    }

    fun transitionFailed(info: UpdateInfo?, cause: UpdateError) {
        _state.value = UpdateState.Failed(info, cause)
    }

    fun transitionAvailable(info: UpdateInfo, skipped: Boolean) {
        _state.value = UpdateState.Available(info, skipped)
    }
}
