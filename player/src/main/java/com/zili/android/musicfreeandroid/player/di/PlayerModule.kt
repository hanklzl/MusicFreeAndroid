package com.zili.android.musicfreeandroid.player.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    // PlayerController is provided via @Inject constructor.
    // Additional player-related bindings can be added here in the future.
}
