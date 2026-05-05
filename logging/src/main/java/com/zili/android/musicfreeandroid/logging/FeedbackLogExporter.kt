package com.zili.android.musicfreeandroid.logging

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FeedbackLogExporter(
    private val config: LoggingConfig,
    private val sessionId: String,
) {
    fun createPackage(): FeedbackPackage {
        MfLog.flush()
        pruneLogs()

        config.feedbackDir.mkdirs()
        val stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = File(config.feedbackDir, "musicfree-feedback-$stamp.zip")
        val logFiles = config.logDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putText("manifest.json", buildManifest(logFiles))
            zip.putText("README-decode.md", decodeReadme())
            logFiles.forEach { file ->
                zip.putNextEntry(ZipEntry("logan/${file.name}"))
                file.inputStream().buffered().use { fileInput ->
                    fileInput.copyTo(zip)
                }
                zip.closeEntry()
            }
        }

        MfLog.trace(
            LogCategory.FEEDBACK,
            "feedback_package_created",
            mapOf("sizeBytes" to target.length()),
        )
        return FeedbackPackage(target, target.name, target.length())
    }

    fun clearLogs() {
        config.logDir.deleteRecursively()
        config.feedbackDir.deleteRecursively()
        config.logDir.mkdirs()
        config.feedbackDir.mkdirs()
        MfLog.trace(LogCategory.FEEDBACK, "feedback_logs_cleared")
    }

    fun pruneLogs() {
        LogPruner.prune(
            config.logDir,
            config.retentionDays,
            config.maxTotalBytes,
        )
    }

    private fun buildManifest(logFiles: List<File>): String = buildJsonObject {
        put("generatedAt", JsonPrimitive(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
        put("sessionId", JsonPrimitive(sessionId))
        put("applicationId", JsonPrimitive(config.applicationId))
        put("versionName", JsonPrimitive(config.appVersionName))
        put("versionCode", JsonPrimitive(config.appVersionCode))
        put("buildType", JsonPrimitive(config.buildType))
        put(
            "files",
            buildJsonArray {
                logFiles.forEach { file ->
                    add(
                        buildJsonObject {
                            put("path", JsonPrimitive("logan/${file.name}"))
                            put("sizeBytes", JsonPrimitive(file.length()))
                            put("lastModified", JsonPrimitive(file.lastModified()))
                        },
                    )
                }
            },
        )
    }.toString()

    private fun decodeReadme(): String = """
        |Use tools/logan/decode-logan.sh with the matching Logan key and IV.
        |Debug logs use the repository development key in app/build.gradle.kts.
        |Release logs require LOGAN_AES_KEY and LOGAN_AES_IV environment variables.
        """.trimMargin()

    private fun ZipOutputStream.putText(path: String, text: String) {
        val entry = ZipEntry(path)
        putNextEntry(entry)
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
