package com.zili.android.musicfreeandroid.logging

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class FeedbackLogExporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `createPackage zips manifest readme and raw logan files`() = runBlocking {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val logDir = tmp.newFolder("logan")
            val cacheDir = tmp.newFolder("cache")
            val feedbackDir = File(cacheDir, "feedback").apply { mkdirs() }
            val now = System.currentTimeMillis()
            val oldLog = createLogFile(logDir, "2026-05-01", "first", now)
            val newLog = createLogFile(logDir, "2026-05-02", "second", now + 1_000)

            val config = LoggingConfig(
                cacheDir = cacheDir,
                logDir = logDir,
                feedbackDir = feedbackDir,
                aesKey16 = "0123456789abcdef",
                aesIv16 = "abcdef0123456789",
                appVersionName = "1.0.0",
                appVersionCode = 1L,
                applicationId = "com.example.musicfree",
                buildType = "debug",
            )

            val exporter = FeedbackLogExporter(
                config,
                sessionIdProvider = { "session-1" },
            )

            val pkg = exporter.createPackage()

            assertTrue(pkg.file.exists())
            assertTrue(pkg.fileName.startsWith("musicfree-feedback-"))
            assertTrue(pkg.fileName.endsWith(".zip"))
            assertEquals(pkg.sizeBytes, pkg.file.length())

            val zip = ZipFile(pkg.file)
            try {
                val manifestEntry = zip.getEntry("manifest.json")
                val readmeEntry = zip.getEntry("README-decode.md")
                val oldLogEntry = zip.getEntry("logan/${oldLog.name}")
                val newLogEntry = zip.getEntry("logan/${newLog.name}")

                assertNotNull(manifestEntry)
                assertNotNull(readmeEntry)
                assertNotNull(oldLogEntry)
                assertNotNull(newLogEntry)

                val manifest = readZipText(zip, manifestEntry!!)
                val manifestJson = Json.parseToJsonElement(manifest).jsonObject

                assertEquals("session-1", manifestJson.getString("sessionId"))
                assertEquals(config.applicationId, manifestJson.getString("applicationId"))
                assertEquals(config.appVersionName, manifestJson.getString("versionName"))
                assertEquals(config.appVersionCode, manifestJson.getLong("versionCode"))
                assertEquals(config.buildType, manifestJson.getString("buildType"))
                assertEquals(Build.VERSION.SDK_INT.toLong(), manifestJson.getLong("androidSdk"))
                assertEquals(Build.VERSION.RELEASE.orEmpty(), manifestJson.getString("androidRelease"))
                assertEquals((Build.MANUFACTURER ?: "").orEmpty(), manifestJson.getString("deviceManufacturer"))
                assertEquals((Build.MODEL ?: "").orEmpty(), manifestJson.getString("deviceModel"))
                assertEquals(now, manifestJson.getLongOrNull("logStartLastModified"))
                assertEquals(newLog.lastModified(), manifestJson.getLongOrNull("logEndLastModified"))

                assertTrue(manifestJson.containsKey("generatedAt"))

                val files = manifestJson.getJsonArray("files")
                assertEquals(2, files.size)

                val first = files[0] as JsonObject
                val second = files[1] as JsonObject
                assertEquals("logan/${oldLog.name}", first.getString("path"))
                assertEquals(oldLog.length(), first.getLong("sizeBytes"))
                assertEquals(oldLog.lastModified(), first.getLong("lastModified"))
                assertEquals("logan/${newLog.name}", second.getString("path"))
                assertEquals(newLog.length(), second.getLong("sizeBytes"))
                assertEquals(newLog.lastModified(), second.getLong("lastModified"))

                val readme = readZipText(zip, readmeEntry!!)
                assertTrue(readme.contains("LOGAN_AES_KEY"))
                assertTrue(readme.contains("LOGAN_AES_IV"))

                val supportedAbis = manifestJson.getJsonArray("supportedAbis")
                    .map { value ->
                        (value as JsonPrimitive).content
                    }
                assertEquals(Build.SUPPORTED_ABIS.orEmpty().toList(), supportedAbis)
            } finally {
                zip.close()
            }

            assertEquals("flush", logger.events.first())
            assertEquals("trace:feedback_package_created", logger.events.last())
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `createPackage uses unique filenames for rapid exports`() = runBlocking {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val logDir = tmp.newFolder("logan")
            val cacheDir = tmp.newFolder("cache")
            val feedbackDir = File(cacheDir, "feedback").apply { mkdirs() }

            createLogFile(logDir, "quick.log", "quick", System.currentTimeMillis())

            val config = LoggingConfig(
                cacheDir = cacheDir,
                logDir = logDir,
                feedbackDir = feedbackDir,
                aesKey16 = "0123456789abcdef",
                aesIv16 = "abcdef0123456789",
                appVersionName = "1.0.0",
                appVersionCode = 1L,
                applicationId = "com.example.musicfree",
                buildType = "debug",
            )

            val exporter = FeedbackLogExporter(
                config,
                sessionIdProvider = { "session-1" },
            )

            val first = exporter.createPackage()
            val second = exporter.createPackage()

            assertNotEquals(first.fileName, second.fileName)
            assertTrue(first.file.exists())
            assertTrue(second.file.exists())
            assertTrue(first.fileName.startsWith("musicfree-feedback-"))
            assertTrue(first.fileName.endsWith(".zip"))
            assertTrue(second.fileName.startsWith("musicfree-feedback-"))
            assertTrue(second.fileName.endsWith(".zip"))
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `createPackage allows feedbackDir under share cache when logan cache is separate`() = runBlocking {
        val filesRoot = tmp.newFolder("files")
        val logDir = File(filesRoot, "logan").apply { mkdirs() }
        val loganCacheDir = File(filesRoot, "logan-cache").apply { mkdirs() }
        val feedbackShareRootDir = tmp.newFolder("cache")
        val feedbackDir = File(feedbackShareRootDir, "feedback").apply { mkdirs() }
        createLogFile(logDir, "release.log", "release", System.currentTimeMillis())

        val config = LoggingConfig(
            cacheDir = loganCacheDir,
            logDir = logDir,
            feedbackDir = feedbackDir,
            feedbackShareRootDir = feedbackShareRootDir,
            aesKey16 = "0123456789abcdef",
            aesIv16 = "abcdef0123456789",
            appVersionName = "1.0.0",
            appVersionCode = 1L,
            applicationId = "com.example.musicfree",
            buildType = "release",
        )

        val exporter = FeedbackLogExporter(
            config,
            sessionIdProvider = { "session-release" },
        )

        val pkg = exporter.createPackage()

        assertTrue(pkg.file.exists())
        assertTrue(pkg.file.toPath().normalize().startsWith(feedbackDir.toPath().normalize()))
    }

    @Test
    fun `createPackage rejects feedbackDir outside cache feedback path`() {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val logDir = tmp.newFolder("logan")
            val cacheDir = tmp.newFolder("cache")
            val feedbackDir = tmp.newFolder("feedback")

            val config = LoggingConfig(
                cacheDir = cacheDir,
                logDir = logDir,
                feedbackDir = feedbackDir,
                aesKey16 = "0123456789abcdef",
                aesIv16 = "abcdef0123456789",
                appVersionName = "1.0.0",
                appVersionCode = 1L,
                applicationId = "com.example.musicfree",
                buildType = "debug",
            )

            assertThrows(IllegalArgumentException::class.java) {
                FeedbackLogExporter(
                    config,
                    sessionIdProvider = { "session-1" },
                )
            }
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `clearLogs deletes feedback and log directories`() = runBlocking {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val logDir = tmp.newFolder("logan")
            val cacheDir = tmp.newFolder("cache")
            val feedbackDir = File(cacheDir, "feedback").apply { mkdirs() }

            logDir.resolve("old.log").writeText("old")
            feedbackDir.resolve("old.zip").writeText("old")

            val config = LoggingConfig(
                cacheDir = cacheDir,
                logDir = logDir,
                feedbackDir = feedbackDir,
                aesKey16 = "0123456789abcdef",
                aesIv16 = "abcdef0123456789",
                appVersionName = "1.0.0",
                appVersionCode = 1L,
                applicationId = "com.example.musicfree",
                buildType = "debug",
            )

            val exporter = FeedbackLogExporter(
                config,
                sessionIdProvider = { "session-1" },
            )

            exporter.clearLogs()

            assertTrue(logDir.exists())
            assertTrue(feedbackDir.exists())
            assertTrue(logDir.listFiles().isNullOrEmpty())
            assertTrue(feedbackDir.listFiles().isNullOrEmpty())

            assertTrue(
                logger.events.any {
                    it.startsWith("trace:") && it.contains("feedback_logs_cleared")
                },
            )
            assertTrue(
                logger.events.none {
                    it.startsWith("error:") && it.contains("feedback_logs_clear_failed")
                },
            )
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `pruneLogs deletes old logs`() = runBlocking {
        val logDir = tmp.newFolder("logan")
        val cacheDir = tmp.newFolder("cache")
        val feedbackDir = File(cacheDir, "feedback").apply { mkdirs() }

        val oldLog = createLogFile(logDir, "old.log", "old", System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000)
        val newLog = createLogFile(logDir, "new.log", "new", System.currentTimeMillis())

        val config = LoggingConfig(
            cacheDir = cacheDir,
            logDir = logDir,
            feedbackDir = feedbackDir,
            aesKey16 = "0123456789abcdef",
            aesIv16 = "abcdef0123456789",
            appVersionName = "1.0.0",
            appVersionCode = 1L,
            applicationId = "com.example.musicfree",
            buildType = "debug",
            retentionDays = 1,
        )

        val exporter = FeedbackLogExporter(
            config,
            sessionIdProvider = { "session-1" },
        )

        exporter.pruneLogs()

        assertTrue(!oldLog.exists())
        assertTrue(newLog.exists())
    }

    private fun createLogFile(directory: File, name: String, contents: String, lastModified: Long): File {
        return directory.resolve(name).apply {
            writeText(contents)
            setLastModified(lastModified)
        }
    }

    private fun readZipText(zip: ZipFile, entry: java.util.zip.ZipEntry): String {
        val stream: InputStream = zip.getInputStream(entry)
        val baos = ByteArrayOutputStream()
        stream.copyTo(baos)
        return baos.toString(Charsets.UTF_8)
    }

    private fun JsonObject.getString(field: String): String = get(field)?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.getLong(field: String): Long = get(field)?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
    private fun JsonObject.getLongOrNull(field: String): Long? = get(field)?.jsonPrimitive?.content?.toLongOrNull()
    private fun JsonObject.getJsonArray(field: String): JsonArray = get(field)?.jsonArray ?: JsonArray(emptyList())

    private class RecordingLogger : MfLogger {
        val events = mutableListOf<String>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events.add("trace:${event}")
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events.add("detail:${event}")
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events.add("error:${event}")
        }

        override fun flush() {
            events.add("flush")
        }
    }
}
