package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CheckUpdateViewModel @Inject constructor(
    val checker: UpdateChecker,
) : ViewModel() {
    fun checkNow() {
        checker.checkManually()
    }
}
