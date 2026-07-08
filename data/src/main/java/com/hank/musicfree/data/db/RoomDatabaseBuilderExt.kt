package com.hank.musicfree.data.db

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

fun <T : RoomDatabase> RoomDatabase.Builder<T>.withAppSQLiteDriver(): RoomDatabase.Builder<T> =
    setDriver(AndroidSQLiteDriver())
