package com.hank.musicfree

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class GatewayCredentialBackupContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `legacy plugin metadata is excluded from cloud backup and device transfer`() {
        val legacy = xml("res/xml/backup_rules.xml")
        assertExcluded(legacy.documentElement)
        val modern = xml("res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val nodes = modern.getElementsByTagName(section)
            assertEquals(1, nodes.length)
            assertExcluded(nodes.item(0) as Element)
        }
        val manifest = xml("AndroidManifest.xml")
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals("@xml/backup_rules", application.getAttributeNS(ns, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ns, "dataExtractionRules"))
    }

    private fun assertExcluded(section: Element) {
        val excludes = section.getElementsByTagName("exclude")
        assertTrue((0 until excludes.length).any { index ->
            val entry = excludes.item(index) as Element
            entry.getAttribute("domain") == "file" &&
                entry.getAttribute("path") == "datastore/plugin_meta.preferences_pb"
        })
    }

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(root, "app/src/main/$path"))
}
