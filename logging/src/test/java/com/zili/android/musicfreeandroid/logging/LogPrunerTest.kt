package com.zili.android.musicfreeandroid.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LogPrunerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `prune deletes files older than retention window`() {
        val logDir = tmp.newFolder("logan")
        val old = logDir.resolve("2026-04-20.log").apply { writeText("old") }
        val fresh = logDir.resolve("2026-05-05.log").apply { writeText("fresh") }
        old.setLastModified(Instant.parse("2026-04-20T00:00:00Z").toEpochMilli())
        fresh.setLastModified(Instant.parse("2026-05-05T00:00:00Z").toEpochMilli())

        LogPruner.prune(
            logDir = logDir,
            retentionDays = 7,
            maxTotalBytes = 50L * 1024L * 1024L,
            clock = Clock.fixed(Instant.parse("2026-05-05T00:00:00Z"), ZoneOffset.UTC),
        )

        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `prune deletes oldest files until total size under limit`() {
        val logDir = tmp.newFolder("logan")
        val old = logDir.resolve("oldest.log").apply { writeText("12345") }
        val middle = logDir.resolve("middle.log").apply { writeText("12345") }
        val newest = logDir.resolve("newest.log").apply { writeText("12") }
        old.setLastModified(Instant.parse("2026-05-01T00:00:00Z").toEpochMilli())
        middle.setLastModified(Instant.parse("2026-05-03T00:00:00Z").toEpochMilli())
        newest.setLastModified(Instant.parse("2026-05-04T00:00:00Z").toEpochMilli())

        LogPruner.prune(
            logDir = logDir,
            retentionDays = 30,
            maxTotalBytes = 8L,
            clock = Clock.fixed(Instant.parse("2026-05-05T00:00:00Z"), ZoneOffset.UTC),
        )

        assertFalse(old.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
    }
}
