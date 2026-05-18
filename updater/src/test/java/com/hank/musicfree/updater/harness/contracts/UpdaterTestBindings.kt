package com.hank.musicfree.updater.harness.contracts

import com.hank.musicfree.core.network.TrafficSample
import com.hank.musicfree.core.network.TrafficSampleSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `TrafficSampleSink` 在生产环境由 `:data` 模块绑定到 `TrafficSampleSinkImpl`。
 * `:updater` 不依赖 `:data`，所以单测内提供一个 no-op 实现把 Hilt graph 闭合。
 * 契约只关心 `eventListenerFactory` 是不是 base 的同一个引用，sink 本身行为与本测无关。
 *
 * 注意：此 `@Module @InstallIn` 在 `:updater` 所有 `@HiltAndroidTest` 中全局生效。
 * 后续若有新增 `@HiltAndroidTest` 需要不同的 `TrafficSampleSink` 实现，必须用
 * `@UninstallModules(UpdaterTestBindings::class)` 卸载，或拆分到独立 `@TestInstallIn`，
 * 否则 Dagger 会抛 DuplicateBindings。
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdaterTestBindings {
    @Provides
    @Singleton
    fun provideTrafficSampleSink(): TrafficSampleSink = object : TrafficSampleSink {
        override fun offer(sample: TrafficSample) = Unit
    }
}
