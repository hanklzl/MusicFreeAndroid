package com.zili.android.musicfreeandroid.updater.checker

object VersionCompare {

    enum class Outcome { NewerAvailable, UpToDate, Unsupported }

    fun compare(
        localCode: Long,
        localName: String,
        remoteCode: Long,
        remoteName: String,
    ): Outcome {
        if (remoteCode > 0L && localCode > 0L) {
            return when {
                remoteCode > localCode -> Outcome.NewerAvailable
                else -> Outcome.UpToDate
            }
        }
        val localParts = parse(localName) ?: return Outcome.Unsupported
        val remoteParts = parse(remoteName) ?: return Outcome.Unsupported
        return if (compareSemver(remoteParts, localParts) > 0) {
            Outcome.NewerAvailable
        } else {
            Outcome.UpToDate
        }
    }

    private fun parse(name: String): IntArray? {
        val parts = name.split('.', limit = 4)
        if (parts.size < 3) return null
        val nums = IntArray(3)
        for (i in 0 until 3) {
            nums[i] = parts[i].toIntOrNull() ?: return null
        }
        return nums
    }

    private fun compareSemver(a: IntArray, b: IntArray): Int {
        for (i in 0 until 3) {
            val diff = a[i] - b[i]
            if (diff != 0) return diff
        }
        return 0
    }
}
