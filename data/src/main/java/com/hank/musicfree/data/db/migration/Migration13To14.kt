package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_runtime_snapshots_namespace_updatedAtEpochMs`
            ON `runtime_snapshots` (`namespace`, `updatedAtEpochMs`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_runtime_snapshots_namespace_expiresAtEpochMs`
            ON `runtime_snapshots` (`namespace`, `expiresAtEpochMs`)
            """.trimIndent(),
        )
    }
}
