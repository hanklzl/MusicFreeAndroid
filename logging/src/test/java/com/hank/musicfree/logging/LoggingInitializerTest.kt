package com.hank.musicfree.logging

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class LoggingInitializerTest {
    @Test
    fun `install uncaught exception handler is idempotent`() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val baselineHandler = Thread.UncaughtExceptionHandler { _, _ -> }
        Thread.setDefaultUncaughtExceptionHandler(baselineHandler)

        val method = LoggingInitializer::class.java.getDeclaredMethod("installUncaughtExceptionHandler")
        method.isAccessible = true

        try {
            method.invoke(LoggingInitializer)
            val first = Thread.getDefaultUncaughtExceptionHandler()
            assertNotSame(
                "first installation should wrap the prior handler",
                baselineHandler,
                first,
            )

            method.invoke(LoggingInitializer)
            val second = Thread.getDefaultUncaughtExceptionHandler()
            assertSame("subsequent installation should reuse existing Logging handler", first, second)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
}
