package com.zili.android.musicfreeandroid.di

import com.zili.android.musicfreeandroid.bootstrap.AppLocalAppVersion
import com.zili.android.musicfreeandroid.updater.bootstrap.LocalAppVersion
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
