package com.zili.android.musicfreeandroid.core.ui

object FidelityAnchors {
    object Screen {
        const val HomeRoot = "screen.home.root"
        const val SearchRoot = "screen.search.root"
        const val RecommendSheetsRoot = "screen.recommendSheets.root"
        const val TopListRoot = "screen.topList.root"
        const val HistoryRoot = "screen.history.root"
        const val SettingsRoot = "screen.settings.root"
        const val PluginListRoot = "screen.pluginList.root"
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
        const val DrawerTitle = "home.drawer.title"
        const val DrawerMeListenStats = "home.drawer.me.listenStats"
        const val DrawerSettings = "home.drawer.settings"
        const val DrawerPluginManagement = "home.drawer.pluginManagement"
        const val DrawerPermissions = "home.drawer.permissions"
        const val DrawerSettingsBasic = "home.drawer.settings.basic"
        const val DrawerSettingsPlugin = "home.drawer.settings.plugin"
        const val DrawerSettingsTheme = "home.drawer.settings.theme"
        const val DrawerOtherScheduleClose = "home.drawer.other.scheduleClose"
        const val DrawerOtherBackup = "home.drawer.other.backup"
        const val DrawerOtherPermissions = "home.drawer.other.permissions"
        const val DrawerSoftwareCheckUpdate = "home.drawer.software.checkUpdate"
        const val DrawerSoftwareAbout = "home.drawer.software.about"
    }

    object Search {
        const val Input = "search.input"
        const val ResultMusicRow = "search.result.musicRow"
        const val ResultMediaRow = "search.result.mediaRow"
        const val ResultSheetItem = "search.result.sheetItem"
    }

    object SearchMusicList {
        const val Input = "searchMusicList.input"
    }

    object RecommendSheets {
        const val Item = "recommendSheets.item"
    }

    object TopList {
        const val Item = "topList.item"
    }

    object Player {
        const val FullscreenRoot = "player.fullscreen.root"
        const val MiniRoot = "player.mini.root"
        const val MiniPlayPause = "player.mini.playPause"
        const val MiniQueue = "player.mini.queue"

        object Queue {
            const val SheetRoot = "player.queue.root"
            const val RepeatModeButton = "player.queue.repeatMode"
            const val ClearButton = "player.queue.clear"
            const val EmptyState = "player.queue.empty"
            const val Row = "player.queue.row"
            const val CurrentMarker = "player.queue.currentMarker"
            const val RemoveButton = "player.queue.removeButton"
        }
    }

    object Panel {
        const val TimingCloseRoot = "panel.timingClose.root"
    }

    object Dialog {
        const val UpdateCheckRoot = "dialog.updateCheck.root"
    }

    object Settings {
        const val BasicRoot = "settings.basic.root"
        const val BasicSectionCommon = "settings.basic.section.common"
        const val BasicSectionSheetAlbum = "settings.basic.section.sheetAlbum"
        const val BasicSectionPlugin = "settings.basic.section.plugin"
        const val BasicSectionPlayback = "settings.basic.section.playback"
        const val BasicSectionDownload = "settings.basic.section.download"
        const val BasicSectionNetwork = "settings.basic.section.network"
        const val BasicSectionLyric = "settings.basic.section.lyric"
        const val BasicSectionCache = "settings.basic.section.cache"
        const val BasicSectionDeveloper = "settings.basic.section.developer"
        const val BasicMaxSearchHistoryLength = "settings.basic.maxSearchHistoryLength"
        const val BasicMusicDetailDefaultPage = "settings.basic.musicDetailDefaultPage"
        const val BasicMusicDetailAwake = "settings.basic.musicDetailAwake"
        const val BasicClickMusicInSearch = "settings.basic.clickMusicInSearch"
        const val BasicClickMusicInAlbum = "settings.basic.clickMusicInAlbum"
        const val BasicMusicOrderInLocalSheet = "settings.basic.musicOrderInLocalSheet"
        const val BasicDefaultPlayQuality = "settings.basic.defaultPlayQuality"
        const val BasicPlayQualityOrder = "settings.basic.playQualityOrder"
        const val BasicMaxDownload = "settings.basic.maxDownload"
        const val BasicDefaultDownloadQuality = "settings.basic.defaultDownloadQuality"
        const val BasicDownloadQualityOrder = "settings.basic.downloadQualityOrder"
        const val BasicUseCellularPlay = "settings.basic.useCellularPlay"
        const val BasicUseCellularDownload = "settings.basic.useCellularDownload"
        const val BasicLyricAutoSearch = "settings.basic.lyricAutoSearch"
        const val ThemeRoot = "settings.theme.root"
        const val BackupRoot = "settings.backup.root"
        const val AboutRoot = "settings.about.root"
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
