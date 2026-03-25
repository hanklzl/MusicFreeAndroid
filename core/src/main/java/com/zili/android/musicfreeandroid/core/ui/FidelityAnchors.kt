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
    }

    object Settings {
        const val PluginManagementEntry = "settings.pluginManagement.entry"
    }
}

object FidelityAnchorPatterns {
    fun mineSheetItem(playlistId: String) = "home.sheets.item.mine.$playlistId"
    fun starredSheetItem(sheetId: String) = "home.sheets.item.starred.$sheetId"
    fun drawerSection(sectionKey: String) = "home.drawer.section.$sectionKey"
}
