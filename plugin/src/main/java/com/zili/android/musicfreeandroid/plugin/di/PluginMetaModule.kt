package com.zili.android.musicfreeandroid.plugin.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluginMetaDataStore

private val Context.pluginMetaDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "plugin_meta")

@Module
@InstallIn(SingletonComponent::class)
object PluginMetaModule {

    @Provides
    @Singleton
    @PluginMetaDataStore
    fun providePluginMetaDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.pluginMetaDataStore
}
