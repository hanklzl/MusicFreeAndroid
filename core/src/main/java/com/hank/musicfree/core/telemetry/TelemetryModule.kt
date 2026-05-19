package com.hank.musicfree.core.telemetry

import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {
    @Provides
    @Singleton
    fun provideMfLogger(): MfLogger = MfLog
}
