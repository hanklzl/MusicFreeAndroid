package com.hank.musicfree.data.db.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.hank.musicfree.data.db.execSql

val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSql(
            """
            CREATE TABLE IF NOT EXISTS traffic_daily (
                local_date TEXT NOT NULL,
                network_type TEXT NOT NULL,
                bytes_received INTEGER NOT NULL,
                bytes_sent INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(local_date, network_type)
            )
            """.trimIndent(),
        )
    }
}
