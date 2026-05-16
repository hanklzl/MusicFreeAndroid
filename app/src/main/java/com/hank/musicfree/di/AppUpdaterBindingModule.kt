package com.hank.musicfree.di

import com.hank.musicfree.bootstrap.AppLocalAppVersion
import com.hank.musicfree.updater.bootstrap.LocalAppVersion
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdaterBindingModule {

    @Binds
    @Singleton
    abstract fun bindLocalAppVersion(impl: AppLocalAppVersion): LocalAppVersion
}
