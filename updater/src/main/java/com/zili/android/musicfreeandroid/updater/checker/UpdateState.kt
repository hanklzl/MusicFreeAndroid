package com.zili.android.musicfreeandroid.updater.checker

import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import java.io.File

sealed interface UpdateState {

    data object Idle : UpdateState
    data object Checking : UpdateState

    data class UpToDate(val checkedAtEpochMillis: Long) : UpdateState

    data class Available(
        val info: UpdateInfo,
        val skipped: Boolean,
    ) : UpdateState

    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val bytes: Long,
        val total: Long,
    ) : UpdateState

    data class ReadyToInstall(
        val info: UpdateInfo,
        val apkFile: File,
    ) : UpdateState

    data class Failed(
        val info: UpdateInfo?,
        val cause: UpdateError,
    ) : UpdateState

    val hasUnreadAvailableUpdate: Boolean
        get() = this is Available && !skipped
}

enum class UpdateError {
    Network,
    SchemaUnsupported,
    SizeMismatch,
    Sha256Mismatch,
    Canceled,
    InstallBlocked,
}
