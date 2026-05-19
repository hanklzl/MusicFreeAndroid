package com.hank.musicfree

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupApplicationContractTest {
    private val projectRoot: Path = locateProjectRoot()
    private val source = Files.readString(
        projectRoot.resolve("app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt"),
    )

    @Test
    fun `pending restore remains in attachBaseContext and before onCreate startup complete`() {
        val attachBody = extractFunctionBody(source, "attachBaseContext")
        val onCreateBody = extractFunctionBody(source, "onCreate")

        assertTrue(
            "attachBaseContext should start StartupTelemetry before pending restore",
            attachBody.indexOf("StartupTelemetry.attachBaseContextStart()") >= 0,
        )
        assertTrue(
            "pending restore must remain in attachBaseContext",
            attachBody.indexOf("StartupBackupRestore.applyIfPending(this)") >= 0,
        )
        assertTrue(
            "Application complete should stay in onCreate",
            onCreateBody.indexOf("StartupTelemetry.completeApplicationStartup(") >= 0,
        )
    }

    @Test
    fun `logging ready happens before startup coordinators are scheduled`() {
        val onCreateBody = extractFunctionBody(source, "onCreate")
        val loggingReady = onCreateBody.indexOf("StartupTelemetry.markLoggingReady(")
        val runtimeStart = onCreateBody.indexOf("runtimeRestoreCoordinator.start()")
        val applicationComplete = onCreateBody.indexOf("StartupTelemetry.completeApplicationStartup(")

        assertTrue("StartupTelemetry.markLoggingReady should be present", loggingReady >= 0)
        assertTrue("runtimeRestoreCoordinator.start should be present", runtimeStart >= 0)
        assertTrue("application complete should be present", applicationComplete >= 0)
        assertTrue("logging should be ready before startup coordinators", loggingReady < runtimeStart)
        assertTrue("application complete should be after coordinators are scheduled", runtimeStart < applicationComplete)
    }

    private fun extractFunctionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName")
        require(signatureIndex >= 0) { "Function $functionName not found" }
        val openBrace = source.indexOf('{', signatureIndex)
        require(openBrace >= 0) { "Function $functionName has no body" }
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(openBrace + 1, index)
                }
            }
        }
        error("Function $functionName body was not closed")
    }

    private fun locateProjectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) return current
            current = current.parent
        }
        error("Could not locate project root")
    }
}
