package com.zili.android.musicfreeandroid.logging

import java.io.File

data class FeedbackPackage(
    val file: File,
    val fileName: String,
    val sizeBytes: Long,
)
