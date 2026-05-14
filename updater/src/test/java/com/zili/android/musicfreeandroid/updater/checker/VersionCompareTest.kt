package com.zili.android.musicfreeandroid.updater.checker

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `prefers versionCode comparison`() {
        assertEquals(
            VersionCompare.Outcome.NewerAvailable,
            VersionCompare.compare(
                localCode = 10000, localName = "1.0.0",
                remoteCode = 10203, remoteName = "1.2.3",
            ),
        )
    }

    @Test
    fun `equal versionCode is up to date`() {
        assertEquals(
            VersionCompare.Outcome.UpToDate,
            VersionCompare.compare(
                localCode = 10203, localName = "1.2.3",
                remoteCode = 10203, remoteName = "1.2.3",
            ),
        )
    }

    @Test
    fun `remote behind local is up to date`() {
        assertEquals(
            VersionCompare.Outcome.UpToDate,
            VersionCompare.compare(
                localCode = 10204, localName = "1.2.4",
                remoteCode = 10203, remoteName = "1.2.3",
            ),
        )
    }

    @Test
    fun `falls back to semver when remote versionCode is zero`() {
        assertEquals(
            VersionCompare.Outcome.NewerAvailable,
            VersionCompare.compare(
                localCode = 10000, localName = "1.0.0",
                remoteCode = 0, remoteName = "1.0.1",
            ),
        )
    }

    @Test
    fun `unparseable semver triggers unsupported`() {
        assertEquals(
            VersionCompare.Outcome.Unsupported,
            VersionCompare.compare(
                localCode = 0, localName = "x.y.z",
                remoteCode = 0, remoteName = "garbage",
            ),
        )
    }
}
