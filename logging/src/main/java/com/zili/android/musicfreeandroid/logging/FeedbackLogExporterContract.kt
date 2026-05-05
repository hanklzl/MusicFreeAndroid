package com.zili.android.musicfreeandroid.logging

interface FeedbackLogExporterContract {
    fun createPackage(): FeedbackPackage

    fun clearLogs()

    fun pruneLogs()
}
