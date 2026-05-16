package com.hank.musicfree.data.di

import com.hank.musicfree.core.theme.runtime.ThemeRepository
import com.hank.musicfree.data.repository.theme.DefaultThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for [ThemeRepository]. Lives in its own module so the existing
 * `DataModule` (an `object`) doesn't have to migrate to an `abstract class`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: DefaultThemeRepository): ThemeRepository
}
