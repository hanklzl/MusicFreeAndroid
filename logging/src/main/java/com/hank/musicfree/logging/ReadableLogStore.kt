package com.hank.musicfree.logging

import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object ReadableLogStore {
    private const val MAX_READ_CHARS = 120_000

    @Volatile
    private var logFile: File? = null

    fun install(file: File) {
        file.parentFile?.mkdirs()
        logFile = file
    }

    fun appendError(event: String, line: String) {
        val file = logFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(
                buildString {
                    append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    append(' ')
                    append(event)
                    append('\n')
                    append(line)
                    append("\n\n")
                },
            )
        }
    }

    fun readErrorLog(maxChars: Int = MAX_READ_CHARS): String {
        val file = logFile ?: return ""
        return runCatching {
            if (!file.exists()) {
                ""
            } else {
                val text = file.readText()
                if (text.length <= maxChars) text else text.takeLast(maxChars)
            }
        }.getOrDefault("")
    }

    fun clear() {
        runCatching { logFile?.delete() }
    }
}
