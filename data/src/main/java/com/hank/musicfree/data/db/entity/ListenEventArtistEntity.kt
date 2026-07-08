package com.hank.musicfree.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
