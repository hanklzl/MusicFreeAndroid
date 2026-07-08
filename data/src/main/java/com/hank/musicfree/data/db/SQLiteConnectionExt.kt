package com.hank.musicfree.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

fun SQLiteConnection.execSql(sql: String) {
    prepare(sql).use { statement ->
        statement.step()
    }
}

inline fun <T> SQLiteConnection.useStatement(
    sql: String,
    block: (SQLiteStatement) -> T,
): T = prepare(sql).use(block)

fun SQLiteStatement.getTextOrNull(index: Int): String? =
    if (isNull(index)) null else getText(index)
