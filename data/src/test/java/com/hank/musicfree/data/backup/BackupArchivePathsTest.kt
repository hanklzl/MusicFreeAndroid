package com.hank.musicfree.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class BackupArchivePathsTest {
    @Test
    fun `safe paths include only migration whitelist`() {
        assertTrue(BackupArchivePaths.isAllowedEntry("manifest.json"))
        assertTrue(BackupArchivePaths.isAllowedEntry("db/musicfree.db"))
        assertTrue(BackupArchivePaths.isAllowedEntry("db/musicfree.db-wal"))
        assertTrue(BackupArchivePaths.isAllowedEntry("db/musicfree.db-shm"))
        assertTrue(BackupArchivePaths.isAllowedEntry("datastore/app_preferences.preferences_pb"))
        assertTrue(BackupArchivePaths.isAllowedEntry("files/plugins/wy.js"))
        assertTrue(BackupArchivePaths.isAllowedEntry("files/playlist_covers/cover.jpg"))
        assertTrue(BackupArchivePaths.isAllowedEntry("files/theme_background.jpg"))

        assertFalse(BackupArchivePaths.isAllowedEntry("/absolute"))
        assertFalse(BackupArchivePaths.isAllowedEntry("../musicfree.db"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/../logan/x"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/logan/20260517"))
        assertFalse(BackupArchivePaths.isAllowedEntry("cache/updates/app.apk"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/plugins/sub/wy.js"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/theme_background.jpg/foo"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/plugins/.js"))
        assertFalse(BackupArchivePaths.isAllowedEntry(""))
        assertFalse(BackupArchivePaths.isAllowedEntry("   "))
    }

    @Test
    fun `sha256 returns lowercase hex`() {
        val actual = BackupArchivePaths.sha256(ByteArrayInputStream("abc".toByteArray()))

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            actual,
        )
    }

    @Test
    fun `sha256 returns empty stream hash`() {
        val actual = BackupArchivePaths.sha256(ByteArrayInputStream(ByteArray(0)))

        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            actual,
        )
    }

    @Test
    fun `sha256 propagates read failures`() {
        val exception = assertThrows(IOException::class.java) {
            BackupArchivePaths.sha256(FailingInputStream())
        }

        assertEquals("read failed", exception.message)
    }

    @Test
    fun `sha256 handles zero-byte read input streams`() {
        val actual = BackupArchivePaths.sha256(ZeroReadThenBytesInputStream("abc".toByteArray()))

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            actual,
        )
    }

    @Test
    fun `sha256 fails when input stream returns zero bytes repeatedly`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            BackupArchivePaths.sha256(AlwaysZeroInputStream())
        }

        assertTrue(exception.message!!.contains("16"))
    }

    private class ZeroReadThenBytesInputStream(
        private val bytes: ByteArray,
    ) : InputStream() {
        private var index = 0
        private var emittedZeroRead = false

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (index >= bytes.size) return -1
            if (!emittedZeroRead) {
                emittedZeroRead = true
                return 0
            }
            val readCount = min(len, bytes.size - index)
            bytes.copyInto(b, off, index, index + readCount)
            index += readCount
            return readCount
        }

        override fun read(): Int {
            val single = ByteArray(1)
            val read = read(single)
            if (read <= 0) return -1
            return single[0].toInt() and 0xFF
        }
    }

    private class AlwaysZeroInputStream : InputStream() {
        override fun read(b: ByteArray, off: Int, len: Int): Int = 0

        override fun read(): Int = 0
    }

    private class FailingInputStream : InputStream() {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            throw IOException("read failed")
        }

        override fun read(): Int {
            throw IOException("read failed")
        }
    }
}
