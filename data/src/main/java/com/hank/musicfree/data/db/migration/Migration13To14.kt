package com.hank.musicfree.data.db.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.hank.musicfree.data.db.execSql

val MIGRATION_13_14 = object : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            CREATE TABLE IF NOT EXISTS `runtime_snapshots` (
                `namespace` TEXT NOT NULL,
                `key` TEXT NOT NULL,
                `snapshotVersion` INTEGER NOT NULL,
                `sourceSignature` TEXT NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                `updatedAtEpochMs` INTEGER NOT NULL,
                `expiresAtEpochMs` INTEGER,
                `payloadJson` TEXT NOT NULL,
                PRIMARY KEY(`namespace`, `key`)
            )
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE INDEX IF NOT EXISTS `index_runtime_snapshots_namespace_updatedAtEpochMs`
            ON `runtime_snapshots` (`namespace`, `updatedAtEpochMs`)
            """.trimIndent(),
        )
        connection.execSql(
            """
            CREATE INDEX IF NOT EXISTS `index_runtime_snapshots_namespace_expiresAtEpochMs`
            ON `runtime_snapshots` (`namespace`, `expiresAtEpochMs`)
            """.trimIndent(),
        )
    }
}
