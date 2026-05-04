package com.zili.android.musicfreeandroid.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDao
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity

@Database(
    entities = [
        MusicItemEntity::class,
        PlaylistEntity::class,
        PlaylistMusicCrossRef::class,
        PlayQueueEntity::class,
        StarredSheetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playQueueDao(): PlayQueueDao
    abstract fun starredSheetDao(): StarredSheetDao
}
