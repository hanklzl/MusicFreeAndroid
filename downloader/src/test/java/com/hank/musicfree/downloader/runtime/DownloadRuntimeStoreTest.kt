package com.hank.musicfree.downloader.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.downloader.engine.DownloadEngine
import com.hank.musicfree.downloader.model.DownloadStatus
import com.hank.musicfree.downloader.model.DownloadTaskUi
import com.hank.musicfree.downloader.model.MediaKey
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DownloadRuntimeStoreTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun restoreLoadsTaskIndexWithoutStartingDownloadsSynchronously() = runTest {
        val engine = mockk<DownloadEngine>(relaxed = true)
        val startedNetworkCalls = AtomicInteger(0)
        every { engine.start() } answers {
            startedNetworkCalls.incrementAndGet()
        }

        coEvery {
            engine.taskSnapshot()
        } returns listOf(
            DownloadTaskUi(
                key = MediaKey.of("task-1", "qq"),
                title = "title",
                artist = "artist",
                artwork = null,
                status = DownloadStatus.DOWNLOADING,
                targetQuality = "standard",
                downloadedBytes = 0L,
                totalBytes = 10L,
                errorReason = null,
            ),
        )

        val store = DownloadRuntimeStore(engine)
        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Restored, result)
        assertEquals(listOf("task-1@qq"), store.state.value.taskIds)
        assertEquals(1, store.state.value.activeCount)
        assertEquals(0, store.state.value.failedCount)
        assertEquals(false, store.state.value.restoring)
        assertEquals(0, startedNetworkCalls.get())
        coVerify(exactly = 0) { engine.start() }
    }

    @Test
    fun restoreEmptyTasksReturnsSkippedAndKeepsRestoringFalse() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)

        val engine = mockk<DownloadEngine>(relaxed = true)
        val startedNetworkCalls = AtomicInteger(0)
        every { engine.start() } answers {
            startedNetworkCalls.incrementAndGet()
        }
        coEvery { engine.taskSnapshot() } returns emptyList()

        val store = DownloadRuntimeStore(engine)
        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Skipped("empty_download_tasks"), result)
        assertEquals(emptyList<String>(), store.state.value.taskIds)
        assertEquals(0, store.state.value.activeCount)
        assertEquals(0, store.state.value.failedCount)
        assertEquals(false, store.state.value.restoring)
        assertEquals(null, store.state.value.lastFailureReason)
        assertEquals(0, startedNetworkCalls.get())
        coVerify(exactly = 0) { engine.start() }

        val event = logger.events.first { it.event == "download_runtime_restore_skipped" }
        assertEquals(LogCategory.DOWNLOAD, event.category)
        assertEquals("download_runtime", event.fields["store"])
        assertEquals("runtime_restore", event.fields["operation"])
        assertEquals("download_runtime:current", event.fields["key"])
        assertEquals("skipped", event.fields["result"])
    }

    @Test
    fun restoreFromFailureLogsErrorAndReturnsFailed() = runTest {
        val error = IllegalStateException("snapshot failed")
        val logger = RecordingLogger()
        MfLog.install(logger)

        val engine = mockk<DownloadEngine>(relaxed = true)
        every { engine.start() } answers {}
        coEvery { engine.taskSnapshot() } throws error

        val store = DownloadRuntimeStore(engine)

        val result = store.restore()

        val failed = result as? RuntimeRestoreResult.Failed
        assertNotNull(failed)
        assertEquals("exception", failed?.reason)
        assertEquals("exception", store.state.value.lastFailureReason)
        assertEquals(false, store.state.value.restoring)

        val event = logger.events.first { it.event == "download_runtime_restore_failed" }
        assertEquals(LogCategory.DOWNLOAD, event.category)
        assertEquals("failure", event.fields["result"])
        assertEquals("exception", event.fields["reason"])
        assertEquals("download_runtime", event.fields["store"])
        assertEquals("runtime_restore", event.fields["operation"])
        assertEquals("download_runtime:current", event.fields["key"])
    }

    @Test
    fun restoreSuccessIncludesCountAndActiveFailedStatisticsInLogFields() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)

        val engine = mockk<DownloadEngine>(relaxed = true)
        coEvery {
            engine.taskSnapshot()
        } returns listOf(
            downloadTask(status = DownloadStatus.PENDING, id = "pending-1", platform = "qq"),
            downloadTask(status = DownloadStatus.PREPARING, id = "preparing-1", platform = "qq"),
            downloadTask(status = DownloadStatus.DOWNLOADING, id = "downloading-1", platform = "qq"),
            downloadTask(status = DownloadStatus.FAILED, id = "failed-1", platform = "qq"),
        )

        val store = DownloadRuntimeStore(engine)
        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Restored, result)
        assertEquals(2, store.state.value.activeCount)
        assertEquals(1, store.state.value.failedCount)

        val event = logger.events.first { it.event == "download_runtime_restore_success" }
        assertEquals(LogCategory.DOWNLOAD, event.category)
        assertEquals("download_runtime", event.fields["store"])
        assertEquals("runtime_restore", event.fields["operation"])
        assertEquals("download_runtime:current", event.fields["key"])
        assertEquals(4, event.fields["count"])
        assertEquals(2, event.fields["activeCount"])
        assertEquals(1, event.fields["failedCount"])
        assertEquals("success", event.fields["result"])
        assertEquals(true, event.fields.containsKey("durationMs"))
    }

    @Test
    fun downloadRuntimeStoreConstructorOnlyDependsOnDownloadEngine() {
        val ctor = DownloadRuntimeStore::class.java.constructors.single()
        assertEquals(1, ctor.parameterTypes.size)
        assertEquals(DownloadEngine::class.java, ctor.parameterTypes.single())
    }

    @Test
    fun downloadRuntimeModuleBindsStoreToRuntimeStoreSet() {
        val method = DownloadRuntimeModule::class.java.declaredMethods.single { it.name == "bindDownloadRuntimeStore" }
        assertEquals(1, method.parameterTypes.size)
        assertEquals(DownloadRuntimeStore::class.java, method.parameterTypes.single())
    }

    private fun downloadTask(id: String, platform: String, status: DownloadStatus): DownloadTaskUi =
        DownloadTaskUi(
            key = MediaKey.of(id, platform),
            title = "title-$id",
            artist = "artist",
            artwork = null,
            status = status,
            targetQuality = "standard",
            downloadedBytes = null,
            totalBytes = null,
            errorReason = null,
        )

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = CopyOnWriteArrayList<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(category, event, fields + ("throwable" to throwable))
        }

        override fun flush() = Unit
    }
}
