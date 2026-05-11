# Default Bootstrap Plugins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-install a developer-configurable set of subscription sources and single plugins on every cold start, so testing/dev builds always have a working plugin set after clearing app data. Disable for release by commenting out entries.

**Architecture:** Add a new `@Singleton DefaultPluginsBootstrapper` in `:app/bootstrap/` injected with `PluginManager` + `PluginMetaStore` + an app-scoped `CoroutineScope`. `MusicFreeApplication.onCreate()` calls `bootstrapper.start()`, which launches a background `Dispatchers.IO` coroutine that reconciles `DefaultPlugins.subscriptionUrls` and `DefaultPlugins.pluginUrls` against already-installed state. Idempotent (no DataStore flag), non-blocking, per-URL exception isolation.

**Tech Stack:** Kotlin + Hilt + kotlinx.coroutines, mockito-kotlin + kotlinx-coroutines-test for unit tests, structured `MfLog` events from `:logging`.

**Spec:** `docs/superpowers/specs/2026-05-11-default-bootstrap-plugins-design.md`

**File map:**

| Action | File | Responsibility |
|---|---|---|
| Modify | `app/build.gradle.kts` | Add `kotlinx.coroutines.test` + `mockito.kotlin` to testImplementation |
| Create | `app/src/main/java/com/zili/android/musicfreeandroid/di/ApplicationScope.kt` | Hilt `@Qualifier` annotation |
| Create | `app/src/main/java/com/zili/android/musicfreeandroid/di/CoroutineModule.kt` | Hilt `@Provides` for app-scoped `CoroutineScope` |
| Create | `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPlugins.kt` | The editable URL list (commented out for release) |
| Create | `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt` | Reconcile logic |
| Modify | `app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt` | `@Inject` bootstrapper + call `start()` |
| Create | `app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapperTest.kt` | Unit tests |
| Modify | `docs/dev-harness/plugin/rules.md` | New MUST rule about release-time URL list cleanup |

---

## Task 1: Add :app test dependencies

**Files:**
- Modify: `app/build.gradle.kts:154`

- [ ] **Step 1: Edit `app/build.gradle.kts` to add test deps**

Insert two lines after `testImplementation(libs.junit)` (line 154):

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
```

- [ ] **Step 2: Sync and verify test classpath resolves**

Run from worktree root:

```bash
./gradlew :app:dependencies --configuration testRuntimeClasspath > /tmp/app-test-deps.txt
grep -E "mockito-kotlin|kotlinx-coroutines-test" /tmp/app-test-deps.txt
```

Expected: at least two lines mentioning `mockito-kotlin-6.3.0` and `kotlinx-coroutines-test-*`.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build(app): add coroutines-test and mockito-kotlin for unit tests"
```

---

## Task 2: Add `ApplicationScope` qualifier + `CoroutineModule`

**Files:**
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/di/ApplicationScope.kt`
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/di/CoroutineModule.kt`

- [ ] **Step 1: Create the qualifier annotation**

```kotlin
// app/src/main/java/com/zili/android/musicfreeandroid/di/ApplicationScope.kt
package com.zili.android.musicfreeandroid.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

- [ ] **Step 2: Create the Hilt module providing the scope**

```kotlin
// app/src/main/java/com/zili/android/musicfreeandroid/di/CoroutineModule.kt
package com.zili.android.musicfreeandroid.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

Note: The scope itself uses `Dispatchers.Default`. Bootstrapper will `launch(Dispatchers.IO)` explicitly for blocking I/O work — matches spec §3.2.

- [ ] **Step 3: Verify Hilt graph compiles**

```bash
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL. (KSP runs Hilt aggregating processor — any qualifier/module mistake fails here.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/di/
git commit -m "feat(app): add ApplicationScope qualifier and CoroutineModule"
```

---

## Task 3: Add `DefaultPlugins` URL list

**Files:**
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPlugins.kt`

- [ ] **Step 1: Create the file**

```kotlin
// app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPlugins.kt
package com.zili.android.musicfreeandroid.bootstrap

/**
 * Developer / test fixture: URLs reconciled on every cold start by
 * [DefaultPluginsBootstrapper]. Comment out entries to disable individually,
 * or empty both lists entirely before publishing a release build.
 *
 * This file is the single source of truth for which plugins ship with the
 * debug fixture — see `docs/dev-harness/plugin/rules.md`.
 */
object DefaultPlugins {
    val subscriptionUrls: List<String> = listOf(
        "https://13413.kstore.vip/yuanli/yuanli.json",
    )

    val pluginUrls: List<String> = listOf(
        "https://raw.githubusercontent.com/ThomasBy2025/musicfree/refs/heads/main/plugins/wy.js",
    )
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPlugins.kt
git commit -m "feat(app): add DefaultPlugins fixture list for dev bootstrap"
```

---

## Task 4: TDD `DefaultPluginsBootstrapper` reconcile logic

This task adds the bootstrapper class one behavior at a time, each verified by a failing-then-passing unit test. The class will end with these capabilities:

- Reads `subscriptionUrls` and `pluginUrls` from caller (injected for testability — see Step 1 for rationale).
- Skips URLs already represented in `PluginMetaStore.subscriptions` (for subscription URLs) and in `PluginManager.plugins.value` via `installSource.value` (for plugin URLs).
- Calls `PluginManager.installFromSubscriptionUrl` / `installFromNetworkUrl` for new URLs.
- Catches `Throwable` per URL; one bad URL does not stop the others.
- Emits structured `MfLog` events.

**Files:**
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt`
- Create: `app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapperTest.kt`

### Task 4a — Empty lists are a no-op

- [ ] **Step 1: Write failing test (creates the test file)**

```kotlin
// app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapperTest.kt
package com.zili.android.musicfreeandroid.bootstrap

import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import com.zili.android.musicfreeandroid.plugin.meta.SubscriptionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DefaultPluginsBootstrapperTest {

    private fun bootstrapper(
        pluginManager: PluginManager,
        pluginMetaStore: PluginMetaStore,
    ): DefaultPluginsBootstrapper = DefaultPluginsBootstrapper(
        pluginManager = pluginManager,
        pluginMetaStore = pluginMetaStore,
        applicationScope = mock(), // unused in reconcile() direct calls
    )

    @Test
    fun reconcile_emptyLists_isNoOp() = runTest {
        val pluginManager = mock<PluginManager>()
        val pluginMetaStore = mock<PluginMetaStore>()

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = emptyList(),
        )

        verify(pluginManager, never()).installFromSubscriptionUrl(org.mockito.kotlin.any())
        verify(pluginManager, never()).installFromNetworkUrl(org.mockito.kotlin.any())
        verify(pluginMetaStore, never()).subscriptions
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapperTest.reconcile_emptyLists_isNoOp"
```

Expected: compile error — `DefaultPluginsBootstrapper` symbol not found.

- [ ] **Step 3: Create the minimal bootstrapper**

```kotlin
// app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt
package com.zili.android.musicfreeandroid.bootstrap

import androidx.annotation.VisibleForTesting
import com.zili.android.musicfreeandroid.di.ApplicationScope
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPluginsBootstrapper @Inject constructor(
    private val pluginManager: PluginManager,
    private val pluginMetaStore: PluginMetaStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    @VisibleForTesting
    internal suspend fun reconcile(
        subscriptionUrls: List<String>,
        pluginUrls: List<String>,
    ) {
        if (subscriptionUrls.isEmpty() && pluginUrls.isEmpty()) return
        // remaining behaviors added in 4b–4d
    }
}
```

- [ ] **Step 4: Run the test — verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapperTest.reconcile_emptyLists_isNoOp"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt \
       app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapperTest.kt
git commit -m "test(app): DefaultPluginsBootstrapper no-ops on empty lists"
```

### Task 4b — Install missing subscription + skip existing

- [ ] **Step 1: Add two failing tests**

Append to `DefaultPluginsBootstrapperTest.kt` (inside the class, after the existing test):

```kotlin
    @Test
    fun reconcile_installsMissingSubscription() = runTest {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = listOf("https://example.com/sub.json"),
            pluginUrls = emptyList(),
        )

        verify(pluginManager).installFromSubscriptionUrl("https://example.com/sub.json")
    }

    @Test
    fun reconcile_skipsAlreadyInstalledSubscription() = runTest {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(
                listOf(SubscriptionItem(name = "sub", url = "https://example.com/sub.json"))
            )
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = listOf("https://example.com/sub.json"),
            pluginUrls = emptyList(),
        )

        verify(pluginManager, never()).installFromSubscriptionUrl(org.mockito.kotlin.any())
    }
```

Add the missing import at the top of the test file:

```kotlin
import org.mockito.kotlin.doReturn
```

- [ ] **Step 2: Run tests — verify both fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapperTest"
```

Expected: `reconcile_installsMissingSubscription` fails (no install call); `reconcile_skipsAlreadyInstalledSubscription` passes by accident.

- [ ] **Step 3: Implement subscription reconcile**

Update `DefaultPluginsBootstrapper.reconcile` body to:

```kotlin
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import kotlinx.coroutines.flow.first

@VisibleForTesting
internal suspend fun reconcile(
    subscriptionUrls: List<String>,
    pluginUrls: List<String>,
) {
    if (subscriptionUrls.isEmpty() && pluginUrls.isEmpty()) return

    val existingSubscriptionUrls = pluginMetaStore.subscriptions.first()
        .map { it.url.trim() }
        .toSet()

    for (raw in subscriptionUrls) {
        val url = raw.trim()
        if (url.isEmpty()) continue
        if (url in existingSubscriptionUrls) {
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "default_plugin_bootstrap_subscription_skipped",
                fields = mapOf("url" to url),
            )
            continue
        }
        val startedAt = System.currentTimeMillis()
        runCatching { pluginManager.installFromSubscriptionUrl(url) }
            .onSuccess { result ->
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "default_plugin_bootstrap_subscription",
                    fields = mapOf(
                        "url" to url,
                        "successCount" to result.successfulInstalls,
                        "failureCount" to result.failedInstalls,
                        "durationMs" to (System.currentTimeMillis() - startedAt),
                    ),
                )
            }
            .onFailure { t ->
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "default_plugin_bootstrap_failed",
                    throwable = t,
                    fields = mapOf(
                        "stage" to "subscription",
                        "url" to url,
                        "errorClass" to t::class.java.name,
                    ),
                )
            }
    }
    // plugin reconcile added in 4c
}
```

- [ ] **Step 4: Run tests — both pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapperTest"
```

Expected: all 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt \
       app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapperTest.kt
git commit -m "feat(app): reconcile missing subscriptions on bootstrap"
```

### Task 4c — Install missing plugin + skip existing

- [ ] **Step 1: Add two failing tests**

Append to `DefaultPluginsBootstrapperTest.kt`:

```kotlin
    @Test
    fun reconcile_installsMissingPlugin() = runTest {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://example.com/wy.js"),
        )

        verify(pluginManager).ensurePluginsLoaded()
        verify(pluginManager).installFromNetworkUrl("https://example.com/wy.js")
    }

    @Test
    fun reconcile_skipsAlreadyInstalledPlugin() = runTest {
        val installed = stubLoadedPluginWithSource("https://example.com/wy.js")
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(listOf(installed))
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://example.com/wy.js"),
        )

        verify(pluginManager, never()).installFromNetworkUrl(org.mockito.kotlin.any())
    }
```

Add the helper at the bottom of the test class:

```kotlin
    private fun stubLoadedPluginWithSource(sourceUrl: String): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        val source = com.zili.android.musicfreeandroid.plugin.manager.PluginInstallSource(
            type = com.zili.android.musicfreeandroid.plugin.manager.PluginInstallSourceType.PLUGIN_URL,
            value = sourceUrl,
        )
        whenever(plugin.installSource).thenReturn(source)
        return plugin
    }
```

- [ ] **Step 2: Run tests — verify the install test fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapperTest"
```

Expected: `reconcile_installsMissingPlugin` fails (no install + no ensurePluginsLoaded); skip test passes by accident.

- [ ] **Step 3: Extend `reconcile` with plugin handling**

Append to the body of `reconcile` (after the subscription loop, before the function closes):

```kotlin
    if (pluginUrls.isEmpty()) return

    pluginManager.ensurePluginsLoaded()
    val existingPluginUrls = pluginManager.plugins.value
        .mapNotNull { it.installSource.value?.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    for (raw in pluginUrls) {
        val url = raw.trim()
        if (url.isEmpty()) continue
        if (url in existingPluginUrls) {
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "default_plugin_bootstrap_plugin_skipped",
                fields = mapOf("url" to url),
            )
            continue
        }
        val startedAt = System.currentTimeMillis()
        runCatching { pluginManager.installFromNetworkUrl(url) }
            .onSuccess { result ->
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "default_plugin_bootstrap_plugin",
                    fields = mapOf(
                        "url" to url,
                        "successCount" to result.successCount,
                        "failureCount" to result.failureCount,
                        "durationMs" to (System.currentTimeMillis() - startedAt),
                    ),
                )
            }
            .onFailure { t ->
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "default_plugin_bootstrap_failed",
                    throwable = t,
                    fields = mapOf(
                        "stage" to "plugin",
                        "url" to url,
                        "errorClass" to t::class.java.name,
                    ),
                )
            }
    }
```

- [ ] **Step 4: Run tests — all 5 pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapperTest"
```

Expected: 5/5 pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt \
       app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapperTest.kt
git commit -m "feat(app): reconcile missing single plugins on bootstrap"
```

### Task 4d — Continue on per-URL failure

- [ ] **Step 1: Add two failing tests**

Append:

```kotlin
    @Test
    fun reconcile_continuesAfterSubscriptionFailure() = runTest {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
            onBlocking { installFromSubscriptionUrl("https://bad/sub.json") }
                .thenThrow(RuntimeException("boom"))
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = listOf(
                "https://bad/sub.json",
                "https://good/sub.json",
            ),
            pluginUrls = emptyList(),
        )

        verify(pluginManager).installFromSubscriptionUrl("https://bad/sub.json")
        verify(pluginManager).installFromSubscriptionUrl("https://good/sub.json")
    }

    @Test
    fun reconcile_continuesAfterPluginFailure() = runTest {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
            onBlocking { installFromNetworkUrl("https://bad/wy.js") }
                .thenThrow(RuntimeException("boom"))
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://bad/wy.js", "https://good/wy.js"),
        )

        verify(pluginManager).installFromNetworkUrl("https://bad/wy.js")
        verify(pluginManager).installFromNetworkUrl("https://good/wy.js")
    }
```

- [ ] **Step 2: Run — verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapperTest"
```

Expected: 7/7 pass. (The reconcile loop already uses `runCatching`, so these tests verify behavior that's already correct — they exist as regression guards. If they fail, the catch was lost.)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapperTest.kt
git commit -m "test(app): bootstrap continues after per-url failure"
```

---

## Task 5: Add `start()` + wire into `MusicFreeApplication`

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt`

- [ ] **Step 1: Add `start()` method to bootstrapper**

Insert directly above the `@VisibleForTesting internal suspend fun reconcile(...)` method:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun start() {
    if (DefaultPlugins.subscriptionUrls.isEmpty() && DefaultPlugins.pluginUrls.isEmpty()) {
        return
    }
    applicationScope.launch(Dispatchers.IO) {
        reconcile(
            subscriptionUrls = DefaultPlugins.subscriptionUrls,
            pluginUrls = DefaultPlugins.pluginUrls,
        )
    }
}
```

- [ ] **Step 2: Wire into `MusicFreeApplication.onCreate()`**

First read the current file to confirm content; it should match the snippet below (or have only cosmetic differences). Then apply two edits:

**Edit A** — at the top of the file, after the existing imports, add two imports:

```kotlin
import com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapper
import javax.inject.Inject
```

**Edit B** — change the class body to declare the injected field and call `start()` at the end of `onCreate()`. The full body after the edit should be:

```kotlin
@HiltAndroidApp
class MusicFreeApplication : Application() {

    @Inject lateinit var defaultPluginsBootstrapper: DefaultPluginsBootstrapper

    override fun onCreate() {
        super.onCreate()

        LoggingInitializer.initialize(
            LoggingConfig(
                cacheDir = File(filesDir, "logan-cache"),
                logDir = File(filesDir, "logan"),
                feedbackDir = File(cacheDir, "feedback"),
                feedbackShareRootDir = cacheDir,
                aesKey16 = BuildConfig.LOGAN_AES_KEY,
                aesIv16 = BuildConfig.LOGAN_AES_IV,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                applicationId = BuildConfig.APPLICATION_ID,
                buildType = BuildConfig.BUILD_TYPE,
            ),
        )

        defaultPluginsBootstrapper.start()
    }
}
```

If the existing `LoggingInitializer.initialize(...)` block differs from above (different fields / shape), preserve it verbatim and only add the `@Inject` field and the `defaultPluginsBootstrapper.start()` line as the last statement of `onCreate()`.

- [ ] **Step 3: Build debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. (Verifies Hilt graph resolves `DefaultPluginsBootstrapper` with all three injected deps.)

- [ ] **Step 4: Re-run unit tests as a sanity check**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all bootstrap tests still pass; no other :app tests broken.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPluginsBootstrapper.kt \
       app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt
git commit -m "feat(app): launch default plugin bootstrap from Application.onCreate"
```

---

## Task 6: Add Dev-Harness rule for release-time URL cleanup

**Files:**
- Modify: `docs/dev-harness/plugin/rules.md` (append after the existing `## userVariables 写入串行化` section, which is currently the last rule)

The file uses `## 标题 {#rule-id}` headings with optional `implemented_by:` metadata. We add a new heading section at end-of-file. No `implemented_by` field (this is a forward-looking convention, not an incident-recovery rule).

- [ ] **Step 1: Append the new section to the end of `docs/dev-harness/plugin/rules.md`**

Open the file in edit mode and append exactly this block as the final section (after the existing `## userVariables 写入串行化` block ends):

```markdown
## 默认引导插件 (dev fixture) {#rule-default-bootstrap-plugins-list}

- `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPlugins.kt` 中的 `subscriptionUrls` 与 `pluginUrls` 列表是 dev / test fixture，由 `DefaultPluginsBootstrapper` 在 `MusicFreeApplication.onCreate()` 触发，每次冷启动 reconcile。
- MUST：发布构建前必须人工把两个 list 改成 `emptyList()` 或全行 `//` 注释。任何 release tag 携带未清空的列表都视为违规。
- MUST NOT：不得在该文件中引入按 buildType / BuildConfig 切换的"自动剥离"逻辑——保留"必须手动改 + 人工 review 一次"的语义，避免把策略埋进 gradle。
- 适用范围：`:app/bootstrap/`、`MusicFreeApplication.onCreate()`、任何调用 `DefaultPluginsBootstrapper.start()` 的入口。
- 关联设计：`docs/superpowers/specs/2026-05-11-default-bootstrap-plugins-design.md`。
```

- [ ] **Step 2: Verify the file still parses cleanly**

```bash
grep -c "^## " docs/dev-harness/plugin/rules.md
```

Expected: the count is exactly one larger than before the edit (a new top-level `##` section).

- [ ] **Step 3: Commit**

```bash
git add docs/dev-harness/plugin/rules.md
git commit -m "docs(harness): require release-time cleanup of DefaultPlugins list"
```

---

## Task 7: End-to-end verification

This task runs the full quality gate and does the manual cold-start checks listed in spec §7.2.

- [ ] **Step 1: Run full :app unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass, including the 7 new tests in `DefaultPluginsBootstrapperTest`.

- [ ] **Step 2: Run lint**

```bash
./gradlew :app:lintDebug
```

Expected: no new lint errors introduced.

- [ ] **Step 3: Build debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Note the APK path output.

- [ ] **Step 4: Install on device/emulator and clear app data**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.zili.android.musicfreeandroid
```

(Use the actual `applicationId` from `app/build.gradle.kts` if different.)

- [ ] **Step 5: Cold start and tail Logcat (Scenario 1)**

```bash
adb logcat -c
adb shell am start -n com.zili.android.musicfreeandroid/.MainActivity
adb logcat | grep -E "default_plugin_bootstrap"
```

Expected (within ~10 seconds, network permitting):

- one `default_plugin_bootstrap_subscription` line with successCount > 0
- one `default_plugin_bootstrap_plugin` line with success
- one `default_plugin_bootstrap_completed` line summarizing counts

Take a screenshot of the logcat output. Save evidence to a scratch path under the worktree.

- [ ] **Step 6: Manual UI check (Scenario 2)**

Open the app → 设置 → 插件. Expected:
- `yuanli` subscription is listed
- Plugins from `yuanli.json` are listed
- `wy.js` (or its declared `platform` name) is listed

- [ ] **Step 7: Idempotency check (Scenario 3)**

```bash
adb shell am force-stop com.zili.android.musicfreeandroid
adb logcat -c
adb shell am start -n com.zili.android.musicfreeandroid/.MainActivity
adb logcat | grep -E "default_plugin_bootstrap"
```

Expected: only `*_skipped` events; the `*_completed` event shows `installedSubscriptionCount=0` and `installedPluginCount=0`.

- [ ] **Step 8: Manual uninstall + reinstall reconcile (Scenario 4)**

In the app, open 插件管理 → uninstall `wy.js` → force-stop → restart. Expected: a fresh `default_plugin_bootstrap_plugin` event for `wy.js`.

- [ ] **Step 9: Offline cold-start (Scenario 5)**

```bash
adb shell svc wifi disable
adb shell svc data disable
adb shell pm clear com.zili.android.musicfreeandroid
adb logcat -c
adb shell am start -n com.zili.android.musicfreeandroid/.MainActivity
adb logcat | grep -E "default_plugin_bootstrap"
```

Expected: at least one `default_plugin_bootstrap_failed` event (likely with `errorClass` referencing IO/network), no crash, main screen renders normally.

Re-enable network when done:
```bash
adb shell svc wifi enable
adb shell svc data enable
```

- [ ] **Step 10: Release-disable simulation (Scenario 6)**

Temporarily edit `DefaultPlugins.kt` to comment out both list contents:

```kotlin
val subscriptionUrls: List<String> = listOf(
//  "https://13413.kstore.vip/yuanli/yuanli.json",
)
val pluginUrls: List<String> = listOf(
//  "https://raw.githubusercontent.com/ThomasBy2025/musicfree/refs/heads/main/plugins/wy.js",
)
```

Rebuild + reinstall + clear data + cold start:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.zili.android.musicfreeandroid
adb logcat -c
adb shell am start -n com.zili.android.musicfreeandroid/.MainActivity
adb logcat | grep -E "default_plugin_bootstrap"
```

Expected: no `default_plugin_bootstrap_*` events at all. No plugins auto-installed.

Then **revert** the temporary edit:

```bash
git checkout app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/DefaultPlugins.kt
```

- [ ] **Step 11: Final commit + log**

If any incidental fixes were needed during verification, commit them now. Otherwise note:

> Manual verification complete: Scenarios 1–6 pass. Evidence at <path>.

No commit required if no source changed.

---

## Done criteria

- All 7 unit tests in `DefaultPluginsBootstrapperTest` pass.
- `:app:assembleDebug` succeeds.
- All 6 manual scenarios pass.
- Dev-harness rule added.
- Commit log clean and atomic per task.
- Branch ready for review / merge: `feat/default-bootstrap-plugins`.
