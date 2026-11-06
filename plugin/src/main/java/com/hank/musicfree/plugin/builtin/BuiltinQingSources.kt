package com.hank.musicfree.plugin.builtin

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** APK-owned code only. This catalog never contains authentication material. */
object BuiltinQingSources {
    const val USER_VARIABLE = "qingGatewayAuth"
    data class Entry(val platform: String, val assetName: String, val sha256: String) {
        val fileName: String get() = "builtin-$assetName"
    }
    val entries = listOf(
        Entry("元力KW", "yuanli-kw.js", "b4142ddc3ab05166500f6f5ca6c36b2683520bd435fe141c2d98c47eb6c0150b"),
        Entry("元力KG", "yuanli-kg.js", "a6e44df8dc7d7d97086999fcdf3b270f210599c1bfe237298b259943965e0e08"),
        Entry("元力WY", "yuanli-wy.js", "5ab5a0d84c7a07db9d5a35d35d9f4f692a44d21231dca40685ca16785342cc94"),
        Entry("元力QQ", "yuanli-tx.js", "55c4083288ef659b80de8a749e95f3c87c1f1ffab0541a6b074bb395f007b093"),
        Entry("bilibili", "yuanli-bili.js", "0ff9f619c06f41a099b535f4d5cf4161bc2609ff89e4a9c424a62c373095871c"),
        Entry("元力MG", "yuanli-mg.js", "f2f16c928ac3c988c4da44bdd1bd9cb5119a27de70283904a76f2eaef09d1570"),
    )
    fun entry(platform: String): Entry? = entries.firstOrNull { it.platform == platform }
    fun isProtected(platform: String): Boolean = entry(platform) != null
    fun isTrusted(platform: String, hash: String?): Boolean = entry(platform)?.sha256 == hash && hash != null
    fun runtimeVariables(platform: String, hash: String?, variables: Map<String, String>): Map<String, String> =
        if (isTrusted(platform, hash)) variables else variables - USER_VARIABLE

    fun materialize(directory: File, readAsset: (String) -> ByteArray) {
        // Validate all bundled assets before changing any installed code.
        val verified = entries.map { entry ->
            val bytes = readAsset("builtin-qing/${entry.assetName}")
            check(hash(bytes) == entry.sha256) { "Builtin source integrity mismatch: ${entry.platform}" }
            entry to bytes
        }
        directory.mkdirs()
        for ((entry, bytes) in verified) {
            val target = File(directory, entry.fileName)
            if (target.isFile && hash(target.readBytes()) == entry.sha256) continue
            val temporary = File.createTempFile("builtin-qing-", ".tmp", directory)
            try {
                temporary.writeBytes(bytes)
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
    }
    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
