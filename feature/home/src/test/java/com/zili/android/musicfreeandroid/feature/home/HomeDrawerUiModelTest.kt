package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDrawerUiModelTest {

    @Test
    fun `drawer ui model preserves spec section order and trailing text`() {
        val model = buildHomeDrawerUiModel(
            currentLanguage = "English",
            currentVersion = "1.0",
            scheduleCloseSummary = "",
        )

        assertEquals(listOf("setting", "other", "software"), model.sections.map { it.sectionKey })
        assertEquals(
            "English",
            model.sections[2].items.first {
                it.anchorTag == FidelityAnchors.Home.DrawerSoftwareLanguage
            }.trailingText,
        )
        assertEquals(
            "1.0",
            model.sections[2].items.first {
                it.anchorTag == FidelityAnchors.Home.DrawerSoftwareCheckUpdate
            }.trailingText,
        )
        assertEquals(
            listOf(
                FidelityAnchors.Home.DrawerActionBackToDesktop,
                FidelityAnchors.Home.DrawerActionExitApp,
            ),
            model.footerActions.map { it.anchorTag },
        )
    }
}
