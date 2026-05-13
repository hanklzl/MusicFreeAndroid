package com.zili.android.musicfreeandroid.feature.home.component

import com.zili.android.musicfreeandroid.feature.home.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIconMappingTest {

    @Test
    fun `home icon mapping locks the exact RN drawable contract`() {
        val ids = listOf(
            HomeIcons.NavMenu,
            HomeIcons.NavSearch,
            HomeIcons.OperationRecommend,
            HomeIcons.OperationTopList,
            HomeIcons.OperationHistory,
            HomeIcons.OperationLocal,
            HomeIcons.SheetsCreate,
            HomeIcons.SheetsImport,
            HomeIcons.DrawerSettings,
            HomeIcons.DrawerPluginManagement,
            HomeIcons.DrawerTheme,
            HomeIcons.DrawerScheduleClose,
            HomeIcons.DrawerBackup,
            HomeIcons.DrawerPermissions,
            HomeIcons.DrawerCheckUpdate,
            HomeIcons.DrawerAbout,
        )

        assertEquals(R.drawable.ic_home_bars_3, HomeIcons.NavMenu)
        assertEquals(R.drawable.ic_home_magnifying_glass, HomeIcons.NavSearch)
        assertEquals(R.drawable.ic_home_fire, HomeIcons.OperationRecommend)
        assertEquals(R.drawable.ic_home_trophy, HomeIcons.OperationTopList)
        assertEquals(R.drawable.ic_home_clock_outline, HomeIcons.OperationHistory)
        assertEquals(R.drawable.ic_home_folder_music_outline, HomeIcons.OperationLocal)
        assertEquals(R.drawable.ic_home_plus, HomeIcons.SheetsCreate)
        assertEquals(R.drawable.ic_home_inbox_arrow_down, HomeIcons.SheetsImport)
        assertEquals(R.drawable.ic_home_cog_8_tooth, HomeIcons.DrawerSettings)
        assertEquals(R.drawable.ic_home_javascript, HomeIcons.DrawerPluginManagement)
        assertEquals(R.drawable.ic_home_t_shirt_outline, HomeIcons.DrawerTheme)
        assertEquals(R.drawable.ic_home_alarm_outline, HomeIcons.DrawerScheduleClose)
        assertEquals(R.drawable.ic_home_circle_stack, HomeIcons.DrawerBackup)
        assertEquals(R.drawable.ic_home_shield_keyhole_outline, HomeIcons.DrawerPermissions)
        assertEquals(R.drawable.ic_home_arrow_path, HomeIcons.DrawerCheckUpdate)
        assertEquals(R.drawable.ic_home_information_circle, HomeIcons.DrawerAbout)
        assertEquals(16, ids.size)
        assertTrue(ids.all { it != 0 })
        assertEquals(ids.size, ids.toSet().size)
    }
}
