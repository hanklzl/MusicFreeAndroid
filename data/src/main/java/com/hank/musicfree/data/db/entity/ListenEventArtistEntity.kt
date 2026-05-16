package com.hank.musicfree.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listen_event_artist",
    foreignKeys = [ForeignKey(
        entity = ListenEventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("eventId"),
        Index("artistName"),
    ],
)
data class ListenEventArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val artistName: String,
    val artistOrder: Int,
)
