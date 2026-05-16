package com.zili.android.musicfreeandroid.feature.home

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpdateBadgeViewModel @Inject constructor(
    val checker: UpdateChecker,
    val downloader: ApkDownloader,
    val installer: ApkInstaller,
) : ViewModel()
