package com.hank.musicfree.plugin.meta

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Device-only user supplied credentials. No default or bundled credential exists. */
@Singleton
class GatewayCredentialStore internal constructor(
    private val directory: File,
    private val keyProvider: () -> SecretKey,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        File(context.noBackupFilesDir, "qing-gateway-credentials"),
        { deviceKey() },
    )

    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    val changes: StateFlow<Long> = revision

    suspend fun read(platform: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireSupported(platform)
            val file = fileFor(platform)
            if (!file.exists()) return@withLock null
            try {
                val bytes = file.readBytes()
                check(bytes.size in 30..MAX_BYTES + 64 && bytes[0] == 1.toByte())
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
                cipher.updateAAD(platform.toByteArray(Charsets.UTF_8))
                cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)
            } catch (_: Exception) {
                // Never attach crypto/parser exceptions which could retain sensitive input.
                throw IllegalStateException("GATEWAY_CREDENTIALS_UNAVAILABLE")
            }
        }
    }

    suspend fun write(platform: String, value: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireSupported(platform)
            if (!value.isNullOrBlank()) validate(value)
            val file = fileFor(platform)
            try {
                if (value.isNullOrBlank()) {
                    Files.deleteIfExists(file.toPath())
                } else {
                    Files.createDirectories(directory.toPath())
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
                    cipher.updateAAD(platform.toByteArray(Charsets.UTF_8))
                    check(cipher.iv.size == 12)
                    val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
                    val temporary = File.createTempFile("credential-", ".tmp", directory)
                    try {
                        FileOutputStream(temporary).use { it.write(encrypted); it.fd.sync() }
                        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } finally { temporary.delete() }
                }
                revision.value += 1
            } catch (_: Exception) {
                throw IllegalStateException("GATEWAY_CREDENTIALS_WRITE_FAILED")
            }
        }
    }

    private fun fileFor(platform: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(platform.toByteArray(Charsets.UTF_8))
        return File(directory, digest.joinToString("") { "%02x".format(it) } + ".gcm")
    }

    companion object {
        const val VARIABLE = "qingGatewayAuth"
        private const val MAX_BYTES = 8192
        private const val ALIAS = "musicfree.qing.gateway.credentials.v1"
        fun supports(platform: String) = platform in setOf("元力KW", "元力KG", "元力WY", "元力QQ", "元力MG")

        private fun requireSupported(platform: String) {
            require(supports(platform)) { "UNSUPPORTED_GATEWAY_PLATFORM" }
        }

        private fun validate(value: String) {
            try {
                check(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
                val obj = Json.parseToJsonElement(value).jsonObject
                for (key in listOf("appId", "signSecret", "appVersion", "deviceId")) {
                    val primitive = obj.getValue(key).jsonPrimitive
                    check(primitive.isString && primitive.content.isNotBlank() && !primitive.content.contains('\r') && !primitive.content.contains('\n'))
                }
            } catch (_: Exception) {
                throw IllegalArgumentException("INVALID_GATEWAY_AUTH_CONFIG")
            }
        }

        @Synchronized private fun deviceKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
            }.generateKey()
        }
    }
}
