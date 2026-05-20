package com.hank.musicfree.core.ui

import androidx.compose.ui.graphics.Color
import com.hank.musicfree.core.theme.DarkMusicFreeColors
import com.hank.musicfree.core.theme.LightMusicFreeColors
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteIconTintTest {
    @Test
    fun `starred favorite icon always uses red tint`() {
        assertEquals(Color(0xFFE31639), favoriteIconTint(starred = true, inactiveTint = LightMusicFreeColors.primary))
        assertEquals(Color(0xFFE31639), favoriteIconTint(starred = true, inactiveTint = DarkMusicFreeColors.primary))
    }

    @Test
    fun `unstarred favorite icon keeps caller inactive tint`() {
        assertEquals(
            LightMusicFreeColors.appBarText,
            favoriteIconTint(starred = false, inactiveTint = LightMusicFreeColors.appBarText),
        )
        assertEquals(
            DarkMusicFreeColors.text,
            favoriteIconTint(starred = false, inactiveTint = DarkMusicFreeColors.text),
        )
    }
}
