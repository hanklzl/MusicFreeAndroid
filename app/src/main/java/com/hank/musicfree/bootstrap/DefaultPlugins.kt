package com.hank.musicfree.bootstrap

/**
 * Developer / test fixture: URLs reconciled on every cold start by
 * [DefaultPluginsBootstrapper]. Comment out entries to disable individually,
 * or empty both lists entirely before publishing a release build.
 *
 * This file is the single source of truth for which plugins ship with the
 * debug fixture — see `docs/dev-harness/plugin/rules.md`.
 */
object DefaultPlugins {
    val subscriptionUrls: List<String> = listOf(
        "https://13413.kstore.vip/yuanli/yuanli.json",
    )

    val pluginUrls: List<String> = listOf(
        "https://raw.githubusercontent.com/ThomasBy2025/musicfree/refs/heads/main/plugins/wy.js",
    )
}
