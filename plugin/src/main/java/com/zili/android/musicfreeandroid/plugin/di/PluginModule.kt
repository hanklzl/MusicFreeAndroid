package com.zili.android.musicfreeandroid.plugin.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the plugin subsystem.
 *
 * [PluginManager] is provided automatically via its @Singleton @Inject constructor.
 * This module exists as a placeholder for future @Provides/@Binds if needed
 * (e.g., providing OkHttpClient or other dependencies to the plugin layer).
 */
@Module
@InstallIn(SingletonComponent::class)
object PluginModule
