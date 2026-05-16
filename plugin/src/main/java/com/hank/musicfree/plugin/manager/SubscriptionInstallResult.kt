package com.hank.musicfree.plugin.manager

data class SubscriptionInstallResult(
    val totalEntries: Int,
    val successfulInstalls: Int,
    val failedInstalls: Int,
    val errorMessage: String? = null,
)
