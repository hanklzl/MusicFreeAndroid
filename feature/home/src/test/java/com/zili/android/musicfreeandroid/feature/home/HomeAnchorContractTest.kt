package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAnchorContractTest {

    @Test
    fun `required home anchors are unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Screen.HomeRoot,
            FidelityAnchors.Home.NavBarRoot,
            FidelityAnchors.Home.NavBarMenu,
            FidelityAnchors.Home.NavBarSearch,
            FidelityAnchors.Home.OperationsRoot,
            FidelityAnchors.Home.OperationsRecommendSheets,
            FidelityAnchors.Home.OperationsTopList,
            FidelityAnchors.Home.OperationsHistory,
            FidelityAnchors.Home.OperationsLocalMusic,
            FidelityAnchors.Home.SheetsRoot,
            FidelityAnchors.Home.SheetsMineTab,
            FidelityAnchors.Home.SheetsStarredTab,
            FidelityAnchors.Home.SheetsCreate,
            FidelityAnchors.Home.SheetsImport,
            FidelityAnchors.Home.DrawerRoot,
            FidelityAnchors.Home.DrawerTitle,
            FidelityAnchors.Home.DrawerSettings,
            FidelityAnchors.Home.DrawerPluginManagement,
            FidelityAnchors.Home.DrawerPermissions,
            FidelityAnchors.Player.MiniRoot,
            FidelityAnchors.Player.MiniPlayPause,
            FidelityAnchors.Player.MiniQueue,
        )

        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }

    @Test
    fun `expanded homepage fidelity anchors stay unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Home.DrawerSettingsBasic,
            FidelityAnchors.Home.DrawerSettingsPlugin,
            FidelityAnchors.Home.DrawerSettingsTheme,
            FidelityAnchors.Home.DrawerOtherScheduleClose,
            FidelityAnchors.Home.DrawerOtherBackup,
            FidelityAnchors.Home.DrawerOtherPermissions,
            FidelityAnchors.Home.DrawerSoftwareLanguage,
            FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
            FidelityAnchors.Home.DrawerSoftwareAbout,
            FidelityAnchors.Home.DrawerActionBackToDesktop,
            FidelityAnchors.Home.DrawerActionExitApp,
            FidelityAnchors.Panel.TimingCloseRoot,
            FidelityAnchors.Dialog.LanguageRoot,
            FidelityAnchors.Dialog.UpdateCheckRoot,
            FidelityAnchors.Settings.PluginManagementEntry,
            FidelityAnchors.Settings.ThemeEntry,
            FidelityAnchors.Settings.BackupEntry,
            FidelityAnchors.Settings.AboutEntry,
        )

        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }
}
