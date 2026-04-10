package com.zili.android.musicfreeandroid.feature.home.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIconMappingTest {

    @Test
    fun `home icon mapping covers every required RN icon`() {
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
            HomeIcons.DrawerLanguage,
            HomeIcons.DrawerCheckUpdate,
            HomeIcons.DrawerAbout,
            HomeIcons.DrawerBackToDesktop,
            HomeIcons.DrawerExitApp,
        )

        assertEquals(19, ids.size)
        assertTrue(ids.all { it != 0 })
    }
}
