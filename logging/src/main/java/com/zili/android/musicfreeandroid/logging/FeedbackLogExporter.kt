package com.zili.android.musicfreeandroid.logging

import android.os.Build
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FeedbackLogExporter(
    private val config: LoggingConfig,
    private val sessionId: String,
) {
    init {
        val feedbackDirectory = config.feedbackDir.toPath().normalize().toAbsolutePath()
        val allowedFeedbackRoot = config.cacheDir.resolve("feedback").toPath().normalize().toAbsolutePath()

        require(
            feedbackDirectory == allowedFeedbackRoot || feedbackDirectory.startsWith(allowedFeedbackRoot),
        ) {
            "feedbackDir must be within cacheDir/feedback for secure sharing"
        }
    }

    fun createPackage(): FeedbackPackage {
        MfLog.flush()
        pruneLogs()

        config.feedbackDir.mkdirs()
        val target = nextAvailablePackageFile()
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
        clearAndRecreate(config.logDir)
        clearAndRecreate(config.feedbackDir)
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
        put("androidSdk", JsonPrimitive(Build.VERSION.SDK_INT))
        put("androidRelease", JsonPrimitive(Build.VERSION.RELEASE.orEmpty()))
        put("deviceManufacturer", JsonPrimitive(Build.MANUFACTURER.orEmpty()))
        put("deviceModel", JsonPrimitive(Build.MODEL.orEmpty()))
        put(
            "supportedAbis",
            buildJsonArray {
                Build.SUPPORTED_ABIS.orEmpty().forEach { abi ->
                    add(JsonPrimitive(abi))
                }
            },
        )
        val (logStartLastModified, logEndLastModified) = computeLogDateRange(logFiles)
        put(
            "logStartLastModified",
            logStartLastModified?.let { JsonPrimitive(it) } ?: JsonNull,
        )
        put("logEndLastModified", logEndLastModified?.let { JsonPrimitive(it) } ?: JsonNull)
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

    private fun computeLogDateRange(logFiles: List<File>): Pair<Long?, Long?> {
        if (logFiles.isEmpty()) {
            return null to null
        }
        val sortedByDate = logFiles.map { it.lastModified() }.sorted()
        return sortedByDate.first() to sortedByDate.last()
    }

    private fun decodeReadme(): String = """
        |Use tools/logan/decode-logan.sh with the matching Logan key and IV.
        |Debug logs use the repository development key in app/build.gradle.kts.
        |Release logs require LOGAN_AES_KEY and LOGAN_AES_IV environment variables.
        """.trimMargin()

    private fun nextAvailablePackageFile(): File {
        val stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS"))
        var suffix = 0
        while (true) {
            val filename = when (suffix) {
                0 -> "musicfree-feedback-$stamp.zip"
                else -> "musicfree-feedback-$stamp-$suffix.zip"
            }

            val candidate = File(config.feedbackDir, filename)
            if (candidate.createNewFile()) {
                candidate.delete()
                return candidate
            }

            suffix++
            if (suffix > 10000) {
                throw IllegalStateException("Failed to allocate unique feedback package filename after 10000 attempts")
            }
        }
    }

    private fun clearAndRecreate(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            val error = IOException("Failed to clear directory: ${directory.absolutePath}")
            MfLog.error(LogCategory.FEEDBACK, "feedback_logs_clear_failed", error)
            throw error
        }

        if (!directory.mkdirs() && !directory.exists()) {
            val error = IOException("Failed to recreate directory: ${directory.absolutePath}")
            MfLog.error(LogCategory.FEEDBACK, "feedback_logs_clear_failed", error)
            throw error
        }
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        val entry = ZipEntry(path)
        putNextEntry(entry)
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
