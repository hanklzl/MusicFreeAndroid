package com.zili.android.musicfreeandroid.feature.home

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpdateBadgeViewModel @Inject constructor(
    val checker: UpdateChecker,
) : ViewModel()
