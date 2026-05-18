package com.hank.musicfree.updater.harness.contracts

import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.core.network.NetworkTrafficEventListener
import com.hank.musicfree.updater.di.UpdaterHttp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Contract: `:updater` 的 `@UpdaterHttp` 客户端必须从 `@BaseOkHttp` 派生，
 * 这样升级检查 / APK 下载流量也会经过 [NetworkTrafficEventListener.Factory] 进入流量统计。
 *
 * 如果有人改回 `OkHttpClient.Builder().build()` 直接构造，
 * `eventListenerFactory` 会变成 OkHttp 默认值，本测就会失败。
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [29])
class UpdaterClientContractTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject @field:UpdaterHttp lateinit var updaterClient: OkHttpClient

    @Inject @field:BaseOkHttp lateinit var baseClient: OkHttpClient

    @Test fun updater_client_event_listener_factory_matches_base() {
        hilt.inject()
        assertTrue(updaterClient.eventListenerFactory is NetworkTrafficEventListener.Factory)
        assertSame(baseClient.eventListenerFactory, updaterClient.eventListenerFactory)
    }
}
