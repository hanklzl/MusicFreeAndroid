package com.zili.android.musicfreeandroid.logging

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val sessionIdProvider: () -> String = { LoggingInitializer.currentSessionId },
) : FeedbackLogExporterContract {
    init {
        val feedbackDirectory = config.feedbackDir.toPath().normalize().toAbsolutePath()
        val allowedFeedbackRoot = config.cacheDir.resolve("feedback").toPath().normalize().toAbsolutePath()

        require(
            feedbackDirectory == allowedFeedbackRoot || feedbackDirectory.startsWith(allowedFeedbackRoot),
        ) {
            "feedbackDir must be within cacheDir/feedback for secure sharing"
        }
    }

    override suspend fun createPackage(): FeedbackPackage = withContext(Dispatchers.IO) {
        createPackageBlocking()
    }

    override suspend fun clearLogs() = withContext(Dispatchers.IO) {
        clearLogsBlocking()
    }

    override suspend fun pruneLogs() = withContext(Dispatchers.IO) {
        pruneLogsBlocking()
    }

    private fun createPackageBlocking(): FeedbackPackage {
        MfLog.flush()
        pruneLogsBlocking()

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

    private fun clearLogsBlocking() {
        clearAndRecreate(config.logDir)
        clearAndRecreate(config.feedbackDir)
        MfLog.trace(LogCategory.FEEDBACK, "feedback_logs_cleared")
    }

    private fun pruneLogsBlocking() {
        LogPruner.prune(
            config.logDir,
            config.retentionDays,
            config.maxTotalBytes,
        )
    }

    private fun buildManifest(logFiles: List<File>): String = buildJsonObject {
        put("generatedAt", JsonPrimitive(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
        put("sessionId", JsonPrimitive(sessionIdProvider()))
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
        |Decoded text is written under tools/logan/out/decoded by default.
        |Debug logs use the repository development key in app/build.gradle.kts.
        |Release logs require LOGAN_AES_KEY and LOGAN_AES_IV environment variables.
        """.trimMargin()

    private fun nextAvailablePackageFile(): File {
        return File.createTempFile("musicfree-feedback-", ".zip", config.feedbackDir)
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
