package com.hank.musicfree.plugin.meta

import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GatewayCredentialStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val example = """{"appId":"synthetic-app","signSecret":"synthetic-secret-only","appVersion":"test","deviceId":"synthetic-device"}"""

    @Test fun `ciphertext is randomized and remains readable after store recreation`() = runBlocking {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dir = tmp.newFolder()
        val store = GatewayCredentialStore(dir) { key }
        store.write("元力QQ", example)
        val first = dir.listFiles()!!.single().readBytes()
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("synthetic-secret"))
        assertEquals(example, GatewayCredentialStore(dir) { key }.read("元力QQ"))
        store.write("元力QQ", example)
        assertFalse(first.contentEquals(dir.listFiles()!!.single().readBytes()))
        store.write("元力QQ", null)
        assertNull(store.read("元力QQ"))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun `tampering or a different device key fails closed without revealing input`() = runBlocking {
        val key = KeyGenerator.getInstance("AES").generateKey()
        val dir = tmp.newFolder(); val store = GatewayCredentialStore(dir) { key }
        store.write("元力QQ", example)
        val otherKey = KeyGenerator.getInstance("AES").generateKey()
        try { GatewayCredentialStore(dir) { otherKey }.read("元力QQ"); fail("must reject") }
        catch (e: IllegalStateException) { assertEquals("GATEWAY_CREDENTIALS_UNAVAILABLE", e.message); assertTrue(generateSequence<Throwable>(e) { it.cause }.all { it.message == "GATEWAY_CREDENTIALS_UNAVAILABLE" }) }
        val file = dir.listFiles()!!.single(); val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        try { store.read("元力QQ"); fail("must reject") }
        catch (e: IllegalStateException) { assertEquals("GATEWAY_CREDENTIALS_UNAVAILABLE", e.message) }
    }

    @Test fun `ciphertext is bound to its plugin platform`() = runBlocking {
        val key = KeyGenerator.getInstance("AES").generateKey(); val dir = tmp.newFolder()
        val store = GatewayCredentialStore(dir) { key }
        store.write("元力QQ", example); val source = dir.listFiles()!!.single()
        store.write("元力KW", example); val other = dir.listFiles()!!.single { it != source }
        other.writeBytes(source.readBytes())
        try { store.read("元力KW"); fail("must reject") }
        catch (e: IllegalStateException) { assertEquals("GATEWAY_CREDENTIALS_UNAVAILABLE", e.message) }
    }

    @Test fun `invalid values and unsupported platforms never create files`() = runBlocking {
        val dir = File(tmp.root, "private"); val store = GatewayCredentialStore(dir) { error("must not request key") }
        for (value in listOf("malformed-synthetic-private", "{}", example.replace("synthetic-app", "x\\r\\ny"))) {
            try { store.write("元力QQ", value); fail("must reject") }
            catch (e: IllegalArgumentException) { assertEquals("INVALID_GATEWAY_AUTH_CONFIG", e.message); assertTrue(generateSequence<Throwable>(e) { it.cause }.all { it.message == "INVALID_GATEWAY_AUTH_CONFIG" }) }
        }
        try { store.write("untrusted", example); fail("must reject") }
        catch (e: IllegalArgumentException) { assertEquals("UNSUPPORTED_GATEWAY_PLATFORM", e.message) }
        assertFalse(dir.exists())
    }
}
