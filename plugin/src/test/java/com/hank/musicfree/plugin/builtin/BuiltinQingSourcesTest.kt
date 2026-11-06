package com.hank.musicfree.plugin.builtin

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuiltinQingSourcesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun asset(path: String): ByteArray {
        val root = sequenceOf(File("src/main/assets"), File("plugin/src/main/assets"))
            .first { File(it, path).isFile }
        return File(root, path).readBytes()
    }

    @Test fun `installs all identities and repairs changed code without touching other plugins`() {
        val directory = temporary.newFolder()
        val external = File(directory, "other.js").apply { writeText("external") }
        BuiltinQingSources.materialize(directory, ::asset)
        assertEquals(setOf("元力KW", "元力KG", "元力WY", "元力QQ", "bilibili", "元力MG"),
            BuiltinQingSources.entries.map { it.platform }.toSet())
        for (entry in BuiltinQingSources.entries) {
            assertArrayEquals(asset("builtin-qing/${entry.assetName}"), File(directory, entry.fileName).readBytes())
        }
        val first = BuiltinQingSources.entries.first()
        File(directory, first.fileName).writeText("tampered")
        BuiltinQingSources.materialize(directory, ::asset)
        assertArrayEquals(asset("builtin-qing/${first.assetName}"), File(directory, first.fileName).readBytes())
        assertEquals("external", external.readText())
    }

    @Test fun `bad APK asset fails before changing installed sources`() {
        val directory = temporary.newFolder()
        val first = BuiltinQingSources.entries.first()
        val installed = File(directory, first.fileName).apply { writeText("old") }
        try {
            BuiltinQingSources.materialize(directory) { path ->
                if (path.endsWith(BuiltinQingSources.entries.last().assetName)) "invalid".toByteArray() else asset(path)
            }
            fail("Asset mismatch must reject the entire catalog")
        } catch (_: IllegalStateException) { }
        assertEquals("old", installed.readText())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun `private variable requires exact bundled code hash and platform`() {
        val entry = BuiltinQingSources.entries.first()
        val variables = mapOf("cookie" to "test-cookie", BuiltinQingSources.USER_VARIABLE to "synthetic-test-value")
        assertEquals(variables, BuiltinQingSources.runtimeVariables(entry.platform, entry.sha256, variables))
        for ((platform, hash) in listOf(entry.platform to "wrong", entry.platform to null, "external" to entry.sha256)) {
            assertEquals(mapOf("cookie" to "test-cookie"), BuiltinQingSources.runtimeVariables(platform, hash, variables))
        }
    }
}
