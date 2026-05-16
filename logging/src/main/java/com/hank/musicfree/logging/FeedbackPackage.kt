package com.hank.musicfree.logging

import java.io.File

data class FeedbackPackage(
    val file: File,
    val fileName: String,
    val sizeBytes: Long,
)
