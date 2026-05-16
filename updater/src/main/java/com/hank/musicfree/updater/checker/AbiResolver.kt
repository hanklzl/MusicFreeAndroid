package com.hank.musicfree.updater.checker

import android.os.Build
import com.hank.musicfree.updater.model.ApkVariant
import com.hank.musicfree.updater.model.UpdateInfo

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
