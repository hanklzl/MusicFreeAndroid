package com.hank.musicfree.data.backup

import java.io.File

data class BackupSourceFile(
    val source: File,
    val archivePath: String,
)

class BackupFileSetProvider(
    private val dataRoot: File,
) {
    fun listBackupSourceFiles(): List<BackupSourceFile> {
        val entries = buildList {
            addDatabaseEntries(this)
            addDatastoreEntries(this)
            addPluginEntries(this)
            addPlaylistCoverEntries(this)
            addThemeBackgroundEntries(this)
        }
            .filter { it.source.exists() && it.source.isFile }
            .filter { BackupArchivePaths.isAllowedEntry(it.archivePath) }

        return entries.sortedBy { it.archivePath }
    }

    private fun addDatabaseEntries(out: MutableList<BackupSourceFile>) {
        out += databaseFile("musicfree.db", BackupArchivePaths.DB)
        out += databaseFile("musicfree.db-wal", BackupArchivePaths.DB_WAL)
        out += databaseFile("musicfree.db-shm", BackupArchivePaths.DB_SHM)
    }

    private fun addDatastoreEntries(out: MutableList<BackupSourceFile>) {
        val source = dataRoot.resolve("datastore/app_preferences.preferences_pb")
        out += BackupSourceFile(source, BackupArchivePaths.DATASTORE)
    }

    private fun addPluginEntries(out: MutableList<BackupSourceFile>) {
        val pluginsDir = dataRoot.resolve("files/plugins")
        if (!pluginsDir.isDirectory) return

        pluginsDir.listFiles()
            ?.filter { it.isFile && it.extension == "js" && it.name.isNotBlank() }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                out += BackupSourceFile(
                    source = file,
                    archivePath = "${BackupArchivePaths.PLUGINS_PREFIX}${file.name}",
                )
            }
    }

    private fun addPlaylistCoverEntries(out: MutableList<BackupSourceFile>) {
        val coversDir = dataRoot.resolve("files/playlist_covers")
        if (!coversDir.isDirectory) return

        coversDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relativePath = file.toRelativeString(coversDir)
                    .replace('\\', '/')
                out += BackupSourceFile(
                    source = file,
                    archivePath = "${BackupArchivePaths.PLAYLIST_COVERS_PREFIX}$relativePath",
                )
            }
    }

    private fun addThemeBackgroundEntries(out: MutableList<BackupSourceFile>) {
        val filesDir = dataRoot.resolve("files")
        if (!filesDir.isDirectory) return

        filesDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("theme_background.") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                out += BackupSourceFile(
                    source = file,
                    archivePath = "${BackupArchivePaths.THEME_BACKGROUND_PREFIX}${
                        file.name.substringAfter("theme_background.")
                    }",
                )
            }
    }

    private fun databaseFile(fileName: String, archivePath: String): BackupSourceFile {
        return BackupSourceFile(
            source = dataRoot.resolve("databases/$fileName"),
            archivePath = archivePath,
        )
    }
}
