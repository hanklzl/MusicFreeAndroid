package com.hank.musicfree.data.repository

import com.hank.musicfree.core.model.StarredKind
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.mapper.toEntity
import com.hank.musicfree.data.mapper.toModel
import org.junit.Assert.assertEquals
import org.junit.Test

class StarredSheetMapperJvmTest {

    private val converters = Converters()

    @Test
    fun `toEntity then toModel preserves all fields including kind`() {
        val source = StarredSheet(
            id = "alb-1",
            platform = "qq",
            title = "专辑 A",
            artist = "Artist",
            coverUri = "cover://a",
            sourceUrl = "https://example.com/a",
            kind = StarredKind.ALBUM,
            description = "desc",
            artwork = "art://a",
            worksNum = 12,
            raw = mapOf("foo" to "bar", "extra" to "7"),
        )

        val roundTripped = source
            .toEntity(createdAt = 100L, updatedAt = 200L, converters = converters)
            .toModel(converters)

        assertEquals(source, roundTripped)
    }

    @Test
    fun `kind defaults to sheet when not specified`() {
        val source = StarredSheet(
            id = "sh-1",
            platform = "kuwo",
            title = "歌单 B",
            artist = null,
            coverUri = null,
            sourceUrl = null,
        )

        val entity = source.toEntity(createdAt = 1L, updatedAt = 2L, converters = converters)

        assertEquals(StarredKind.SHEET, entity.kind)
        assertEquals(StarredKind.SHEET, entity.toModel(converters).kind)
    }
}
