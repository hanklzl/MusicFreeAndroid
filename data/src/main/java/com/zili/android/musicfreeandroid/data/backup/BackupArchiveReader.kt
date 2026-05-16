package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class StagedRestore(
    val id: String,
    val directory: File,
    val manifest: BackupManifest,
)

class BackupArchiveException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class BackupArchiveReader(
    private val json: Json,
) {
    private val safeIdPattern = Regex("[A-Za-z0-9._-]{1,96}")

    fun extractAndValidate(
        input: InputStream,
        stagingRoot: File,
        id: String,
    ): StagedRestore {
        val stagingDir = resolveSafeStagingDir(stagingRoot, id)
        recreateStagingDirectory(stagingDir)

        try {
            val manifest = extractArchiveAndParseManifest(input, stagingDir)
            validateManifest(manifest, stagingDir)
            validateHashAndSize(manifest, stagingDir)
            return StagedRestore(id = id, directory = stagingDir, manifest = manifest)
        } catch (error: Throwable) {
            cleanupStagingDirectory(stagingDir)
            if (error is BackupArchiveException) throw error
            throw BackupArchiveException("Failed to extract backup archive", error)
        }
    }

    private fun extractArchiveAndParseManifest(
        input: InputStream,
        stagingDir: File,
    ): BackupManifest {
        val extractedEntries = HashSet<String>()
        val baseDir = stagingDir.canonicalFile
        var manifestContent: ByteArray? = null

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    throw BackupArchiveException("Directory entry is not allowed in backup archive: ${entry.name}")
                }

                validateZipEntry(entry, stagingDir, baseDir, extractedEntries)
                val canonicalEntryName = entry.name

                if (canonicalEntryName == BackupArchivePaths.MANIFEST) {
                    manifestContent = readManifestEntryToBytes(zip)
                    val outputFile = File(stagingDir, canonicalEntryName)
                    outputFile.parentFile?.mkdirs()
                    outputFile.writeBytes(manifestContent)
                    zip.closeEntry()
                    continue
                }

                val outputFile = File(stagingDir, canonicalEntryName)
                outputFile.parentFile?.mkdirs()
                outputFile.outputStream().buffered().use { output ->
                    copyToOutput(zip, output)
                }
                zip.closeEntry()
            }
        }

        val manifestBytes = manifestContent ?: throw BackupArchiveException("manifest.json missing")
        val manifestText = manifestBytes.toString(Charsets.UTF_8)
        val manifest = runCatching {
            json.decodeFromString<BackupManifest>(manifestText)
        }.getOrElse {
            throw BackupArchiveException("Failed to parse manifest.json", it)
        }

        if (manifest.schemaVersion != BackupManifest.CURRENT_SCHEMA_VERSION) {
            throw BackupArchiveException(
                "Unsupported manifest schema: ${manifest.schemaVersion}, expected: ${BackupManifest.CURRENT_SCHEMA_VERSION}",
            )
        }

        validateEntriesInManifestAndZip(manifest, extractedEntries)
        return manifest
    }

    private fun validateEntriesInManifestAndZip(manifest: BackupManifest, extractedEntries: Set<String>) {
        val manifestFiles = manifest.files
        val manifestPaths = HashSet<String>(manifestFiles.size)

        for (entry in manifestFiles) {
            if (!manifestPaths.add(entry.path)) {
                throw BackupArchiveException("Duplicate manifest entry: ${entry.path}")
            }
            if (entry.path == BackupArchivePaths.MANIFEST) {
                throw BackupArchiveException("Manifest file cannot be part of backup file list: ${entry.path}")
            }
            if (!BackupArchivePaths.isAllowedEntry(entry.path)) {
                throw BackupArchiveException("Manifest entry is not allowed in backup archive: ${entry.path}")
            }
        }

        val requiredFilesInManifest = BackupArchivePaths.requiredEntries - BackupArchivePaths.MANIFEST
        val missingFromManifest = requiredFilesInManifest.filterNot { it in manifestPaths }
        if (missingFromManifest.isNotEmpty()) {
            throw BackupArchiveException("Manifest is missing required entries: ${missingFromManifest.joinToString()}")
        }

        val missingFromArchive = BackupArchivePaths.requiredEntries.filterNot { it in extractedEntries }
        if (missingFromArchive.isNotEmpty()) {
            throw BackupArchiveException("Archive is missing required entries: ${missingFromArchive.joinToString()}")
        }

        val undeclaredEntries = extractedEntries - manifestPaths - BackupArchivePaths.MANIFEST
        if (undeclaredEntries.isNotEmpty()) {
            throw BackupArchiveException("Archive contains files not listed in manifest: ${undeclaredEntries.joinToString()}")
        }

        val manifestNotInArchive = manifestPaths - extractedEntries
        if (manifestNotInArchive.isNotEmpty()) {
            throw BackupArchiveException("Manifest references missing archive entries: ${manifestNotInArchive.joinToString()}")
        }
    }

    private fun validateZipEntry(
        entry: ZipEntry,
        stagingDir: File,
        baseDir: File,
        extractedEntries: MutableSet<String>,
    ) {
        val name = entry.name
        if (!BackupArchivePaths.isAllowedEntry(name)) {
            throw BackupArchiveException("Backup entry is not allowed: $name")
        }
        if (!extractedEntries.add(name)) {
            throw BackupArchiveException("Duplicate backup entry: $name")
        }

        val targetFile = File(stagingDir, name).canonicalFile
        val basePath = baseDir.path
        if (targetFile != baseDir && !targetFile.path.startsWith("$basePath${File.separator}")) {
            throw BackupArchiveException("Backup entry is outside staging directory: $name")
        }
        if (targetFile.path == basePath) {
            throw BackupArchiveException("Entry must not point to staging root directory: $name")
        }
    }

    private fun validateManifest(manifest: BackupManifest, stagingDir: File) {
        manifest.files.forEach { manifestFile ->
            val file = File(stagingDir, manifestFile.path)
            if (!file.exists()) {
                throw BackupArchiveException("Manifest references missing file: ${manifestFile.path}")
            }
            if (!file.isFile) {
                throw BackupArchiveException("Manifest file path is not a regular file: ${manifestFile.path}")
            }
        }
    }

    private fun validateHashAndSize(manifest: BackupManifest, stagingDir: File) {
        manifest.files.forEach { manifestFile ->
            val file = File(stagingDir, manifestFile.path)
            val actualSize = file.length()
            if (actualSize != manifestFile.sizeBytes) {
                throw BackupArchiveException(
                    "Manifest file size mismatch: ${manifestFile.path}, expected=${manifestFile.sizeBytes}, actual=$actualSize",
                )
            }

            val actualHash = file.inputStream().buffered().use { input ->
                BackupArchivePaths.sha256(input)
            }
            if (!actualHash.equals(manifestFile.sha256, ignoreCase = true)) {
                throw BackupArchiveException("Manifest hash mismatch: ${manifestFile.path}")
            }
        }
    }

    private fun recreateStagingDirectory(stagingDir: File) {
        cleanupStagingDirectory(stagingDir)
        if (!stagingDir.mkdirs()) {
            throw BackupArchiveException("Failed to create staging directory: ${stagingDir.path}")
        }
    }

    private fun cleanupStagingDirectory(stagingDir: File) {
        if (!stagingDir.exists()) return
        if (!stagingDir.deleteRecursively() || stagingDir.exists()) {
            throw BackupArchiveException("Failed to clean staging directory: ${stagingDir.path}")
        }
    }

    private fun resolveSafeStagingDir(stagingRoot: File, id: String): File {
        if (!safeIdPattern.matches(id)) {
            throw BackupArchiveException("Invalid staging id: $id")
        }
        val root = stagingRoot.canonicalFile
        val stagingDir = File(root, id).canonicalFile
        if (!stagingDir.isInside(root)) {
            throw BackupArchiveException("Staging directory is outside root: $id")
        }
        return stagingDir
    }

    private fun readManifestEntryToBytes(entry: ZipInputStream): ByteArray {
        val bytes = ByteArrayOutputStream()
        copyToOutput(entry, bytes, maxBytes = MAX_MANIFEST_BYTES)
        return bytes.toByteArray()
    }

    private fun copyToOutput(source: InputStream, destination: OutputStream, maxBytes: Long? = null) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            if (read > 0) {
                totalBytes += read.toLong()
                if (maxBytes != null && totalBytes > maxBytes) {
                    throw BackupArchiveException("manifest.json exceeds maximum size")
                }
                destination.write(buffer, 0, read)
            }
        }
    }

    private fun File.isInside(parent: File): Boolean {
        if (this == parent) return false
        val parentPath = parent.path
        return path.startsWith("$parentPath${File.separator}")
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 1024 * 1024L
    }
}
