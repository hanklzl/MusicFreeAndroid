package com.hank.musicfree.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLogEventsTest {
    @Test
    fun `ui and navigation categories have stable wire names`() {
        assertEquals("ui", LogCategory.UI.wireName)
        assertEquals("navigation", LogCategory.NAVIGATION.wireName)
    }

    @Test
    fun `event names are stable snake_case`() {
        assertEquals("screen_enter", UiLogEvents.SCREEN_ENTER)
        assertEquals("screen_exit", UiLogEvents.SCREEN_EXIT)
        assertEquals("tab_switch", UiLogEvents.TAB_SWITCH)
        assertEquals("ui_click", UiLogEvents.UI_CLICK)
        assertEquals("dialog_open", UiLogEvents.DIALOG_OPEN)
        assertEquals("dialog_dismiss", UiLogEvents.DIALOG_DISMISS)
        assertEquals("app_background", UiLogEvents.APP_BACKGROUND)
        assertEquals("app_foreground", UiLogEvents.APP_FOREGROUND)
        assertEquals("activity_created", UiLogEvents.ACTIVITY_CREATED)
        assertEquals("activity_destroyed", UiLogEvents.ACTIVITY_DESTROYED)
        assertEquals("store_created", UiLogEvents.STORE_CREATED)
        assertEquals("store_destroyed", UiLogEvents.STORE_DESTROYED)
        assertEquals("plugin_engine_init", UiLogEvents.PLUGIN_ENGINE_INIT)
        assertEquals("plugin_engine_destroyed", UiLogEvents.PLUGIN_ENGINE_DESTROYED)
        assertEquals("media_session_started", UiLogEvents.MEDIA_SESSION_STARTED)
        assertEquals("media_session_destroyed", UiLogEvents.MEDIA_SESSION_DESTROYED)
        assertEquals("process_start_after_kill", UiLogEvents.PROCESS_START_AFTER_KILL)
    }

    @Test
    fun `outcome and scope constants are stable`() {
        assertEquals("confirm", UiLogEvents.Outcome.CONFIRM)
        assertEquals("cancel", UiLogEvents.Outcome.CANCEL)
        assertEquals("system", UiLogEvents.Outcome.SYSTEM)
        assertEquals("app", UiLogEvents.Scope.APP)
        assertEquals("activity", UiLogEvents.Scope.ACTIVITY)
        assertEquals("screen", UiLogEvents.Scope.SCREEN)
    }

    @Test
    fun `field key names are stable`() {
        assertEquals("targetId", UiLogEvents.Fields.TARGET_ID)
        assertEquals("screen", UiLogEvents.Fields.SCREEN)
        assertEquals("route", UiLogEvents.Fields.ROUTE)
        assertEquals("durationMs", UiLogEvents.Fields.DURATION_MS)
        assertEquals("backgroundedDurationMs", UiLogEvents.Fields.BACKGROUNDED_DURATION_MS)
        assertEquals("storeId", UiLogEvents.Fields.STORE_ID)
        assertEquals("restoredFromSnapshot", UiLogEvents.Fields.RESTORED_FROM_SNAPSHOT)
    }
}
