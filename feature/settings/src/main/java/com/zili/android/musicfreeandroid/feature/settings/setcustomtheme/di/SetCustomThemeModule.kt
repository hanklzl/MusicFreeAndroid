package com.zili.android.musicfreeandroid.feature.settings.setcustomtheme.di

import com.zili.android.musicfreeandroid.feature.settings.setcustomtheme.DefaultImageAndPaletteLoader
import com.zili.android.musicfreeandroid.feature.settings.setcustomtheme.ImageAndPaletteLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Hilt does not honour the Kotlin default value on
// `SetCustomThemeViewModel(... loader = DefaultImageAndPaletteLoader)`. The
// generated component still requires a binding, so provide the object instance
// here. Tests construct the ViewModel manually with a fake loader and bypass
// this module entirely.
@Module
@InstallIn(SingletonComponent::class)
object SetCustomThemeModule {

    @Provides
    @Singleton
    fun provideImageAndPaletteLoader(): ImageAndPaletteLoader = DefaultImageAndPaletteLoader
}
