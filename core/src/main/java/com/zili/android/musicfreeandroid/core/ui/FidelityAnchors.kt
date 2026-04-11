package com.zili.android.musicfreeandroid.core.ui

object FidelityAnchors {
    object Screen {
        const val HomeRoot = "screen.home.root"
        const val SearchRoot = "screen.search.root"
        const val RecommendSheetsRoot = "screen.recommendSheets.root"
        const val TopListRoot = "screen.topList.root"
        const val HistoryRoot = "screen.history.root"
        const val SettingsRoot = "screen.settings.root"
        const val PermissionsRoot = "screen.permissions.root"
        const val LocalRoot = "screen.local.root"
    }

    object Home {
        const val NavBarRoot = "home.navBar.root"
        const val NavBarMenu = "home.navBar.menu"
        const val NavBarSearch = "home.navBar.search"
        const val OperationsRoot = "home.operations.root"
        const val OperationsRecommendSheets = "home.operations.recommendSheets"
        const val OperationsTopList = "home.operations.topList"
        const val OperationsHistory = "home.operations.history"
        const val OperationsLocalMusic = "home.operations.localMusic"
        const val SheetsRoot = "home.sheets.root"
        const val SheetsMineTab = "home.sheets.tab.mine"
        const val SheetsStarredTab = "home.sheets.tab.starred"
        const val SheetsCreate = "home.sheets.action.create"
        const val SheetsImport = "home.sheets.action.import"
        const val DrawerRoot = "home.drawer.root"
        const val DrawerSettings = "home.drawer.settings"
        const val DrawerPluginManagement = "home.drawer.pluginManagement"
        const val DrawerPermissions = "home.drawer.permissions"
        const val DrawerSettingsBasic = "home.drawer.settings.basic"
        const val DrawerSettingsPlugin = "home.drawer.settings.plugin"
        const val DrawerSettingsTheme = "home.drawer.settings.theme"
        const val DrawerOtherScheduleClose = "home.drawer.other.scheduleClose"
        const val DrawerOtherBackup = "home.drawer.other.backup"
        const val DrawerOtherPermissions = "home.drawer.other.permissions"
        const val DrawerSoftwareLanguage = "home.drawer.software.language"
        const val DrawerSoftwareCheckUpdate = "home.drawer.software.checkUpdate"
        const val DrawerSoftwareAbout = "home.drawer.software.about"
        const val DrawerActionBackToDesktop = "home.drawer.action.backToDesktop"
        const val DrawerActionExitApp = "home.drawer.action.exitApp"
    }

    object Player {
        const val MiniRoot = "player.mini.root"
        const val MiniPlayPause = "player.mini.playPause"
        const val MiniQueue = "player.mini.queue"
    }

    object Panel {
        const val TimingCloseRoot = "panel.timingClose.root"
    }

    object Dialog {
        const val LanguageRoot = "dialog.language.root"
        const val UpdateCheckRoot = "dialog.updateCheck.root"
    }

    object Settings {
        const val PluginManagementEntry = "settings.pluginManagement.entry"
        const val ThemeEntry = "settings.theme.entry"
        const val BackupEntry = "settings.backup.entry"
        const val AboutEntry = "settings.about.entry"
    }
}

object FidelityAnchorPatterns {
    fun mineSheetItem(playlistId: String) = "home.sheets.item.mine.$playlistId"
    fun starredSheetItem(sheetId: String) = "home.sheets.item.starred.$sheetId"
    fun drawerSection(sectionKey: String) = "home.drawer.section.$sectionKey"
}
