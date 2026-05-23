package com.hank.musicfree.logging

object UiLogEvents {
    const val SCREEN_ENTER = "screen_enter"
    const val SCREEN_EXIT = "screen_exit"
    const val TAB_SWITCH = "tab_switch"

    const val UI_CLICK = "ui_click"
    const val DIALOG_OPEN = "dialog_open"
    const val DIALOG_DISMISS = "dialog_dismiss"

    const val APP_BACKGROUND = "app_background"
    const val APP_FOREGROUND = "app_foreground"

    const val ACTIVITY_CREATED = "activity_created"
    const val ACTIVITY_DESTROYED = "activity_destroyed"

    const val STORE_CREATED = "store_created"
    const val STORE_DESTROYED = "store_destroyed"

    const val PLUGIN_ENGINE_INIT = "plugin_engine_init"
    const val PLUGIN_ENGINE_DESTROYED = "plugin_engine_destroyed"

    const val MEDIA_SESSION_STARTED = "media_session_started"
    const val MEDIA_SESSION_DESTROYED = "media_session_destroyed"

    const val PROCESS_START_AFTER_KILL = "process_start_after_kill"

    object Fields {
        const val TARGET_ID = "targetId"
        const val TARGET_LABEL = "targetLabel"
        const val SCREEN = "screen"
        const val ROUTE = "route"
        const val PARAMS = "params"
        const val SOURCE = "source"
        const val FROM = "from"
        const val TO = "to"
        const val DIALOG_ID = "dialogId"
        const val TRIGGER = "trigger"
        const val OUTCOME = "outcome"
        const val DURATION_MS = "durationMs"
        const val LAST_SCREEN = "lastScreen"
        const val RESUME_SCREEN = "resumeScreen"
        const val IS_PLAYING = "isPlaying"
        const val BACKGROUNDED_DURATION_MS = "backgroundedDurationMs"
        const val ACTIVITY = "activity"
        const val HAS_SAVED_STATE = "hasSavedState"
        const val IS_CONFIG_CHANGE = "isConfigChange"
        const val IS_COLD_START = "isColdStart"
        const val IS_FINISHING = "isFinishing"
        const val IS_CHANGING_CONFIG = "isChangingConfigurations"
        const val LIFETIME_MS = "lifetimeMs"
        const val REASON = "reason"
        const val STORE_ID = "storeId"
        const val SCOPE = "scope"
        const val RESTORED_FROM_SNAPSHOT = "restoredFromSnapshot"
        const val SNAPSHOT_KEYS = "snapshotKeys"
        const val PLUGIN_COUNT = "pluginCount"
        const val JS_ENGINE_VERSION = "jsEngineVersion"
        const val RESTORED_QUEUE_SIZE = "restoredQueueSize"
        const val LAST_SONG_ID = "lastSongId"
        const val QUEUE_SIZE = "queueSize"
        const val LAST_BACKGROUNDED_DURATION_MS = "lastBackgroundedDurationMs"
        const val SUSPECTED_REASON = "suspectedReason"
        const val PREVIOUS_SESSION_ID = "previousSessionId"
    }

    object Outcome {
        const val CONFIRM = "confirm"
        const val CANCEL = "cancel"
        const val SYSTEM = "system"
    }

    object Scope {
        const val APP = "app"
        const val ACTIVITY = "activity"
        const val SCREEN = "screen"
    }

    object Trigger {
        const val LIFECYCLE_ON_STOP = "lifecycle_on_stop"
        const val LIFECYCLE_ON_START = "lifecycle_on_start"
        const val UI_CLICK = "ui_click"
        const val SYSTEM = "system"
        const val NAV_LISTENER = "nav_listener"
    }
}
