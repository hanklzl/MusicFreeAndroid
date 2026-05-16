package com.hank.musicfree.feature.home

import com.hank.musicfree.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDrawerUiModelTest {

    @Test
    fun `me section is first and contains listen stats item`() {
        val model = buildHomeDrawerUiModel(currentVersion = "1.0", scheduleCloseSummary = "")
        val first = model.sections.first()
        assertEquals("me", first.sectionKey)
        assertEquals("我的", first.title)
        val item = first.items.single()
        assertEquals("听歌足迹", item.title)
        assertEquals(HomeDrawerAction.OpenListenStats, item.action)
        assertEquals(
            FidelityAnchors.Home.DrawerMeListenStats,
            item.anchorTag,
        )
    }

    @Test
    fun `drawer ui model preserves spec section order and trailing text`() {
        val model = buildHomeDrawerUiModel(
            currentVersion = "1.0",
            scheduleCloseSummary = "",
        )

        assertEquals(
            listOf("me", "setting", "other", "software"),
            model.sections.map { it.sectionKey },
        )
        val softwareSection = model.sections.first { it.sectionKey == "software" }
        assertEquals(
            listOf(
                FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
                FidelityAnchors.Home.DrawerSoftwareAbout,
            ),
            softwareSection.items.map { it.anchorTag },
        )
        assertEquals(
            "1.0",
            softwareSection.items.first {
                it.anchorTag == FidelityAnchors.Home.DrawerSoftwareCheckUpdate
            }.trailingText,
        )
        assertEquals(emptyList<String>(), model.footerActions.map { it.anchorTag })
    }
}
