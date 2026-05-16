package com.hank.musicfree.logging

interface FeedbackLogExporterContract {
    suspend fun createPackage(): FeedbackPackage

    suspend fun clearLogs()

    suspend fun pruneLogs()
}
