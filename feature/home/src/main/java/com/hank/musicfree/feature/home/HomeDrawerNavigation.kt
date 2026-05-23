package com.hank.musicfree.feature.home

import androidx.annotation.DrawableRes
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.home.component.HomeIcons

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
    data object OpenListenStats : HomeDrawerAction
    data object OpenTrafficStats : HomeDrawerAction
    data object OpenSettingsRoot : HomeDrawerAction
    data object OpenPluginManagement : HomeDrawerAction
    data object OpenThemeSettings : HomeDrawerAction
    data object ShowScheduleClosePanel : HomeDrawerAction
    data object OpenBackup : HomeDrawerAction
    data object OpenPermissions : HomeDrawerAction
    data object TriggerManualUpdateCheck : HomeDrawerAction
    data object OpenFeedback : HomeDrawerAction
    data object OpenAbout : HomeDrawerAction
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
    val hasBadge: Boolean = false,
    val action: HomeDrawerAction,
)

fun buildHomeDrawerUiModel(
    currentVersion: String,
    scheduleCloseSummary: String,
    updateTrailingText: String = currentVersion,
    hasUpdateBadge: Boolean = false,
): HomeDrawerUiModel = HomeDrawerUiModel(
    sections = listOf(
        HomeDrawerSectionUiModel(
            sectionKey = "me",
            title = "我的",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "听歌足迹",
                    iconRes = HomeIcons.DrawerListenStats,
                    anchorTag = FidelityAnchors.Home.DrawerMeListenStats,
                    action = HomeDrawerAction.OpenListenStats,
                ),
                HomeDrawerItemUiModel(
                    title = "流量统计",
                    iconRes = HomeIcons.DrawerTrafficStats,
                    anchorTag = FidelityAnchors.Home.DrawerMeTrafficStats,
                    action = HomeDrawerAction.OpenTrafficStats,
                ),
            ),
        ),
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
                    title = "检查更新",
                    iconRes = HomeIcons.DrawerCheckUpdate,
                    anchorTag = FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
                    trailingText = updateTrailingText,
                    hasBadge = hasUpdateBadge,
                    action = HomeDrawerAction.TriggerManualUpdateCheck,
                ),
                HomeDrawerItemUiModel(
                    title = "用户反馈",
                    iconRes = HomeIcons.DrawerFeedback,
                    anchorTag = FidelityAnchors.Home.DrawerSoftwareFeedback,
                    action = HomeDrawerAction.OpenFeedback,
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
    footerActions = emptyList(),
)
