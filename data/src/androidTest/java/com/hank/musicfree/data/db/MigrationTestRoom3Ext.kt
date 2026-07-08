package com.hank.musicfree.data.db

import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.data.db.migration.MIGRATION_10_11
import com.hank.musicfree.data.db.migration.MIGRATION_11_12
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
import com.hank.musicfree.data.db.migration.MIGRATION_13_14
import com.hank.musicfree.data.db.migration.MIGRATION_14_15
import com.hank.musicfree.data.db.migration.MIGRATION_15_16
import com.hank.musicfree.data.db.migration.MIGRATION_9_10

val APP_DATABASE_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
)

fun appDatabaseMigrationHelper(databaseName: String): MigrationTestHelper {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.targetContext.deleteDatabase(databaseName)
    return MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(databaseName),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )
}

fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { statement ->
        statement.step()
    }
}

fun SQLiteConnection.query(sql: String): TestCursor =
    TestCursor(prepare(sql))

suspend fun openLatestAppDatabase(databaseName: String, vararg migrations: Migration) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .withAppSQLiteDriver()
        .addMigrations(*migrations)
        .build()
    try {
        db.useReaderConnection { connection ->
            connection.usePrepared("SELECT 1") { statement ->
                statement.step()
            }
        }
    } finally {
        db.close()
    }
}

class TestCursor(
    private val statement: SQLiteStatement,
) : AutoCloseable {
    private val columnNames = statement.getColumnNames()
    private val rows = buildList {
        while (statement.step()) {
            add((0 until statement.getColumnCount()).map { column -> statement.valueAt(column) })
        }
    }
    private var position = -1

    val count: Int
        get() = rows.size

    fun moveToFirst(): Boolean {
        position = if (rows.isEmpty()) -1 else 0
        return rows.isNotEmpty()
    }

    fun moveToNext(): Boolean {
        if (position + 1 >= rows.size) return false
        position += 1
        return true
    }

    fun getString(index: Int): String =
        checkNotNull(currentRow()[index]) { "Column at index $index is NULL." }.toString()

    fun getLong(index: Int): Long = (currentRow()[index] as Number).toLong()

    fun getInt(index: Int): Int = (currentRow()[index] as Number).toInt()

    fun isNull(index: Int): Boolean = currentRow()[index] == null

    fun getColumnIndexOrThrow(name: String): Int =
        columnNames.indexOf(name).takeIf { it >= 0 }
            ?: error("Column '$name' does not exist. Columns: $columnNames")

    override fun close() {
        statement.close()
    }

    private fun currentRow(): List<Any?> {
        check(position in rows.indices) { "Cursor is not positioned on a row." }
        return rows[position]
    }

    private fun SQLiteStatement.valueAt(index: Int): Any? =
        when (getColumnType(index)) {
            SQLITE_DATA_INTEGER -> getLong(index)
            SQLITE_DATA_FLOAT -> getDouble(index)
            SQLITE_DATA_TEXT -> getText(index)
            SQLITE_DATA_BLOB -> getBlob(index)
            SQLITE_DATA_NULL -> null
            else -> error("Unsupported SQLite column type ${getColumnType(index)} at index $index")
        }
}
