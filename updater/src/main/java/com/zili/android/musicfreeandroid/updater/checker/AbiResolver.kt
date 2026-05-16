package com.zili.android.musicfreeandroid.updater.checker

import android.os.Build
import com.zili.android.musicfreeandroid.updater.model.ApkVariant
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo

data class ResolvedUpdate(
    val info: UpdateInfo,
    val abi: String,
    val variant: ApkVariant,
)

class AbiResolver(
    private val supportedAbis: () -> List<String> = { Build.SUPPORTED_ABIS.toList() },
) {
    fun resolve(info: UpdateInfo): ResolvedUpdate? =
        supportedAbis().firstOrNull { it in info.variants }
            ?.let { abi -> ResolvedUpdate(info = info, abi = abi, variant = info.variants.getValue(abi)) }
}
