package com.hank.musicfree.plugin.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.repository.CachedPluginMetadata
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PluginRuntimeStoreTest {
    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun restoreMetadataPublishesPluginIndexWithoutEvaluatingQuickJs() = runTest {
        val ctorParams = PluginRuntimeStore::class.java.constructors.single().parameterTypes
        assertEquals(2, ctorParams.size)
        assertFalse(
            "PluginRuntimeStore constructor should not accept PluginManager dependency",
            ctorParams.any { it.name == "com.hank.musicfree.plugin.manager.PluginManager" },
        )
        assertFalse(
            "PluginRuntimeStore constructor should not accept runtime engine dependency",
            ctorParams.any { it.name == "com.hank.musicfree.plugin.engine.JsEngine" },
        )

        val logger = RecordingLogger()
        MfLog.install(logger)

        val gateway = InMemoryMetadataGateway(
            rows = listOf(
                CachedPluginMetadata(
                    filePath = "/tmp/demo-plugin.js",
                    platform = "demo",
                    version = "1.2.3",
                    hash = "hash",
                    srcUrl = null,
                    appVersion = "1.0.0",
                    supportedMethods = setOf("search"),
                    supportedSearchTypes = emptyList(),
                    userVariableKeys = emptyList(),
                    sourceMtimeMs = 0L,
                    cachedAtAppVersion = "1.0.0",
                ),
            ),
        )
        val store = PluginRuntimeStore(
            metadataCacheGateway = gateway,
            snapshotStore = InMemorySnapshotStore(),
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Restored, result)
        assertEquals(
            listOf(
                PluginRuntimeEntry(
                    platform = "demo",
                    version = "1.2.3",
                    filePath = "/tmp/demo-plugin.js",
                    loaded = false,
                    failedReason = null,
                ),
            ),
            store.state.value.plugins,
        )
        assertFalse(store.state.value.restoring)
        assertEquals(
            RuntimeStoreKey.singleton("plugin_runtime").value,
            RuntimeStoreKey.singleton(store.storeName).value,
        )

        val event = logger.events.single { it.event == "plugin_runtime_restore_success" }
        assertEquals(LogCategory.PLUGIN, event.category)
        assertEquals("success", event.fields["result"])
        assertEquals("plugin_runtime", event.fields["store"])
        assertEquals("plugin_runtime:current", event.fields["key"])
    }

    @Test
    fun restoreEmptyMetadataReturnsSkippedAndClearsState() = runTest {
        val store = PluginRuntimeStore(
            metadataCacheGateway = InMemoryMetadataGateway(),
            snapshotStore = InMemorySnapshotStore(),
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Skipped("empty_plugin_metadata"), result)
        assertTrue(store.state.value.plugins.isEmpty())
        assertEquals("empty_plugin_metadata", (result as RuntimeRestoreResult.Skipped).reason)
        assertFalse(store.state.value.restoring)
        assertEquals(null, store.state.value.lastFailureReason)
    }

    @Test
    fun restoreGatewayFailureReturnsFailedAndLogsReasonedError() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val gateway = InMemoryMetadataGateway(throwOnGetAll = IllegalStateException("boom"))

        val store = PluginRuntimeStore(
            metadataCacheGateway = gateway,
            snapshotStore = InMemorySnapshotStore(),
        )

        val result = store.restore()

        val failed = result as RuntimeRestoreResult.Failed
        assertEquals("metadata_restore_failed", failed.reason)
        assertEquals("metadata_restore_failed", store.state.value.lastFailureReason)
        assertFalse(store.state.value.restoring)
        assertEquals(0, store.state.value.plugins.size)

        val event = logger.events.single { it.event == "plugin_runtime_restore_failed" }
        assertEquals(LogCategory.PLUGIN, event.category)
        assertEquals("failure", event.fields["result"])
        assertEquals("metadata_restore_failed", event.fields["reason"])
        assertEquals("plugin_runtime", event.fields["store"])
        assertEquals("plugin_runtime:current", event.fields["key"])
    }

    @Test
    fun pruneDelegatesToSnapshotStoreDeleteExpiredOnly() = runTest {
        val snapshotStore = InMemorySnapshotStore()
        val store = PluginRuntimeStore(
            metadataCacheGateway = InMemoryMetadataGateway(),
            snapshotStore = snapshotStore,
        )

        store.prune(1_234L)

        val event = snapshotStore.deleteExpiredCalls.single()
        assertEquals("plugin_runtime", event.first)
        assertEquals(1_234L, event.second)
        assertFalse(snapshotStore.pruneNamespaceCalled)
    }

    private class InMemoryMetadataGateway(
        private val rows: List<CachedPluginMetadata> = emptyList(),
        private val throwOnGetAll: Throwable? = null,
    ) : PluginMetadataCacheGateway {
        override suspend fun getAll(): List<CachedPluginMetadata> {
            throwOnGetAll?.let { throw it }
            return rows
        }

        override suspend fun getByPath(filePath: String): CachedPluginMetadata? = null
        override suspend fun upsert(meta: CachedPluginMetadata) = Unit
        override suspend fun deleteByPath(filePath: String) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class InMemorySnapshotStore : SnapshotStore {
        val deleteExpiredCalls = mutableListOf<Pair<String, Long>>()
        var pruneNamespaceCalled = false

        override suspend fun read(namespace: String, key: String): com.hank.musicfree.core.runtime.RuntimeSnapshot? = null
        override suspend fun write(snapshot: com.hank.musicfree.core.runtime.RuntimeSnapshot) {}
        override suspend fun delete(namespace: String, key: String) {}
        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int {
            deleteExpiredCalls += namespace to nowEpochMs
            return 0
        }

        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int {
            pruneNamespaceCalled = true
            return 0
        }

        override suspend fun keys(namespace: String, limit: Int): List<String> = emptyList()
    }

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
            events += RecordedLogEvent(category, event, fields)
        }

        override fun flush() {}
    }
}
