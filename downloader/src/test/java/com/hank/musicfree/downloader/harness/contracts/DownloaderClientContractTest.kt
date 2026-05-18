package com.hank.musicfree.downloader.harness.contracts

import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.core.network.NetworkTrafficEventListener
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
 * Contract: `:downloader` 的 OkHttpClient 必须从 `@BaseOkHttp` 派生，
 * 这样所有下载流量都会经过 [NetworkTrafficEventListener.Factory] 计入 traffic_daily。
 *
 * 这条契约一旦回归（例如有人新写 `OkHttpClient.Builder().build()` 而不是从 base 派生），
 * 这个测试就会失败 —— `eventListenerFactory` 会变成 OkHttp 默认的内部 factory。
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [29])
class DownloaderClientContractTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var downloaderClient: OkHttpClient

    @Inject @field:BaseOkHttp lateinit var baseClient: OkHttpClient

    @Test fun downloader_client_event_listener_factory_matches_base() {
        hilt.inject()
        assertTrue(downloaderClient.eventListenerFactory is NetworkTrafficEventListener.Factory)
        assertSame(baseClient.eventListenerFactory, downloaderClient.eventListenerFactory)
    }
}
