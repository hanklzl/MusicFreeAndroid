package com.hank.musicfree.feature.home

import androidx.lifecycle.ViewModel
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.downloader.UpdateDownloadManager
import com.hank.musicfree.updater.installer.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpdateBadgeViewModel @Inject constructor(
    val checker: UpdateChecker,
    val downloadManager: UpdateDownloadManager,
    val installer: ApkInstaller,
) : ViewModel()
