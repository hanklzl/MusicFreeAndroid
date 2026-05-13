package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDrawerUiModelTest {

    @Test
    fun `drawer ui model preserves spec section order and trailing text`() {
        val model = buildHomeDrawerUiModel(
            currentVersion = "1.0",
            scheduleCloseSummary = "",
        )

        assertEquals(
            listOf("setting", "other", "software"),
            model.sections.map { it.sectionKey },
        )
        assertEquals(
            listOf(
                FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
                FidelityAnchors.Home.DrawerSoftwareAbout,
            ),
            model.sections[2].items.map { it.anchorTag },
        )
        assertEquals(
            "1.0",
            model.sections[2].items.first {
                it.anchorTag == FidelityAnchors.Home.DrawerSoftwareCheckUpdate
            }.trailingText,
        )
        assertEquals(emptyList<String>(), model.footerActions.map { it.anchorTag })
    }
}
