package com.hank.musicfree.data.db.entity

import androidx.room.Entity
import com.hank.musicfree.core.model.StarredKind

@Entity(
    tableName = "starred_sheets",
    primaryKeys = ["id", "platform"],
)
data class StarredSheetEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val kind: String = StarredKind.SHEET,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val rawJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
