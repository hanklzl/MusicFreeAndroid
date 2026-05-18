package com.hank.musicfree.downloader.harness.contracts

import com.hank.musicfree.core.media.EmptyMediaSourceResolver
import com.hank.musicfree.core.media.MediaSourceResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `MediaSourceResolver` 在生产环境由 `:plugin` 模块绑定。
 * `:downloader` 测试不依赖 `:plugin`，因此用 [EmptyMediaSourceResolver] 占位，
 * 保证 Hilt graph 闭合后契约测试能跑起来。本模块只对单测生效。
 *
 * 注意：此 `@Module @InstallIn` 在 `:downloader` 所有 `@HiltAndroidTest` 中全局生效。
 * 后续若有新增 `@HiltAndroidTest` 需要不同的 `MediaSourceResolver` 实现，必须用
 * `@UninstallModules(DownloaderTestBindings::class)` 卸载，或拆分到独立 `@TestInstallIn`，
 * 否则 Dagger 会抛 DuplicateBindings。
 */
@Module
@InstallIn(SingletonComponent::class)
object DownloaderTestBindings {
    @Provides
    @Singleton
    fun provideMediaSourceResolver(): MediaSourceResolver = EmptyMediaSourceResolver
}
