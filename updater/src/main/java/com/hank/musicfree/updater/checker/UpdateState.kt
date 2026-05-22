package com.hank.musicfree.updater.checker

import java.io.File

sealed interface UpdateState {

    data object Idle : UpdateState
    data object Checking : UpdateState

    data class UpToDate(val checkedAtEpochMillis: Long) : UpdateState

    data class Available(
        val update: ResolvedUpdate,
        val skipped: Boolean,
    ) : UpdateState

    data class Downloading(
        val update: ResolvedUpdate,
        val progress: Float,
        val bytes: Long,
        val total: Long,
    ) : UpdateState

    data class ReadyToInstall(
        val update: ResolvedUpdate,
        val apkFile: File,
    ) : UpdateState

    data class Failed(
        val update: ResolvedUpdate?,
        val cause: UpdateError,
    ) : UpdateState

    val hasUnreadAvailableUpdate: Boolean
        get() = this is Available && !skipped
}

enum class UpdateError {
    Network,
    SchemaUnsupported,
    UnsupportedAbi,
    SizeMismatch,
    Sha256Mismatch,
    Canceled,
    InstallBlocked,     // 未知来源未授权（pre-flight 闸门失败）
    InstallFailed,      // 系统/Session 报告的运行时安装失败（含坏包 / 包冲突 / IO / 系统拒绝）
}
