package com.zili.android.musicfreeandroid.updater.checker

import com.zili.android.musicfreeandroid.updater.model.ApkVariant
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbiResolverTest {

    private fun makeInfo(abis: List<String>): UpdateInfo = UpdateInfo(
        schemaVersion = 2,
        version = "1.2.3",
        versionCode = 10203,
        releasedAt = "2026-05-16T18:00:00Z",
        releaseNotesUrl = "https://example.com/notes",
        changeLog = emptyList(),
        variants = abis.associateWith {
            ApkVariant(
                download = listOf("https://example.com/$it.apk"),
                size = 1,
                sha256 = it,
            )
        },
    )

    @Test
    fun `selects arm64 when device prefers arm64`() {
        val resolver = AbiResolver { listOf("arm64-v8a", "armeabi-v7a", "armeabi") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        val resolved = resolver.resolve(info)
        assertEquals("arm64-v8a", resolved?.abi)
        assertEquals("arm64-v8a", resolved?.variant?.sha256)
    }

    @Test
    fun `selects x86_64 when device prefers x86_64`() {
        val resolver = AbiResolver { listOf("x86_64", "x86") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        assertEquals("x86_64", resolver.resolve(info)?.abi)
    }

    @Test
    fun `returns null when no supported abi has a variant`() {
        val resolver = AbiResolver { listOf("armeabi-v7a", "armeabi") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        assertNull(resolver.resolve(info))
    }

    @Test
    fun `returns null when variants map empty`() {
        val resolver = AbiResolver { listOf("arm64-v8a") }
        val info = makeInfo(emptyList())
        assertNull(resolver.resolve(info))
    }

    @Test
    fun `returns null when device supported abi list empty`() {
        val resolver = AbiResolver { emptyList() }
        val info = makeInfo(listOf("arm64-v8a"))
        assertNull(resolver.resolve(info))
    }

    @Test
    fun `respects device abi priority order`() {
        // 设备同时支持 arm64 和 x86_64（罕见但可能），按列表顺序优先
        val resolver = AbiResolver { listOf("x86_64", "arm64-v8a") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        assertEquals("x86_64", resolver.resolve(info)?.abi)
    }
}
