package com.zili.android.musicfreeandroid.feature.home

import androidx.annotation.DrawableRes
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.component.HomeIcons

@Suppress("TooGenericExceptionCaught")
suspend fun runHomeDrawerNavigation(
    navigate: () -> Unit,
    closeDrawer: suspend () -> Unit,
) {
    navigate()
    runCatching {
        closeDrawer()
    }
}

sealed interface HomeDrawerAction {
    data object OpenSettingsRoot : HomeDrawerAction
    data object OpenPluginManagement : HomeDrawerAction
    data object OpenThemeSettings : HomeDrawerAction
    data object ShowScheduleClosePanel : HomeDrawerAction
    data object OpenBackup : HomeDrawerAction
    data object OpenPermissions : HomeDrawerAction
    data object ShowLanguageDialog : HomeDrawerAction
    data object ShowUpdateCheckDialog : HomeDrawerAction
    data object OpenAbout : HomeDrawerAction
    data object BackToDesktop : HomeDrawerAction
    data object ExitApp : HomeDrawerAction
}

data class HomeDrawerUiModel(
    val sections: List<HomeDrawerSectionUiModel>,
    val footerActions: List<HomeDrawerItemUiModel>,
)

data class HomeDrawerSectionUiModel(
    val sectionKey: String,
    val title: String,
    val items: List<HomeDrawerItemUiModel>,
)

data class HomeDrawerItemUiModel(
    val title: String,
    @param:DrawableRes
    @field:DrawableRes
    val iconRes: Int,
    val anchorTag: String,
    val trailingText: String? = null,
    val action: HomeDrawerAction,
)

fun buildHomeDrawerUiModel(
    currentLanguage: String,
    currentVersion: String,
    scheduleCloseSummary: String,
): HomeDrawerUiModel = HomeDrawerUiModel(
    sections = listOf(
        HomeDrawerSectionUiModel(
            sectionKey = "setting",
            title = "设置",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "基础设置",
                    iconRes = HomeIcons.DrawerSettings,
                    anchorTag = FidelityAnchors.Home.DrawerSettingsBasic,
                    action = HomeDrawerAction.OpenSettingsRoot,
                ),
                HomeDrawerItemUiModel(
                    title = "插件管理",
                    iconRes = HomeIcons.DrawerPluginManagement,
                    anchorTag = FidelityAnchors.Home.DrawerSettingsPlugin,
                    action = HomeDrawerAction.OpenPluginManagement,
                ),
                HomeDrawerItemUiModel(
                    title = "主题设置",
                    iconRes = HomeIcons.DrawerTheme,
                    anchorTag = FidelityAnchors.Home.DrawerSettingsTheme,
                    action = HomeDrawerAction.OpenThemeSettings,
                ),
            ),
        ),
        HomeDrawerSectionUiModel(
            sectionKey = "other",
            title = "其他",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "定时关闭",
                    iconRes = HomeIcons.DrawerScheduleClose,
                    anchorTag = FidelityAnchors.Home.DrawerOtherScheduleClose,
                    trailingText = scheduleCloseSummary.ifBlank { null },
                    action = HomeDrawerAction.ShowScheduleClosePanel,
                ),
                HomeDrawerItemUiModel(
                    title = "备份与恢复",
                    iconRes = HomeIcons.DrawerBackup,
                    anchorTag = FidelityAnchors.Home.DrawerOtherBackup,
                    action = HomeDrawerAction.OpenBackup,
                ),
                HomeDrawerItemUiModel(
                    title = "权限管理",
                    iconRes = HomeIcons.DrawerPermissions,
                    anchorTag = FidelityAnchors.Home.DrawerOtherPermissions,
                    action = HomeDrawerAction.OpenPermissions,
                ),
            ),
        ),
        HomeDrawerSectionUiModel(
            sectionKey = "software",
            title = "软件",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "语言设置",
                    iconRes = HomeIcons.DrawerLanguage,
                    anchorTag = FidelityAnchors.Home.DrawerSoftwareLanguage,
                    trailingText = currentLanguage,
                    action = HomeDrawerAction.ShowLanguageDialog,
                ),
                HomeDrawerItemUiModel(
                    title = "检查更新",
                    iconRes = HomeIcons.DrawerCheckUpdate,
                    anchorTag = FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
                    trailingText = currentVersion,
                    action = HomeDrawerAction.ShowUpdateCheckDialog,
                ),
                HomeDrawerItemUiModel(
                    title = "关于 MusicFree",
                    iconRes = HomeIcons.DrawerAbout,
                    anchorTag = FidelityAnchors.Home.DrawerSoftwareAbout,
                    action = HomeDrawerAction.OpenAbout,
                ),
            ),
        ),
    ),
    footerActions = listOf(
        HomeDrawerItemUiModel(
            title = "回到桌面",
            iconRes = HomeIcons.DrawerBackToDesktop,
            anchorTag = FidelityAnchors.Home.DrawerActionBackToDesktop,
            action = HomeDrawerAction.BackToDesktop,
        ),
        HomeDrawerItemUiModel(
            title = "退出软件",
            iconRes = HomeIcons.DrawerExitApp,
            anchorTag = FidelityAnchors.Home.DrawerActionExitApp,
            action = HomeDrawerAction.ExitApp,
        ),
    ),
)
