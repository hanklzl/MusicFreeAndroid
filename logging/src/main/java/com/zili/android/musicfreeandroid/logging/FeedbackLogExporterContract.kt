package com.zili.android.musicfreeandroid.logging

interface FeedbackLogExporterContract {
    suspend fun createPackage(): FeedbackPackage

    suspend fun clearLogs()

    suspend fun pruneLogs()
}
