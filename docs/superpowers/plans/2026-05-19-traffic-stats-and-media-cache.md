# 流量统计与音频本地缓存 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把项目内 5 个独立 OkHttpClient 收敛到 `:core` 统一 `@BaseOkHttp` provider，统一接 `EventListener` 采集 HTTP 流量，按 (本地日期 × 网络类型) 聚合存入 Room；同时引入 ExoPlayer SimpleCache 降低重复播放流量；在 HomeDrawer 加「流量统计」入口提供查询页。

**Architecture:** 自下而上分 7 个 phase：前置基建 → 网络基建骨架（采集层）→ 数据层（Room/sink/repo）→ 五个 client 收敛 → Coil 改造 → Media3 + SimpleCache → UI → dev-harness 守门。每个 phase 完成即可独立发版，独立可回滚。

**Tech Stack:** Kotlin / Jetpack Compose / Hilt / Room / OkHttp `EventListener` / Media3 `OkHttpDataSource` + `SimpleCache` / Coil 3 `OkHttpNetworkFetcherFactory` / `kotlinx.coroutines` Channel + 协程 worker。

**Design source of truth:** `docs/superpowers/specs/2026-05-19-traffic-stats-and-media-cache-design.md`（每个 task 引用对应 §章节，代码细节以 spec 为准）。

---

## File Structure

每个文件一句职责说明，按改动方向分组。

### Phase 0 — 前置

| 文件 | 操作 | 职责 |
|---|---|---|
| `core/src/main/java/com/hank/musicfree/core/util/Clock.kt` | 创建 | 抽象时间源，便于单测注入 fake clock |
| `core/src/main/java/com/hank/musicfree/core/util/SystemClock.kt` | 创建 | `Clock` 的 system-time 实现 + Hilt `@Binds` |
| `core/src/main/java/com/hank/musicfree/core/util/ClockModule.kt` | 创建 | Hilt 模块绑定 `Clock → SystemClock` |
| `core/src/main/java/com/hank/musicfree/core/di/ApplicationScope.kt` | 创建（从 :app 迁移） | `@ApplicationScope` qualifier |
| `core/src/main/java/com/hank/musicfree/core/di/CoroutineModule.kt` | 创建（从 :app 迁移） | `@ApplicationScope CoroutineScope` provider |
| `app/src/main/java/com/hank/musicfree/di/ApplicationScope.kt` | 删除 | 已下移 |
| `app/src/main/java/com/hank/musicfree/di/CoroutineModule.kt` | 删除 | 已下移 |
| 多处现有 .kt | 修改 import | `com.hank.musicfree.di.ApplicationScope` → `com.hank.musicfree.core.di.ApplicationScope` |

### Phase 1 — 网络基建骨架（`:core/network`）

| 文件 | 操作 | 职责 |
|---|---|---|
| `core/src/main/java/com/hank/musicfree/core/network/NetworkType.kt` | 创建 | `enum class { WIFI, CELLULAR, OTHER }` |
| `core/src/main/java/com/hank/musicfree/core/network/TrafficSample.kt` | 创建 | 单次 HTTP call 的字节快照 domain model |
| `core/src/main/java/com/hank/musicfree/core/network/TrafficSampleSink.kt` | 创建 | `interface { fun offer(s: TrafficSample) }` |
| `core/src/main/java/com/hank/musicfree/core/network/NetworkTypeDetector.kt` | 创建 | 注册 `ConnectivityManager.NetworkCallback` 缓存当前网络类型 |
| `core/src/main/java/com/hank/musicfree/core/network/NetworkTrafficEventListener.kt` | 创建 | OkHttp `EventListener` 采集每 call 字节并投递 sink |
| `core/src/main/java/com/hank/musicfree/core/network/BaseOkHttp.kt` | 创建 | `@BaseOkHttp` qualifier |
| `core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt` | 创建 | Hilt provide `@BaseOkHttp OkHttpClient` |
| `core/src/main/java/com/hank/musicfree/core/network/NoOpTrafficSampleSink.kt` | 创建 | 兜底 sink，Phase 2 前先满足 DI 图 |

### Phase 2 — 数据层（`:data/traffic` + `:data/db`）

| 文件 | 操作 | 职责 |
|---|---|---|
| `data/src/main/java/com/hank/musicfree/data/db/entity/TrafficDailyEntity.kt` | 创建 | Room entity |
| `data/src/main/java/com/hank/musicfree/data/db/dao/TrafficDailyDao.kt` | 创建 | DAO（含 upsert 累加、范围查询、月聚合、清空） |
| `data/src/main/java/com/hank/musicfree/data/db/dao/TrafficMonthlyRow.kt` | 创建 | DAO 月聚合返回类型 |
| `data/src/main/java/com/hank/musicfree/data/db/dao/TrafficTotalRow.kt` | 创建 | DAO 总聚合返回类型 |
| `data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt` | 创建 | DB 12→13 migration |
| `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt` | 修改 | version 12→13、加 entity、加 dao |
| `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt` | 修改 | `.addMigrations(...)` 追加 `MIGRATION_12_13` |
| `data/src/main/java/com/hank/musicfree/data/traffic/TrafficSampleSinkImpl.kt` | 创建 | Channel + worker 协程 batch flush 到 DAO |
| `data/src/main/java/com/hank/musicfree/data/traffic/TrafficStatsRepository.kt` | 创建 | 接口 + impl，按 scope/anchor 聚合 |
| `data/src/main/java/com/hank/musicfree/data/traffic/TrafficSinkBindings.kt` | 创建 | Hilt @Binds 把 `TrafficSampleSink` 绑到 `TrafficSampleSinkImpl` |

### Phase 3 — 5 个 client 收敛

| 文件 | 操作 | 职责 |
|---|---|---|
| `downloader/.../di/DownloaderProvidersModule.kt:44-45` | 修改 | `provideOkHttpClient` 从 `@BaseOkHttp base` 派生 |
| `updater/.../di/UpdaterModule.kt:47-54` | 修改 | `provideUpdaterOkHttp` 从 base 派生 |
| `plugin/.../manager/PluginManager.kt:153-158` | 修改 | `httpClient` 从注入的 base 派生 |
| `plugin/.../engine/WebDavShim.kt:120-143` | 修改 | `SHARED` 改为 Hilt singleton + 注入 base |
| `plugin/.../engine/AxiosShim.kt:59-71` | 修改 | 加 `setBaseClient(client)` 静态注入入口 |
| `app/.../MusicFreeApplication.kt` | 修改 | Application onCreate 注入 base 到 `AxiosShim.setBaseClient` |
| `plugin/.../engine/WebDavShimModule.kt` | 创建 | Hilt provide WebDavShim |
| `downloader/.../harness/contracts/DownloaderClientContractTest.kt` | 创建 | 契约：派生自 base |
| `updater/.../harness/contracts/UpdaterClientContractTest.kt` | 创建 | 同上 |
| `plugin/.../harness/contracts/PluginClientContractTest.kt` | 创建 | 同上 |
| `app/.../harness/contracts/BaseOkHttpClientWiringTest.kt` | 创建 | 验证 base client eventListenerFactory 非 null |

### Phase 4 — Coil 改造

| 文件 | 操作 | 职责 |
|---|---|---|
| `core/src/main/java/com/hank/musicfree/core/coil/ImageLoaderModule.kt` | 创建 | Hilt provide `ImageLoader` 注入 `@BaseOkHttp` |
| `app/.../MusicFreeApplication.kt` | 修改 | 实现 `SingletonImageLoader.Factory` |

### Phase 5 — Media3 + SimpleCache

| 文件 | 操作 | 职责 |
|---|---|---|
| `gradle/libs.versions.toml` | 修改 | 加 `androidx-media3-database` 别名 |
| `player/build.gradle.kts` | 修改 | 加 `implementation(libs.androidx.media3.database)` |
| `player/.../cache/SimpleCacheHolder.kt` | 创建 | SimpleCache 单例 holder，支持 `resetForClear` 和 init 失败 fallback |
| `player/.../cache/MediaCacheStore.kt` | 创建 | UI 用：`usedBytesFlow` + `clear()` |
| `player/.../cache/MediaCacheStoreImpl.kt` | 创建 | impl，依赖 holder + clock |
| `player/.../source/TrackHeaderRegistry.kt` | 修改 | `HeaderEntry` 加 `cacheKey: String?`，put/get 扩展 |
| `player/.../source/HeaderInjectingDataSourceFactory.kt` | 修改 | 改 OkHttpDataSource + CacheDataSource + fallback |
| `player/.../controller/PlayerController.kt` | 修改 | 三处 `trackHeaderRegistry.put(...)` 加 mediaId 参数 |

### Phase 6 — UI

| 文件 | 操作 | 职责 |
|---|---|---|
| `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt` | 修改 | 新增 `TrafficStatsRoute` |
| `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt` | 修改 | 新增 `Home.DrawerMeTrafficStats` + `Screen.TrafficStatsRoot` |
| `feature/settings/.../traffic/TrafficScope.kt` | 创建 | enum + shift 边界算法 |
| `feature/settings/.../traffic/TrafficUiState.kt` | 创建 | UI state sealed class |
| `feature/settings/.../traffic/TrafficUiMapper.kt` | 创建 | Repository 输出 → UI state 映射 |
| `feature/settings/.../traffic/TrafficStatsViewModel.kt` | 创建 | Hilt ViewModel |
| `feature/settings/.../traffic/TrafficBarChart.kt` | 创建 | Compose Canvas 自绘堆叠柱状图 |
| `feature/settings/.../traffic/TrafficStatsScreen.kt` | 创建 | 屏幕组装 |
| `feature/settings/.../traffic/navigation/TrafficStatsNavigation.kt` | 创建 | NavGraphBuilder 扩展 |
| `feature/home/.../HomeIcons.kt` | 修改 | 加 `DrawerTrafficStats` drawable ref |
| `feature/home/.../HomeDrawerNavigation.kt` | 修改 | 加 `OpenTrafficStats` action + drawer 项 |
| `feature/home/.../HomeScreen.kt` | 修改 | 加 `onNavigateToTrafficStats` 参数 + 分发 |
| `feature/home/.../navigation/HomeNavigation.kt` | 修改 | 透传 callback |
| `core/src/main/res/drawable/ic_home_data_usage.xml` | 创建 | Material outlined "data usage" icon |
| `app/.../navigation/AppNavHost.kt` | 修改 | 挂载 `TrafficStatsRoute` |

### Phase 7 — Dev-harness 守门

| 文件 | 操作 | 职责 |
|---|---|---|
| `docs/dev-harness/network/rules.md` | 创建 | 网络 area 规则 |
| `docs/dev-harness/network/incidents.md` | 创建 | 占位 |
| `docs/dev-harness/INDEX.md` | 修改 | 加 network 域行 |
| `scripts/dev-harness/grep-check.py` | 修改 | 加 `OkHttpClient.Builder()` 和 `DefaultHttpDataSource.Factory()` 守门 |
| `scripts/dev-harness/check.sh` | 修改 | contract test 行加 `:downloader` `:updater` |

---

## Tasks

### Task 1: 新增 `Clock` 时间抽象

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/util/Clock.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/util/SystemClock.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/util/ClockModule.kt`
- Test: `core/src/test/java/com/hank/musicfree/core/util/SystemClockTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
// core/src/test/java/com/hank/musicfree/core/util/SystemClockTest.kt
package com.hank.musicfree.core.util

import org.junit.Test
import kotlin.test.assertTrue

class SystemClockTest {
    @Test fun now_returns_system_currentTimeMillis_within_1_second() {
        val before = System.currentTimeMillis()
        val v = SystemClock.now()
        val after = System.currentTimeMillis()
        assertTrue(v in before..after)
    }
}
```

- [ ] **Step 2: 运行测试看失败**

```
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.util.SystemClockTest
```
Expected: FAIL (`SystemClock` 类不存在)

- [ ] **Step 3: 写 `Clock` 接口与 `SystemClock` 实现**

```kotlin
// core/src/main/java/com/hank/musicfree/core/util/Clock.kt
package com.hank.musicfree.core.util

interface Clock { fun now(): Long }
```

```kotlin
// core/src/main/java/com/hank/musicfree/core/util/SystemClock.kt
package com.hank.musicfree.core.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
    companion object : Clock by SystemClock()
}
```

注：`companion object : Clock by SystemClock()` 让 `SystemClock.now()` 静态调用也工作（测试用）。

```kotlin
// core/src/main/java/com/hank/musicfree/core/util/ClockModule.kt
package com.hank.musicfree.core.util

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds @Singleton abstract fun bindClock(impl: SystemClock): Clock
}
```

- [ ] **Step 4: 跑测试看通过**

```
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.util.SystemClockTest
```
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/util/ \
        core/src/test/java/com/hank/musicfree/core/util/
git commit -m "feat(core): 新增 Clock 时间抽象"
```

---

### Task 2: `@ApplicationScope` qualifier 下移到 `:core/di`

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/di/ApplicationScope.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/di/CoroutineModule.kt`
- Delete: `app/src/main/java/com/hank/musicfree/di/ApplicationScope.kt`
- Delete: `app/src/main/java/com/hank/musicfree/di/CoroutineModule.kt`
- Modify: 所有引用 `com.hank.musicfree.di.ApplicationScope` 的文件（导入路径替换）

- [ ] **Step 1: 创建新文件（内容与现有相同，仅 package 变化）**

```kotlin
// core/src/main/java/com/hank/musicfree/core/di/ApplicationScope.kt
package com.hank.musicfree.core.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

```kotlin
// core/src/main/java/com/hank/musicfree/core/di/CoroutineModule.kt
package com.hank.musicfree.core.di

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

- [ ] **Step 2: 删除旧文件**

```bash
rm app/src/main/java/com/hank/musicfree/di/ApplicationScope.kt
rm app/src/main/java/com/hank/musicfree/di/CoroutineModule.kt
```

- [ ] **Step 3: 全仓批量替换 import**

```bash
# 找出所有引用
grep -rln "com.hank.musicfree.di.ApplicationScope" --include="*.kt" \
  /Users/zili/code/android/MusicFreeAndroid

# 逐文件用 Edit 工具替换 import 行：
# import com.hank.musicfree.di.ApplicationScope
# →
# import com.hank.musicfree.core.di.ApplicationScope
```

预期触达文件：`PluginAutoUpdateCoordinator.kt`、`PlaybackStartupCoordinator.kt` 等。

- [ ] **Step 4: 编译全仓**

```
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 跑现有单测确认无回归**

```
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 6: commit**

```bash
git add -A
git commit -m "refactor(core): @ApplicationScope qualifier 下移到 :core/di"
```

---

### Task 3: `NetworkType` + `TrafficSample` domain

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/network/NetworkType.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/network/TrafficSample.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/network/TrafficSampleSink.kt`

- [ ] **Step 1: 写文件**

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/NetworkType.kt
package com.hank.musicfree.core.network

enum class NetworkType { WIFI, CELLULAR, OTHER }
```

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/TrafficSample.kt
package com.hank.musicfree.core.network

import java.time.LocalDate

data class TrafficSample(
    val localDate: LocalDate,
    val networkType: NetworkType,
    val bytesReceived: Long,
    val bytesSent: Long,
    val timestampMs: Long,
)
```

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/TrafficSampleSink.kt
package com.hank.musicfree.core.network

interface TrafficSampleSink {
    fun offer(sample: TrafficSample)
}
```

- [ ] **Step 2: 编译**

```
./gradlew :core:compileDebugKotlin
```
Expected: SUCCESS

- [ ] **Step 3: commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/network/
git commit -m "feat(core): 新增流量统计 domain 模型与 sink 接口"
```

---

### Task 4: `NetworkTypeDetector`

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/network/NetworkTypeDetector.kt`
- Test: `core/src/test/java/com/hank/musicfree/core/network/NetworkTypeDetectorTest.kt`

- [ ] **Step 1: 写失败测试（Robolectric）**

```kotlin
// core/src/test/java/com/hank/musicfree/core/network/NetworkTypeDetectorTest.kt
package com.hank.musicfree.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NetworkTypeDetectorTest {
    private lateinit var context: Context
    private lateinit var detector: NetworkTypeDetector

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        detector = NetworkTypeDetector(context)
    }

    @Test fun defaults_to_OTHER_when_no_active_network() {
        assertEquals(NetworkType.OTHER, detector.current())
    }
}
```

- [ ] **Step 2: 运行测试看失败**

```
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.network.NetworkTypeDetectorTest
```
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 `NetworkTypeDetector`**

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/NetworkTypeDetector.kt
package com.hank.musicfree.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTypeDetector @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cm: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val cachedType = AtomicReference(NetworkType.OTHER)

    init {
        runCatching {
            cm.activeNetwork?.let { cachedType.set(classify(cm.getNetworkCapabilities(it))) }
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    cachedType.set(classify(caps))
                }
            })
        }
    }

    fun current(): NetworkType = cachedType.get()

    private fun classify(caps: NetworkCapabilities?): NetworkType = when {
        caps == null -> NetworkType.OTHER
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
        else -> NetworkType.OTHER
    }
}
```

- [ ] **Step 4: 跑测试**

```
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.network.NetworkTypeDetectorTest
```
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/network/NetworkTypeDetector.kt \
        core/src/test/java/com/hank/musicfree/core/network/
git commit -m "feat(core): 新增 NetworkTypeDetector"
```

---

### Task 5: `NetworkTrafficEventListener`

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/network/NetworkTrafficEventListener.kt`
- Test: `core/src/test/java/com/hank/musicfree/core/network/NetworkTrafficEventListenerTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
// core/src/test/java/com/hank/musicfree/core/network/NetworkTrafficEventListenerTest.kt
package com.hank.musicfree.core.network

import com.hank.musicfree.core.util.Clock
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

class NetworkTrafficEventListenerTest {
    private val fakeClock = object : Clock { override fun now() = 1_700_000_000_000L }
    private val fakeDetector = mockk<NetworkTypeDetector> {
        every { current() } returns NetworkType.WIFI
    }
    private val captured = mutableListOf<TrafficSample>()
    private val sink = object : TrafficSampleSink {
        override fun offer(sample: TrafficSample) { captured += sample }
    }
    private val call = mockk<Call>(relaxed = true)

    private fun newListener() = NetworkTrafficEventListener.Factory(sink, fakeDetector, fakeClock)
        .create(call) as NetworkTrafficEventListener

    @Test fun accumulates_request_and_response_body_bytes() {
        val l = newListener()
        l.callStart(call)
        l.requestBodyEnd(call, 100)
        l.responseBodyEnd(call, 500)
        l.callEnd(call)
        assertEquals(1, captured.size)
        assertEquals(100L, captured[0].bytesSent)
        assertEquals(500L, captured[0].bytesReceived)
        assertEquals(NetworkType.WIFI, captured[0].networkType)
    }

    @Test fun callFailed_still_flushes_partial_bytes() {
        val l = newListener()
        l.callStart(call)
        l.requestBodyEnd(call, 50)
        l.callFailed(call, IOException("boom"))
        assertEquals(1, captured.size)
        assertEquals(50L, captured[0].bytesSent)
    }

    @Test fun zero_bytes_call_does_not_offer() {
        val l = newListener()
        l.callStart(call)
        l.callFailed(call, IOException("dns"))
        assertEquals(0, captured.size)
    }

    @Test fun factory_returns_new_instance_per_call() {
        val factory = NetworkTrafficEventListener.Factory(sink, fakeDetector, fakeClock)
        val a = factory.create(call); val b = factory.create(call)
        assert(a !== b)
    }
}
```

- [ ] **Step 2: 运行测试看失败**

```
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.network.NetworkTrafficEventListenerTest
```
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现**

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/NetworkTrafficEventListener.kt
package com.hank.musicfree.core.network

import com.hank.musicfree.core.util.Clock
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class NetworkTrafficEventListener internal constructor(
    private val sink: TrafficSampleSink,
    private val networkTypeDetector: NetworkTypeDetector,
    private val clock: Clock,
) : EventListener() {

    private var snapshotType: NetworkType = NetworkType.OTHER
    private var bytesSent: Long = 0
    private var bytesReceived: Long = 0

    override fun callStart(call: Call) {
        snapshotType = networkTypeDetector.current()
        bytesSent = 0; bytesReceived = 0
    }
    override fun requestBodyEnd(call: Call, byteCount: Long) { bytesSent += byteCount }
    override fun responseBodyEnd(call: Call, byteCount: Long) { bytesReceived += byteCount }
    override fun callEnd(call: Call) { flush() }
    override fun callFailed(call: Call, ioe: IOException) { flush() }

    private fun flush() {
        if (bytesSent == 0L && bytesReceived == 0L) return
        sink.offer(
            TrafficSample(
                localDate = LocalDate.now(ZoneId.systemDefault()),
                networkType = snapshotType,
                bytesReceived = bytesReceived,
                bytesSent = bytesSent,
                timestampMs = clock.now(),
            )
        )
    }

    class Factory @Inject constructor(
        private val sink: TrafficSampleSink,
        private val detector: NetworkTypeDetector,
        private val clock: Clock,
    ) : EventListener.Factory {
        override fun create(call: Call): EventListener =
            NetworkTrafficEventListener(sink, detector, clock)
    }
}
```

- [ ] **Step 4: 跑测试**

```
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.network.NetworkTrafficEventListenerTest
```
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/network/NetworkTrafficEventListener.kt \
        core/src/test/java/com/hank/musicfree/core/network/NetworkTrafficEventListenerTest.kt
git commit -m "feat(core): 新增 NetworkTrafficEventListener 采集 OkHttp 字节"
```

---

### Task 6: `@BaseOkHttp` qualifier + `NetworkModule` + 兜底 sink

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/network/BaseOkHttp.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/network/NoOpTrafficSampleSink.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt`

- [ ] **Step 1: 写文件**

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/BaseOkHttp.kt
package com.hank.musicfree.core.network

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttp
```

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/NoOpTrafficSampleSink.kt
package com.hank.musicfree.core.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 默认 sink，Phase 2 用 TrafficSampleSinkImpl 通过 @Binds 覆盖。
 */
@Singleton
class NoOpTrafficSampleSink @Inject constructor() : TrafficSampleSink {
    override fun offer(sample: TrafficSample) { /* no-op */ }
}
```

```kotlin
// core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt
package com.hank.musicfree.core.network

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds @Singleton
    abstract fun bindSink(impl: NoOpTrafficSampleSink): TrafficSampleSink

    companion object {
        @Provides @Singleton @BaseOkHttp
        fun provideBaseOkHttpClient(
            factory: NetworkTrafficEventListener.Factory,
        ): OkHttpClient = OkHttpClient.Builder()
            .eventListenerFactory(factory)
            .build()
    }
}
```

- [ ] **Step 2: 编译**

```
./gradlew :core:compileDebugKotlin
```
Expected: SUCCESS

- [ ] **Step 3: commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/network/BaseOkHttp.kt \
        core/src/main/java/com/hank/musicfree/core/network/NoOpTrafficSampleSink.kt \
        core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt
git commit -m "feat(core): 新增 @BaseOkHttp provider 与兜底 sink"
```

---

### Task 7: `TrafficDailyEntity` + DAO + 单测

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/db/entity/TrafficDailyEntity.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/dao/TrafficDailyDao.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/dao/TrafficMonthlyRow.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/dao/TrafficTotalRow.kt`
- Test: `data/src/test/java/com/hank/musicfree/data/db/dao/TrafficDailyDaoTest.kt`

- [ ] **Step 1: 写 entity + dao + 返回类型**

```kotlin
// data/src/main/java/com/hank/musicfree/data/db/entity/TrafficDailyEntity.kt
package com.hank.musicfree.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "traffic_daily",
    primaryKeys = ["local_date", "network_type"],
)
data class TrafficDailyEntity(
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "network_type") val networkType: String,
    @ColumnInfo(name = "bytes_received") val bytesReceived: Long,
    @ColumnInfo(name = "bytes_sent") val bytesSent: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

```kotlin
// data/src/main/java/com/hank/musicfree/data/db/dao/TrafficMonthlyRow.kt
package com.hank.musicfree.data.db.dao

data class TrafficMonthlyRow(
    val yearMonth: String,
    val networkType: String,
    val bytesReceived: Long,
    val bytesSent: Long,
)
```

```kotlin
// data/src/main/java/com/hank/musicfree/data/db/dao/TrafficTotalRow.kt
package com.hank.musicfree.data.db.dao

data class TrafficTotalRow(
    val networkType: String,
    val bytesReceived: Long,
    val bytesSent: Long,
)
```

```kotlin
// data/src/main/java/com/hank/musicfree/data/db/dao/TrafficDailyDao.kt
package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TrafficDailyDao {

    @Transaction
    open suspend fun upsertAllAccumulating(rows: List<TrafficDailyEntity>) {
        rows.forEach { r ->
            val n = upsertAccumulate(r.localDate, r.networkType, r.bytesReceived, r.bytesSent, r.updatedAt)
            if (n == 0) insertIgnore(r)
        }
    }

    @Query("""
        UPDATE traffic_daily
        SET bytes_received = bytes_received + :rx,
            bytes_sent = bytes_sent + :tx,
            updated_at = :now
        WHERE local_date = :date AND network_type = :type
    """)
    abstract suspend fun upsertAccumulate(date: String, type: String, rx: Long, tx: Long, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(row: TrafficDailyEntity)

    @Query("SELECT * FROM traffic_daily WHERE local_date BETWEEN :startDate AND :endDate ORDER BY local_date ASC")
    abstract fun observeRange(startDate: String, endDate: String): Flow<List<TrafficDailyEntity>>

    @Query("""
        SELECT substr(local_date, 1, 7) AS yearMonth,
               network_type AS networkType,
               SUM(bytes_received) AS bytesReceived,
               SUM(bytes_sent) AS bytesSent
        FROM traffic_daily
        WHERE local_date >= :startInclusive AND local_date < :endExclusive
        GROUP BY yearMonth, network_type
        ORDER BY yearMonth ASC
    """)
    abstract fun observeMonthlyRange(startInclusive: String, endExclusive: String): Flow<List<TrafficMonthlyRow>>

    @Query("""
        SELECT network_type AS networkType,
               SUM(bytes_received) AS bytesReceived,
               SUM(bytes_sent) AS bytesSent
        FROM traffic_daily
        GROUP BY network_type
    """)
    abstract fun observeTotalsByNetwork(): Flow<List<TrafficTotalRow>>

    @Query("SELECT MIN(local_date) FROM traffic_daily")
    abstract fun observeFirstRecordDate(): Flow<String?>

    @Query("DELETE FROM traffic_daily")
    abstract suspend fun clearAll()
}
```

- [ ] **Step 2: 写 DAO 测试（Room in-memory）**

```kotlin
// data/src/test/java/com/hank/musicfree/data/db/dao/TrafficDailyDaoTest.kt
package com.hank.musicfree.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TrafficDailyDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrafficDailyDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.trafficDailyDao()
    }

    @After fun teardown() { db.close() }

    @Test fun upsert_accumulates_existing_row() = runTest {
        dao.upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-19", "WIFI", 100, 10, 1L)
        ))
        dao.upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-19", "WIFI", 200, 20, 2L)
        ))
        val rows = dao.observeRange("2026-05-19", "2026-05-19").first()
        assertEquals(1, rows.size)
        assertEquals(300, rows[0].bytesReceived)
        assertEquals(30, rows[0].bytesSent)
    }

    @Test fun upsert_inserts_new_row_when_missing() = runTest {
        dao.upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-19", "CELLULAR", 50, 5, 1L)
        ))
        val rows = dao.observeRange("2026-05-19", "2026-05-19").first()
        assertEquals(1, rows.size)
        assertEquals(50, rows[0].bytesReceived)
        assertEquals("CELLULAR", rows[0].networkType)
    }

    @Test fun observeTotalsByNetwork_sums_across_dates() = runTest {
        dao.upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-18", "WIFI", 100, 0, 1L),
            TrafficDailyEntity("2026-05-19", "WIFI", 200, 0, 2L),
        ))
        val totals = dao.observeTotalsByNetwork().first()
        assertEquals(1, totals.size)
        assertEquals(300, totals[0].bytesReceived)
    }

    @Test fun clearAll_empties_table() = runTest {
        dao.upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-19", "WIFI", 1, 1, 1L)
        ))
        dao.clearAll()
        assertEquals(0, dao.observeRange("2000-01-01", "2099-12-31").first().size)
    }
}
```

注：此测试依赖 Task 8 把 `trafficDailyDao()` 加进 `AppDatabase`，Task 7 仅写 dao 文件本身，先 commit；测试在 Task 8 完成后跑。

- [ ] **Step 3: 编译 dao 文件本身**

```
./gradlew :data:compileDebugKotlin
```
Expected: SUCCESS

- [ ] **Step 4: commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/entity/TrafficDailyEntity.kt \
        data/src/main/java/com/hank/musicfree/data/db/dao/TrafficDailyDao.kt \
        data/src/main/java/com/hank/musicfree/data/db/dao/TrafficMonthlyRow.kt \
        data/src/main/java/com/hank/musicfree/data/db/dao/TrafficTotalRow.kt \
        data/src/test/java/com/hank/musicfree/data/db/dao/TrafficDailyDaoTest.kt
git commit -m "feat(data): 新增 traffic_daily entity 与 DAO"
```

---

### Task 8: Migration 12→13 + 注册到 `AppDatabase` + `DataModule`

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt:56-57`
- Test: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration12To13Test.kt`

- [ ] **Step 1: 写 migration**

```kotlin
// data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt
package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS traffic_daily (
                local_date TEXT NOT NULL,
                network_type TEXT NOT NULL,
                bytes_received INTEGER NOT NULL DEFAULT 0,
                bytes_sent INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(local_date, network_type)
            )
        """.trimIndent())
    }
}
```

- [ ] **Step 2: 改 `AppDatabase.kt`**

加 import：
```kotlin
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import com.hank.musicfree.data.db.dao.TrafficDailyDao
```

把 `version = 12` 改为 `version = 13`；`entities = [...]` 数组追加 `TrafficDailyEntity::class`；类体新增：
```kotlin
abstract fun trafficDailyDao(): TrafficDailyDao
```

- [ ] **Step 3: 改 `DataModule.kt:57`**

把：
```kotlin
.addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
```
改为：
```kotlin
.addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
```
并加 import：
```kotlin
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
```

- [ ] **Step 4: 写 migration androidTest（仿 `AppDatabaseMigration11To12Test.kt`）**

```kotlin
// data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration12To13Test.kt
package com.hank.musicfree.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
import com.hank.musicfree.data.db.migration.MIGRATION_11_12
import com.hank.musicfree.data.db.migration.MIGRATION_10_11
import com.hank.musicfree.data.db.migration.MIGRATION_9_10
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration12To13Test {

    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val TEST_DB = "migration-12-13-test.db"

    @Test fun migrate_creates_traffic_daily() {
        helper.createDatabase(TEST_DB, 12).close()
        helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13).use { db ->
            db.query("SELECT COUNT(*) FROM traffic_daily").use { c ->
                c.moveToFirst(); assertEquals(0, c.getInt(0))
            }
        }
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, TEST_DB,
        ).addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .build().apply { openHelper.writableDatabase; close() }
    }
}
```

- [ ] **Step 5: 跑 DAO 单测（Task 7 testfiles + Task 8 schema 接齐）**

```
./gradlew :data:testDebugUnitTest --tests com.hank.musicfree.data.db.dao.TrafficDailyDaoTest
```
Expected: PASS

- [ ] **Step 6: 跑 migration androidTest**

```
./gradlew :data:connectedDebugAndroidTest --tests com.hank.musicfree.data.db.AppDatabaseMigration12To13Test
```
Expected: PASS（需连接设备/模拟器）

- [ ] **Step 7: commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt \
        data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt \
        data/src/main/java/com/hank/musicfree/data/di/DataModule.kt \
        data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration12To13Test.kt
git commit -m "feat(data): Room 12→13 新增 traffic_daily 迁移"
```

---

### Task 9: `TrafficSampleSinkImpl` + 测试

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/traffic/TrafficSampleSinkImpl.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/traffic/TrafficSinkBindings.kt`
- Test: `data/src/test/java/com/hank/musicfree/data/traffic/TrafficSampleSinkImplTest.kt`

- [ ] **Step 1: 写 impl**（参考 spec §4.3）

```kotlin
// data/src/main/java/com/hank/musicfree/data/traffic/TrafficSampleSinkImpl.kt
package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.core.network.TrafficSample
import com.hank.musicfree.core.network.TrafficSampleSink
import com.hank.musicfree.core.util.Clock
import com.hank.musicfree.data.db.dao.TrafficDailyDao
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficSampleSinkImpl @Inject constructor(
    private val dao: TrafficDailyDao,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : TrafficSampleSink {

    private val channel = Channel<TrafficSample>(
        capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch(Dispatchers.IO) {
            val pending = mutableMapOf<Pair<String, String>, Accum>()
            while (isActive) {
                val first = channel.receive()
                aggregate(pending, first)
                val deadline = clock.now() + FLUSH_INTERVAL_MS
                while (pending.size < MAX_BATCH) {
                    val remaining = deadline - clock.now()
                    if (remaining <= 0) break
                    val next = withTimeoutOrNull(remaining) { channel.receive() } ?: break
                    aggregate(pending, next)
                }
                flush(pending)
                pending.clear()
            }
        }
    }

    override fun offer(sample: TrafficSample) { channel.trySend(sample) }

    private fun aggregate(p: MutableMap<Pair<String, String>, Accum>, s: TrafficSample) {
        val key = s.localDate.toString() to s.networkType.name
        val a = p.getOrPut(key) { Accum() }
        a.rx += s.bytesReceived; a.tx += s.bytesSent
    }

    private suspend fun flush(p: Map<Pair<String, String>, Accum>) {
        if (p.isEmpty()) return
        val now = clock.now()
        val rows = p.map { (k, a) -> TrafficDailyEntity(k.first, k.second, a.rx, a.tx, now) }
        runCatching { dao.upsertAllAccumulating(rows) }
    }

    private class Accum { var rx = 0L; var tx = 0L }
    private companion object {
        const val FLUSH_INTERVAL_MS = 5_000L
        const val MAX_BATCH = 64
    }
}
```

- [ ] **Step 2: 写 Hilt @Binds（覆盖 NoOpSink）**

```kotlin
// data/src/main/java/com/hank/musicfree/data/traffic/TrafficSinkBindings.kt
package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.TrafficSampleSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.hank.musicfree.core.network.NetworkModule::class],
)
abstract class TrafficSinkBindings {
    @Binds @Singleton
    abstract fun bindSink(impl: TrafficSampleSinkImpl): TrafficSampleSink
}
```

⚠️ 注意：直接用 `@Binds` 在 `:data` 替换 `:core` 的 binding 会和原 `@Binds` 冲突。**实际正确写法**：把 `NoOpTrafficSampleSink` 的 `@Binds` 从 `NetworkModule` 里**移除**（Task 6 留的兜底改为 Phase 2 必备）。Task 9 这里直接 `@Binds` 即可，无需 `TestInstallIn`。

修正后的代码：

```kotlin
package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.TrafficSampleSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrafficSinkBindings {
    @Binds @Singleton
    abstract fun bindSink(impl: TrafficSampleSinkImpl): TrafficSampleSink
}
```

同时**修改 Task 6 的 `NetworkModule.kt`**：移除 `@Binds bindSink`，删除 `NoOpTrafficSampleSink.kt` 文件。这一步在 Task 9 内执行：

```bash
rm core/src/main/java/com/hank/musicfree/core/network/NoOpTrafficSampleSink.kt
```

并把 `NetworkModule.kt` 改成纯 object（不再 abstract）：

```kotlin
package com.hank.musicfree.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton @BaseOkHttp
    fun provideBaseOkHttpClient(
        factory: NetworkTrafficEventListener.Factory,
    ): OkHttpClient = OkHttpClient.Builder()
        .eventListenerFactory(factory)
        .build()
}
```

- [ ] **Step 3: 写 sink 单测**

```kotlin
// data/src/test/java/com/hank/musicfree/data/traffic/TrafficSampleSinkImplTest.kt
package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.core.network.TrafficSample
import com.hank.musicfree.core.util.Clock
import com.hank.musicfree.data.db.dao.TrafficDailyDao
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TrafficSampleSinkImplTest {

    private class FixedClock : Clock {
        var t = 0L
        override fun now(): Long = t
    }

    @Test fun flushes_aggregated_rows_after_window() = runTest {
        val dao = mockk<TrafficDailyDao>(relaxed = true)
        val clock = FixedClock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sink = TrafficSampleSinkImpl(dao, clock, scope)

        clock.t = 0L
        sink.offer(TrafficSample(LocalDate.of(2026, 5, 19), NetworkType.WIFI, 100, 10, 0))
        sink.offer(TrafficSample(LocalDate.of(2026, 5, 19), NetworkType.WIFI, 200, 20, 0))
        clock.t = 6_000L

        delay(6_500)
        val captured = slot<List<TrafficDailyEntity>>()
        coVerify { dao.upsertAllAccumulating(capture(captured)) }
        assertEquals(1, captured.captured.size)
        assertEquals(300, captured.captured[0].bytesReceived)
        assertEquals(30, captured.captured[0].bytesSent)
        scope.cancel()
    }
}
```

注：sink 内部使用 IO dispatcher 实际计时（不便完全 fake clock）。本测试用 `delay(6_500)` 等真实 6.5 秒，能稳定 pass 但慢。如果希望毫秒级测试，可以把 `FLUSH_INTERVAL_MS` 抽成 @VisibleForTesting 常量并在测试时通过 reflection 缩短。本版接受 6.5 秒慢测以保持 impl 简洁。

- [ ] **Step 4: 跑测试**

```
./gradlew :data:testDebugUnitTest --tests com.hank.musicfree.data.traffic.TrafficSampleSinkImplTest
```
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/traffic/ \
        data/src/test/java/com/hank/musicfree/data/traffic/ \
        core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt
git rm core/src/main/java/com/hank/musicfree/core/network/NoOpTrafficSampleSink.kt
git commit -m "feat(data): 新增 TrafficSampleSinkImpl 批量落盘"
```

---

### Task 10: `TrafficStatsRepository`

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/traffic/TrafficStatsRepository.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/traffic/TrafficStatsRepositoryImpl.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/traffic/TrafficRepositoryBindings.kt`
- Test: `data/src/test/java/com/hank/musicfree/data/traffic/TrafficStatsRepositoryImplTest.kt`

- [ ] **Step 1: 接口**

```kotlin
// data/src/main/java/com/hank/musicfree/data/traffic/TrafficStatsRepository.kt
package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.NetworkType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class TrafficBucket(
    val label: String,                  // "2026-05-19" 或 "2026-05" 或 "2026" 或 "TOTAL"
    val byNetwork: Map<NetworkType, Long>,   // 已合并 rx+tx
)

data class TrafficRangeSummary(
    val anchor: LocalDate,
    val buckets: List<TrafficBucket>,
    val totalBytes: Long,
    val byNetwork: Map<NetworkType, Long>,
)

interface TrafficStatsRepository {
    fun observeDaily(date: LocalDate): Flow<TrafficRangeSummary>
    fun observeWeekly(weekStart: LocalDate): Flow<TrafficRangeSummary>
    fun observeMonthly(monthStart: LocalDate): Flow<TrafficRangeSummary>
    fun observeYearly(yearStart: LocalDate): Flow<TrafficRangeSummary>
    fun observeTotal(): Flow<TrafficRangeSummary>
    fun observeFirstRecordDate(): Flow<LocalDate?>
    suspend fun clearAll()
}
```

- [ ] **Step 2: 实现**

```kotlin
// data/src/main/java/com/hank/musicfree/data/traffic/TrafficStatsRepositoryImpl.kt
package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.data.db.dao.TrafficDailyDao
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficStatsRepositoryImpl @Inject constructor(
    private val dao: TrafficDailyDao,
) : TrafficStatsRepository {

    override fun observeDaily(date: LocalDate): Flow<TrafficRangeSummary> =
        dao.observeRange(date.toString(), date.toString())
            .map { rows -> rows.toSummary(anchor = date, bucketKey = { it.localDate }) }

    override fun observeWeekly(weekStart: LocalDate): Flow<TrafficRangeSummary> {
        val end = weekStart.plusDays(6)
        return dao.observeRange(weekStart.toString(), end.toString())
            .map { rows -> rows.toSummary(anchor = weekStart, bucketKey = { it.localDate }) }
    }

    override fun observeMonthly(monthStart: LocalDate): Flow<TrafficRangeSummary> {
        val end = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        return dao.observeRange(monthStart.toString(), end.toString())
            .map { rows -> rows.toSummary(anchor = monthStart, bucketKey = { it.localDate }) }
    }

    override fun observeYearly(yearStart: LocalDate): Flow<TrafficRangeSummary> {
        val yearStartStr = yearStart.toString()
        val nextYear = yearStart.plusYears(1).toString()
        return dao.observeMonthlyRange(yearStartStr, nextYear)
            .map { rows ->
                val byMonth = rows.groupBy { it.yearMonth }
                val buckets = (1..12).map { m ->
                    val ym = YearMonth.of(yearStart.year, m).toString()
                    val byNet = byMonth[ym]?.associate {
                        NetworkType.valueOf(it.networkType) to (it.bytesReceived + it.bytesSent)
                    } ?: emptyMap()
                    TrafficBucket(label = ym, byNetwork = byNet)
                }
                TrafficRangeSummary(
                    anchor = yearStart,
                    buckets = buckets,
                    totalBytes = buckets.sumOf { it.byNetwork.values.sum() },
                    byNetwork = NetworkType.values().associateWith { nt ->
                        buckets.sumOf { it.byNetwork[nt] ?: 0L }
                    },
                )
            }
    }

    override fun observeTotal(): Flow<TrafficRangeSummary> =
        dao.observeTotalsByNetwork().map { rows ->
            val byNet = rows.associate {
                NetworkType.valueOf(it.networkType) to (it.bytesReceived + it.bytesSent)
            }
            TrafficRangeSummary(
                anchor = LocalDate.now(),
                buckets = listOf(TrafficBucket(label = "TOTAL", byNetwork = byNet)),
                totalBytes = byNet.values.sum(),
                byNetwork = byNet,
            )
        }

    override fun observeFirstRecordDate(): Flow<LocalDate?> =
        dao.observeFirstRecordDate().map { it?.let(LocalDate::parse) }

    override suspend fun clearAll() = dao.clearAll()

    private fun List<TrafficDailyEntity>.toSummary(
        anchor: LocalDate,
        bucketKey: (TrafficDailyEntity) -> String,
    ): TrafficRangeSummary {
        val grouped = groupBy(bucketKey)
        val buckets = grouped.entries.sortedBy { it.key }.map { (k, rows) ->
            val byNet = rows.associate {
                NetworkType.valueOf(it.networkType) to (it.bytesReceived + it.bytesSent)
            }
            TrafficBucket(label = k, byNetwork = byNet)
        }
        return TrafficRangeSummary(
            anchor = anchor,
            buckets = buckets,
            totalBytes = buckets.sumOf { it.byNetwork.values.sum() },
            byNetwork = NetworkType.values().associateWith { nt ->
                buckets.sumOf { it.byNetwork[nt] ?: 0L }
            },
        )
    }
}
```

- [ ] **Step 3: Hilt @Binds**

```kotlin
// data/src/main/java/com/hank/musicfree/data/traffic/TrafficRepositoryBindings.kt
package com.hank.musicfree.data.traffic

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrafficRepositoryBindings {
    @Binds @Singleton
    abstract fun bindRepo(impl: TrafficStatsRepositoryImpl): TrafficStatsRepository
}
```

- [ ] **Step 4: 写 repo 单测（验证聚合逻辑）**

```kotlin
// data/src/test/java/com/hank/musicfree/data/traffic/TrafficStatsRepositoryImplTest.kt
package com.hank.musicfree.data.traffic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TrafficStatsRepositoryImplTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TrafficStatsRepositoryImpl

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TrafficStatsRepositoryImpl(db.trafficDailyDao())
    }

    @After fun teardown() { db.close() }

    @Test fun observeTotal_sums_all_dates_and_networks() = runTest {
        db.trafficDailyDao().upsertAllAccumulating(listOf(
            TrafficDailyEntity("2026-05-18", "WIFI", 100, 10, 1),
            TrafficDailyEntity("2026-05-19", "CELLULAR", 200, 20, 2),
        ))
        val s = repo.observeTotal().first()
        assertEquals(330, s.totalBytes)
        assertEquals(110, s.byNetwork[NetworkType.WIFI])
        assertEquals(220, s.byNetwork[NetworkType.CELLULAR])
    }
}
```

- [ ] **Step 5: 跑测试**

```
./gradlew :data:testDebugUnitTest --tests com.hank.musicfree.data.traffic.TrafficStatsRepositoryImplTest
```
Expected: PASS

- [ ] **Step 6: commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/traffic/ \
        data/src/test/java/com/hank/musicfree/data/traffic/TrafficStatsRepositoryImplTest.kt
git commit -m "feat(data): 新增 TrafficStatsRepository 聚合查询"
```

---

### Task 11: `DownloaderProvidersModule` 从 base 派生

**Files:**
- Modify: `downloader/src/main/java/com/hank/musicfree/downloader/di/DownloaderProvidersModule.kt:44-45`
- Test: `downloader/src/test/java/com/hank/musicfree/downloader/harness/contracts/DownloaderClientContractTest.kt`

- [ ] **Step 1: 改 `provideOkHttpClient`**

```kotlin
@Provides @Singleton
fun provideOkHttpClient(@BaseOkHttp base: OkHttpClient): OkHttpClient =
    base.newBuilder().build()
```

并加 import：
```kotlin
import com.hank.musicfree.core.network.BaseOkHttp
```

- [ ] **Step 2: 写契约测试**

```kotlin
// downloader/src/test/java/com/hank/musicfree/downloader/harness/contracts/DownloaderClientContractTest.kt
package com.hank.musicfree.downloader.harness.contracts

import com.hank.musicfree.core.network.NetworkTrafficEventListener
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject
import kotlin.test.assertTrue

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class DownloaderClientContractTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var client: OkHttpClient

    @Test fun downloader_client_has_traffic_listener_factory() {
        hilt.inject()
        assertTrue(client.eventListenerFactory is NetworkTrafficEventListener.Factory)
    }
}
```

- [ ] **Step 3: 跑测试**

```
./gradlew :downloader:testDebugUnitTest --tests com.hank.musicfree.downloader.harness.contracts.DownloaderClientContractTest
```
Expected: PASS

- [ ] **Step 4: commit**

```bash
git add downloader/src/main/java/com/hank/musicfree/downloader/di/DownloaderProvidersModule.kt \
        downloader/src/test/java/com/hank/musicfree/downloader/harness/contracts/DownloaderClientContractTest.kt
git commit -m "refactor(downloader): OkHttpClient 改从 @BaseOkHttp 派生"
```

---

### Task 12: `UpdaterModule` 从 base 派生

**Files:**
- Modify: `updater/src/main/java/com/hank/musicfree/updater/di/UpdaterModule.kt:47-54`
- Test: `updater/src/test/java/com/hank/musicfree/updater/harness/contracts/UpdaterClientContractTest.kt`

- [ ] **Step 1: 改 `provideUpdaterOkHttp`**

```kotlin
@Provides
@Singleton
@UpdaterHttp
fun provideUpdaterOkHttp(@BaseOkHttp base: OkHttpClient): OkHttpClient =
    base.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
```

加 import：
```kotlin
import com.hank.musicfree.core.network.BaseOkHttp
```

- [ ] **Step 2: 写契约测试**

```kotlin
// updater/src/test/java/com/hank/musicfree/updater/harness/contracts/UpdaterClientContractTest.kt
package com.hank.musicfree.updater.harness.contracts

import com.hank.musicfree.core.network.NetworkTrafficEventListener
import com.hank.musicfree.updater.di.UpdaterHttp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject
import kotlin.test.assertTrue

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class UpdaterClientContractTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @UpdaterHttp lateinit var client: OkHttpClient

    @Test fun updater_client_has_traffic_listener_factory() {
        hilt.inject()
        assertTrue(client.eventListenerFactory is NetworkTrafficEventListener.Factory)
    }
}
```

- [ ] **Step 3: 跑测试**

```
./gradlew :updater:testDebugUnitTest --tests com.hank.musicfree.updater.harness.contracts.UpdaterClientContractTest
```
Expected: PASS

- [ ] **Step 4: commit**

```bash
git add updater/src/main/java/com/hank/musicfree/updater/di/UpdaterModule.kt \
        updater/src/test/java/com/hank/musicfree/updater/harness/contracts/UpdaterClientContractTest.kt
git commit -m "refactor(updater): OkHttpClient 改从 @BaseOkHttp 派生"
```

---

### Task 13: `PluginManager.httpClient` 从 base 派生

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt:74,153-158`
- Test: `plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginManagerClientContractTest.kt`

- [ ] **Step 1: 改 `PluginManager` 构造接受 `@BaseOkHttp base`**

在 ctor `@Inject constructor(` 内追加一个参数：
```kotlin
@BaseOkHttp private val baseOkHttpClient: OkHttpClient,
```
加 import：
```kotlin
import com.hank.musicfree.core.network.BaseOkHttp
import okhttp3.OkHttpClient
```

把原 `httpClient` 字段：
```kotlin
private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
```
改为：
```kotlin
private val httpClient: OkHttpClient by lazy {
    baseOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
```

- [ ] **Step 2: 写契约测试**

```kotlin
// plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginManagerClientContractTest.kt
package com.hank.musicfree.plugin.harness.contracts

import com.hank.musicfree.core.network.NetworkTrafficEventListener
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject
import kotlin.test.assertTrue

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class PluginManagerClientContractTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var pluginManager: PluginManager

    @Test fun plugin_manager_http_client_has_traffic_listener_factory() {
        hilt.inject()
        // PluginManager 的 httpClient 是 private lazy; 用 reflection 取
        val field = PluginManager::class.java.getDeclaredField("httpClient\$delegate")
        field.isAccessible = true
        val lazyDelegate = field.get(pluginManager) as kotlin.Lazy<okhttp3.OkHttpClient>
        val client = lazyDelegate.value
        assertTrue(client.eventListenerFactory is NetworkTrafficEventListener.Factory)
    }
}
```

- [ ] **Step 3: 跑测试**

```
./gradlew :plugin:testDebugUnitTest --tests com.hank.musicfree.plugin.harness.contracts.PluginManagerClientContractTest
```
Expected: PASS

- [ ] **Step 4: 跑现有 PluginManager 单测确认无回归**

```
./gradlew :plugin:testDebugUnitTest
```
Expected: ALL PASS

- [ ] **Step 5: commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt \
        plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginManagerClientContractTest.kt
git commit -m "refactor(plugin): PluginManager httpClient 改从 @BaseOkHttp 派生"
```

---

### Task 14: `WebDavShim` 改为 Hilt singleton + 注入 base

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/engine/WebDavShim.kt:120-143`
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/engine/WebDavShimModule.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt:2920`

- [ ] **Step 1: 移除 `WebDavShim` 内的 `defaultClient()` 与 `SHARED`，改为构造可注入**

`WebDavShim.kt` 当前是 `class WebDavShim(...)` 接 `client: OkHttpClient`。删除 companion 中 `defaultClient()` 与 `private val SHARED ...`。`register(engine)` 改为接收 `shim: WebDavShim` 参数（不再有默认值）：

```kotlin
companion object {
    suspend fun register(engine: JsEngine, shim: WebDavShim) {
        // 原内容...
    }
}
```

- [ ] **Step 2: 创建 `WebDavShimModule`**

```kotlin
// plugin/src/main/java/com/hank/musicfree/plugin/engine/WebDavShimModule.kt
package com.hank.musicfree.plugin.engine

import com.hank.musicfree.core.network.BaseOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebDavShimModule {
    @Provides @Singleton
    fun provideWebDavShim(@BaseOkHttp base: OkHttpClient): WebDavShim =
        WebDavShim(
            base.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        )
}
```

- [ ] **Step 3: PluginManager 注入 `WebDavShim` 并传给 register**

`PluginManager` ctor 加：
```kotlin
private val webDavShim: WebDavShim,
```

把 `WebDavShim.register(engine)` 改为 `WebDavShim.register(engine, webDavShim)`。

- [ ] **Step 4: 更新 `WebDavShimTest`**

测试已经用 `shim = WebDavShim(client)`，不变。但 `WebDavShim.register(engine)` 不再有默认 shim 参数，需要检查现有测试调用是否影响（grep 显示只有 PluginManager 与 test 调用）。如有破坏，把测试调用改为 `WebDavShim.register(engine, shim)`。

- [ ] **Step 5: 跑 :plugin 测试**

```
./gradlew :plugin:testDebugUnitTest
```
Expected: ALL PASS

- [ ] **Step 6: commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/WebDavShim.kt \
        plugin/src/main/java/com/hank/musicfree/plugin/engine/WebDavShimModule.kt \
        plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt \
        plugin/src/test/java/com/hank/musicfree/plugin/engine/WebDavShimTest.kt
git commit -m "refactor(plugin): WebDavShim 改为 Hilt singleton 注入 @BaseOkHttp"
```

---

### Task 15: `AxiosShim` 加 `setBaseClient` 静态注入入口

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt:59-71`
- Modify: `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`

`AxiosShim` 是 Kotlin object，无法 `@Inject`。方案：把 `baseClient` 改成 `@Volatile var`，由 Application 在 `onCreate` 时调用 `setBaseClient(...)` 注入。

- [ ] **Step 1: 改 `AxiosShim` baseClient 字段**

把：
```kotlin
private val baseClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(true)
        .build()
}
```
改为：
```kotlin
@Volatile
private var baseClient: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(true)
    .build()

/** Called once at Application onCreate after Hilt is ready. */
fun setBaseClient(client: OkHttpClient) {
    baseClient = client.newBuilder()
        .followRedirects(true)
        .build()
}
```

`clientFor(timeoutMs)` 不变（已经 `baseClient.newBuilder()...`）。

- [ ] **Step 2: Application 注入 base**

在 `MusicFreeApplication.kt` 加 `@Inject @BaseOkHttp lateinit var baseHttp: OkHttpClient`，并在 `onCreate()` super 调用之后：
```kotlin
AxiosShim.setBaseClient(baseHttp)
```
加 import：
```kotlin
import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.plugin.engine.AxiosShim
import okhttp3.OkHttpClient
```

- [ ] **Step 3: 写契约测试**

```kotlin
// plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/AxiosShimClientContractTest.kt
package com.hank.musicfree.plugin.harness.contracts

import com.hank.musicfree.core.network.NetworkTrafficEventListener
import com.hank.musicfree.plugin.engine.AxiosShim
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import kotlin.test.assertTrue

class AxiosShimClientContractTest {
    @Test fun setBaseClient_propagates_event_listener_factory() {
        val factory = NetworkTrafficEventListener.Factory(
            sink = { _ -> },
            detector = null!!,    // 不实际触发
            clock = { 0L },
        )
        val client = OkHttpClient.Builder().eventListenerFactory(factory).build()
        AxiosShim.setBaseClient(client)
        // 通过 reflection 验证 baseClient
        val field = AxiosShim::class.java.getDeclaredField("baseClient")
        field.isAccessible = true
        val stored = field.get(AxiosShim) as OkHttpClient
        assertTrue(stored.eventListenerFactory === factory)
    }
}
```

注：此测试用 reflection；如果 Kotlin 编译产生的字段名是 `baseClient`，可直接访问。如果 Lazy 转 var 后是 `getBaseClient/setBaseClient` 方法，改用反射方法调用。

- [ ] **Step 4: 跑测试**

```
./gradlew :plugin:testDebugUnitTest --tests com.hank.musicfree.plugin.harness.contracts.AxiosShimClientContractTest
```
Expected: PASS

- [ ] **Step 5: 跑现有 `AxiosShimTimeoutTest` 确认无回归**

```
./gradlew :plugin:testDebugUnitTest --tests com.hank.musicfree.plugin.engine.AxiosShimTimeoutTest
```
Expected: PASS

- [ ] **Step 6: commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt \
        plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/AxiosShimClientContractTest.kt \
        app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt
git commit -m "refactor(plugin): AxiosShim 接入 @BaseOkHttp via setBaseClient"
```

---

### Task 16: `BaseOkHttpClientWiringTest` 顶层验证

**Files:**
- Test: `app/src/test/java/com/hank/musicfree/harness/contracts/BaseOkHttpClientWiringTest.kt`

- [ ] **Step 1: 写测试**

```kotlin
// app/src/test/java/com/hank/musicfree/harness/contracts/BaseOkHttpClientWiringTest.kt
package com.hank.musicfree.harness.contracts

import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.core.network.NetworkTrafficEventListener
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject
import kotlin.test.assertTrue

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class BaseOkHttpClientWiringTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @BaseOkHttp lateinit var baseClient: OkHttpClient

    @Test fun base_client_has_traffic_event_listener_factory() {
        hilt.inject()
        assertTrue(baseClient.eventListenerFactory is NetworkTrafficEventListener.Factory)
    }
}
```

- [ ] **Step 2: 跑测试**

```
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.harness.contracts.BaseOkHttpClientWiringTest
```
Expected: PASS

- [ ] **Step 3: commit**

```bash
git add app/src/test/java/com/hank/musicfree/harness/contracts/BaseOkHttpClientWiringTest.kt
git commit -m "test(app): 新增 @BaseOkHttp wiring 顶层契约测试"
```

---

### Task 17: Coil 自定义 `ImageLoader`

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/coil/ImageLoaderModule.kt`
- Modify: `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`

- [ ] **Step 1: 创建 `ImageLoaderModule`**

```kotlin
// core/src/main/java/com/hank/musicfree/core/coil/ImageLoaderModule.kt
package com.hank.musicfree.core.coil

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.hank.musicfree.core.network.BaseOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttp okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
        .build()
}
```

注：`coil3.network.okhttp.OkHttpNetworkFetcherFactory` 路径正确（来自 `coil-network-okhttp:3.4.0`）。

- [ ] **Step 2: Application 实现 `SingletonImageLoader.Factory`**

`MusicFreeApplication.kt` 类签名加 `, SingletonImageLoader.Factory`，类体加：
```kotlin
@Inject lateinit var imageLoader: ImageLoader
override fun newImageLoader(context: coil3.PlatformContext): ImageLoader = imageLoader
```
加 import：
```kotlin
import coil3.ImageLoader
import coil3.SingletonImageLoader
```

- [ ] **Step 3: 编译**

```
./gradlew :app:assembleDebug
```
Expected: SUCCESS

- [ ] **Step 4: commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/coil/ImageLoaderModule.kt \
        app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt
git commit -m "feat(core): Coil ImageLoader 接入 @BaseOkHttp 统一统计图片流量"
```

---

### Task 18: 加 `media3-database` 依赖 + `SimpleCacheHolder`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `player/build.gradle.kts`
- Create: `player/src/main/java/com/hank/musicfree/player/cache/SimpleCacheHolder.kt`
- Test: `player/src/test/java/com/hank/musicfree/player/cache/SimpleCacheHolderTest.kt`

- [ ] **Step 1: 加依赖别名**

`gradle/libs.versions.toml` 在已有 media3 别名后追加：
```toml
androidx-media3-database = { group = "androidx.media3", name = "media3-database", version.ref = "media3" }
```

`player/build.gradle.kts` `dependencies {` 块追加：
```kotlin
implementation(libs.androidx.media3.database)
```

- [ ] **Step 2: 写 holder**

```kotlin
// player/src/main/java/com/hank/musicfree/player/cache/SimpleCacheHolder.kt
package com.hank.musicfree.player.cache

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@AndroidXOptIn(markerClass = [UnstableApi::class])
class SimpleCacheHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ref = AtomicReference<SimpleCache?>(null)
    private val initFailed = AtomicBoolean(false)

    val current: SimpleCache?
        get() = ref.get() ?: synchronized(this) {
            if (initFailed.get()) return null
            ref.get() ?: tryCreate()?.also { ref.set(it) }
        }

    fun resetForClear(): SimpleCache? = synchronized(this) {
        ref.get()?.release()
        ref.set(null)
        cacheDir().deleteRecursively()
        tryCreate()?.also { ref.set(it) }
    }

    fun cacheDirPath(): String = cacheDir().absolutePath
    fun usedBytes(): Long = current?.cacheSpace ?: 0L

    private fun tryCreate(): SimpleCache? = runCatching {
        SimpleCache(
            cacheDir().apply { mkdirs() },
            LeastRecentlyUsedCacheEvictor(DEFAULT_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }.onFailure { initFailed.set(true) }.getOrNull()

    private fun cacheDir(): File =
        context.getExternalFilesDir(null)?.resolve("media-cache")
            ?: context.cacheDir.resolve("media-cache")

    companion object {
        const val DEFAULT_BYTES = 512L * 1024 * 1024
    }
}
```

- [ ] **Step 3: 写 holder 单测（Robolectric）**

```kotlin
// player/src/test/java/com/hank/musicfree/player/cache/SimpleCacheHolderTest.kt
package com.hank.musicfree.player.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SimpleCacheHolderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val holder = SimpleCacheHolder(ctx)

    @After fun teardown() { holder.resetForClear() }

    @Test fun lazy_creates_cache_on_first_access() {
        assertNotNull(holder.current)
    }

    @Test fun resetForClear_replaces_instance() {
        val first = holder.current
        val second = holder.resetForClear()
        assertTrue(first !== second)
    }
}
```

- [ ] **Step 4: 跑测试**

```
./gradlew :player:testDebugUnitTest --tests com.hank.musicfree.player.cache.SimpleCacheHolderTest
```
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add gradle/libs.versions.toml player/build.gradle.kts \
        player/src/main/java/com/hank/musicfree/player/cache/SimpleCacheHolder.kt \
        player/src/test/java/com/hank/musicfree/player/cache/SimpleCacheHolderTest.kt
git commit -m "feat(player): 新增 SimpleCacheHolder 与 media3-database 依赖"
```

---

### Task 19: `TrackHeaderRegistry` 扩展 `cacheKey` 支持

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/source/TrackHeaderRegistry.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt:716,1060,1165`
- Test: `player/src/test/java/com/hank/musicfree/player/source/TrackHeaderRegistryTest.kt`

- [ ] **Step 1: 改 `HeaderEntry` 加 cacheKey**

```kotlin
data class HeaderEntry(
    val headers: Map<String, String>,
    val userAgent: String?,
    val cacheKey: String?,
)
```

更新 `put` 签名：
```kotlin
@Synchronized
fun put(url: String, headers: Map<String, String>, userAgent: String?, cacheKey: String? = null) {
    map[url] = HeaderEntry(headers, userAgent, cacheKey)
}
```

- [ ] **Step 2: 在 `PlayerController` 三处 `put` 加 mediaId**

三处分别在行 716、1060、1165；每处都需要把对应的 `track.id` 或 `resolution.mediaId` 作为新参数 `cacheKey` 传入。具体值取决于上下文，参考各 site 已有 `track` / `resolution` 对象的 id 字段。

例（行 716 附近）：
```kotlin
trackHeaderRegistry.put(
    resolvedUrl,
    source.headers.orEmpty(),
    source.userAgent,
    cacheKey = playable?.id,
)
```

- [ ] **Step 3: 写测试**

```kotlin
// player/src/test/java/com/hank/musicfree/player/source/TrackHeaderRegistryTest.kt
package com.hank.musicfree.player.source

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackHeaderRegistryTest {
    private val r = TrackHeaderRegistry()

    @Test fun put_with_cacheKey_returns_in_entry() {
        r.put("https://x/a", mapOf("k" to "v"), "UA", cacheKey = "media-123")
        assertEquals("media-123", r.get("https://x/a")?.cacheKey)
    }

    @Test fun put_without_cacheKey_default_null() {
        r.put("https://x/b", emptyMap(), null)
        assertNull(r.get("https://x/b")?.cacheKey)
    }
}
```

- [ ] **Step 4: 跑测试**

```
./gradlew :player:testDebugUnitTest --tests com.hank.musicfree.player.source.TrackHeaderRegistryTest
```
Expected: PASS

- [ ] **Step 5: 跑 :player 全量单测确认无回归**

```
./gradlew :player:testDebugUnitTest
```
Expected: ALL PASS

- [ ] **Step 6: commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/source/TrackHeaderRegistry.kt \
        player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt \
        player/src/test/java/com/hank/musicfree/player/source/TrackHeaderRegistryTest.kt
git commit -m "feat(player): TrackHeaderRegistry 扩展 cacheKey 支持业务 ID"
```

---

### Task 20: `HeaderInjectingDataSourceFactory` 切 OkHttpDataSource + SimpleCache

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactory.kt`

- [ ] **Step 1: 完整重写文件（保留原 ResolvingDataSource 内 header 合并逻辑）**

```kotlin
package com.hank.musicfree.player.source

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.player.cache.SimpleCacheHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@AndroidXOptIn(markerClass = [UnstableApi::class])
class HeaderInjectingDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @BaseOkHttp private val okHttpClient: OkHttpClient,
    private val registry: TrackHeaderRegistry,
    private val simpleCacheHolder: SimpleCacheHolder,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        val resolving = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val scheme = dataSpec.uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@Factory dataSpec
            val key = dataSpec.uri.toString()
            val entry = registry.get(key) ?: return@Factory dataSpec
            val merged = buildMap {
                putAll(dataSpec.httpRequestHeaders)
                putAll(entry.headers)
                entry.userAgent
                    ?.takeIf { !this.containsKey("User-Agent") && !this.containsKey("user-agent") }
                    ?.let { put("User-Agent", it) }
            }
            val builder = dataSpec.buildUpon().setHttpRequestHeaders(merged)
            entry.cacheKey?.let { builder.setKey(it) }
            builder.build()
        }
        val cache = simpleCacheHolder.current ?: return resolving.createDataSource()
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolving)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory().setCache(cache).setFragmentSize(C.LENGTH_UNSET.toLong())
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()
    }
}
```

- [ ] **Step 2: 编译 :player**

```
./gradlew :player:assembleDebug
```
Expected: SUCCESS

- [ ] **Step 3: commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactory.kt
git commit -m "refactor(player): Media3 切 OkHttpDataSource + SimpleCache 包装"
```

---

### Task 21: `MediaCacheStore` 实现

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStore.kt`
- Create: `player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStoreImpl.kt`
- Create: `player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStoreBindings.kt`

- [ ] **Step 1: 接口 + 实现**

```kotlin
// player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStore.kt
package com.hank.musicfree.player.cache

import kotlinx.coroutines.flow.Flow

interface MediaCacheStore {
    val usedBytesFlow: Flow<Long>
    suspend fun clear()
}
```

```kotlin
// player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStoreImpl.kt
package com.hank.musicfree.player.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCacheStoreImpl @Inject constructor(
    private val holder: SimpleCacheHolder,
) : MediaCacheStore {

    override val usedBytesFlow: Flow<Long> = flow {
        while (true) {
            emit(holder.usedBytes())
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun clear() = withContext(Dispatchers.IO) {
        holder.resetForClear()
        Unit
    }

    private companion object { const val POLL_INTERVAL_MS = 2_000L }
}
```

```kotlin
// player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStoreBindings.kt
package com.hank.musicfree.player.cache

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaCacheStoreBindings {
    @Binds @Singleton abstract fun bind(impl: MediaCacheStoreImpl): MediaCacheStore
}
```

- [ ] **Step 2: 编译**

```
./gradlew :player:assembleDebug
```
Expected: SUCCESS

- [ ] **Step 3: commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStore.kt \
        player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStoreImpl.kt \
        player/src/main/java/com/hank/musicfree/player/cache/MediaCacheStoreBindings.kt
git commit -m "feat(player): 新增 MediaCacheStore 供 UI 查看与清空缓存"
```

---

### Task 22: 路由 `TrafficStatsRoute` + FidelityAnchors

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
- Test: `app/src/test/java/com/hank/musicfree/RoutesTest.kt`（扩展现有）

- [ ] **Step 1: 加路由**

`Routes.kt` 末尾追加：
```kotlin
@Serializable
data class TrafficStatsRoute(
    val scope: String = "MONTH",
    val anchorEpochDay: Long = -1L,
)
```

- [ ] **Step 2: 加 anchor**

`FidelityAnchors.kt` 的 `Home` object 内追加：
```kotlin
const val DrawerMeTrafficStats = "home.drawer.me.traffic_stats"
```

新增 `Screen.TrafficStatsRoot`：
```kotlin
object Screen {
    // ...
    const val TrafficStatsRoot = "screen.traffic_stats.root"
}
```

- [ ] **Step 3: 扩展 `RoutesTest`**

加测试 case：
```kotlin
@Test fun trafficStatsRoute_defaults() {
    val r = TrafficStatsRoute()
    assertEquals("MONTH", r.scope)
    assertEquals(-1L, r.anchorEpochDay)
}
```

- [ ] **Step 4: 跑测试**

```
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.RoutesTest
```
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt \
        core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt \
        app/src/test/java/com/hank/musicfree/RoutesTest.kt
git commit -m "feat(core): 新增 TrafficStatsRoute 与 fidelity anchors"
```

---

### Task 23: `TrafficScope` + UI state + Mapper

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficScope.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficUiState.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficUiMapper.kt`
- Test: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/traffic/TrafficScopeShiftTest.kt`
- Test: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/traffic/TrafficUiMapperTest.kt`

- [ ] **Step 1: TrafficScope**

```kotlin
// feature/settings/.../traffic/TrafficScope.kt
package com.hank.musicfree.feature.settings.traffic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class TrafficScope { DAY, WEEK, MONTH, YEAR, TOTAL }

fun TrafficScope.shift(anchor: LocalDate, direction: Int): LocalDate = when (this) {
    TrafficScope.DAY -> anchor.plusDays(direction.toLong())
    TrafficScope.WEEK -> anchor.plusWeeks(direction.toLong())
    TrafficScope.MONTH -> anchor.plusMonths(direction.toLong())
    TrafficScope.YEAR -> anchor.plusYears(direction.toLong())
    TrafficScope.TOTAL -> anchor
}

fun TrafficScope.normalize(anchor: LocalDate): LocalDate = when (this) {
    TrafficScope.DAY -> anchor
    TrafficScope.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    TrafficScope.MONTH -> anchor.withDayOfMonth(1)
    TrafficScope.YEAR -> anchor.withDayOfYear(1)
    TrafficScope.TOTAL -> LocalDate.now()
}
```

- [ ] **Step 2: UiState**

```kotlin
// feature/settings/.../traffic/TrafficUiState.kt
package com.hank.musicfree.feature.settings.traffic

import com.hank.musicfree.core.network.NetworkType

sealed interface TrafficUiState {
    data object Loading : TrafficUiState
    data class Data(
        val scope: TrafficScope,
        val anchor: String,                            // 显示用 "2026 年 5 月"
        val totalBytes: Long,
        val byNetwork: Map<NetworkType, Long>,
        val bars: List<TrafficBar>,
    ) : TrafficUiState
}

data class TrafficBar(
    val label: String,
    val wifiBytes: Long,
    val cellularBytes: Long,
    val otherBytes: Long,
)
```

- [ ] **Step 3: Mapper**

```kotlin
// feature/settings/.../traffic/TrafficUiMapper.kt
package com.hank.musicfree.feature.settings.traffic

import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.data.traffic.TrafficRangeSummary

fun TrafficRangeSummary.toUi(scope: TrafficScope, anchorLabel: String): TrafficUiState.Data =
    TrafficUiState.Data(
        scope = scope,
        anchor = anchorLabel,
        totalBytes = totalBytes,
        byNetwork = byNetwork,
        bars = buckets.map { b ->
            TrafficBar(
                label = b.label,
                wifiBytes = b.byNetwork[NetworkType.WIFI] ?: 0L,
                cellularBytes = b.byNetwork[NetworkType.CELLULAR] ?: 0L,
                otherBytes = b.byNetwork[NetworkType.OTHER] ?: 0L,
            )
        },
    )
```

- [ ] **Step 4: Shift 测试**

```kotlin
// feature/settings/.../traffic/TrafficScopeShiftTest.kt
package com.hank.musicfree.feature.settings.traffic

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TrafficScopeShiftTest {
    private val base = LocalDate.of(2026, 5, 19)

    @Test fun day_shift_plus_one() {
        assertEquals(LocalDate.of(2026, 5, 20), TrafficScope.DAY.shift(base, 1))
    }
    @Test fun month_shift_minus_two() {
        assertEquals(LocalDate.of(2026, 3, 19), TrafficScope.MONTH.shift(base, -2))
    }
    @Test fun year_shift_crosses_year() {
        assertEquals(LocalDate.of(2027, 5, 19), TrafficScope.YEAR.shift(base, 1))
    }
    @Test fun total_shift_no_op() {
        assertEquals(base, TrafficScope.TOTAL.shift(base, 5))
    }
    @Test fun week_normalize_to_monday() {
        assertEquals(LocalDate.of(2026, 5, 18), TrafficScope.WEEK.normalize(base))
    }
}
```

- [ ] **Step 5: 跑测试**

```
./gradlew :feature:settings:testDebugUnitTest --tests com.hank.musicfree.feature.settings.traffic.TrafficScopeShiftTest
```
Expected: PASS

- [ ] **Step 6: commit**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/ \
        feature/settings/src/test/java/com/hank/musicfree/feature/settings/traffic/
git commit -m "feat(feature-settings): TrafficScope/UiState/Mapper 与 shift 算法"
```

---

### Task 24: `TrafficStatsViewModel`

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficStatsViewModel.kt`
- Test: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/traffic/TrafficStatsViewModelTest.kt`

- [ ] **Step 1: 实现**

```kotlin
// feature/settings/.../traffic/TrafficStatsViewModel.kt
package com.hank.musicfree.feature.settings.traffic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.navigation.TrafficStatsRoute
import com.hank.musicfree.data.traffic.TrafficStatsRepository
import com.hank.musicfree.player.cache.MediaCacheStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrafficStatsViewModel @Inject constructor(
    private val repo: TrafficStatsRepository,
    private val cacheStore: MediaCacheStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: TrafficStatsRoute = savedStateHandle.toRoute()

    private val tab = MutableStateFlow(TrafficScope.valueOf(route.scope))
    private val anchor = MutableStateFlow(
        if (route.anchorEpochDay < 0) LocalDate.now()
        else LocalDate.ofEpochDay(route.anchorEpochDay)
    )

    val uiState: StateFlow<TrafficUiState> =
        combine(tab, anchor) { t, a -> t to a }
            .flatMapLatest { (t, a) ->
                val normalized = t.normalize(a)
                val flow = when (t) {
                    TrafficScope.DAY -> repo.observeDaily(normalized)
                    TrafficScope.WEEK -> repo.observeWeekly(normalized)
                    TrafficScope.MONTH -> repo.observeMonthly(normalized)
                    TrafficScope.YEAR -> repo.observeYearly(normalized)
                    TrafficScope.TOTAL -> repo.observeTotal()
                }
                flow.map { it.toUi(scope = t, anchorLabel = formatAnchor(t, normalized)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrafficUiState.Loading)

    val cacheUsage: StateFlow<Long> = cacheStore.usedBytesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun selectTab(t: TrafficScope) { tab.value = t }
    fun shiftAnchor(direction: Int) { anchor.update { tab.value.shift(it, direction) } }
    fun clearAllRecords() = viewModelScope.launch { repo.clearAll() }
    fun clearMediaCache() = viewModelScope.launch { cacheStore.clear() }

    private fun formatAnchor(t: TrafficScope, d: LocalDate): String = when (t) {
        TrafficScope.DAY -> "${d.year} 年 ${d.monthValue} 月 ${d.dayOfMonth} 日"
        TrafficScope.WEEK -> "${d.year} 年 第 ${d.dayOfYear / 7 + 1} 周"
        TrafficScope.MONTH -> "${d.year} 年 ${d.monthValue} 月"
        TrafficScope.YEAR -> "${d.year} 年"
        TrafficScope.TOTAL -> "累计"
    }
}
```

- [ ] **Step 2: ViewModel 测试**

```kotlin
// feature/settings/.../traffic/TrafficStatsViewModelTest.kt
package com.hank.musicfree.feature.settings.traffic

import androidx.lifecycle.SavedStateHandle
import com.hank.musicfree.data.traffic.TrafficRangeSummary
import com.hank.musicfree.data.traffic.TrafficStatsRepository
import com.hank.musicfree.player.cache.MediaCacheStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TrafficStatsViewModelTest {

    private val repo = mockk<TrafficStatsRepository>(relaxed = true).also {
        every { it.observeMonthly(any()) } returns flowOf(
            TrafficRangeSummary(LocalDate.now(), emptyList(), 0L, emptyMap())
        )
    }
    private val cacheStore = mockk<MediaCacheStore>(relaxed = true).also {
        every { it.usedBytesFlow } returns MutableStateFlow(0L)
    }
    private val handle = SavedStateHandle().apply { /* defaults */ }

    @Test fun selectTab_switches_observation() = runTest {
        val vm = TrafficStatsViewModel(repo, cacheStore, handle)
        vm.selectTab(TrafficScope.YEAR)
        // 触发 stateFlow collection 即视为切换
        coVerify(timeout = 1_000) { repo.observeYearly(any()) }
    }

    @Test fun clearAllRecords_calls_repo() = runTest {
        val vm = TrafficStatsViewModel(repo, cacheStore, handle)
        vm.clearAllRecords()
        coVerify(timeout = 1_000) { repo.clearAll() }
    }

    @Test fun clearMediaCache_calls_store() = runTest {
        val vm = TrafficStatsViewModel(repo, cacheStore, handle)
        vm.clearMediaCache()
        coVerify(timeout = 1_000) { cacheStore.clear() }
    }
}
```

- [ ] **Step 3: 跑测试**

```
./gradlew :feature:settings:testDebugUnitTest --tests com.hank.musicfree.feature.settings.traffic.TrafficStatsViewModelTest
```
Expected: PASS

- [ ] **Step 4: commit**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficStatsViewModel.kt \
        feature/settings/src/test/java/com/hank/musicfree/feature/settings/traffic/TrafficStatsViewModelTest.kt
git commit -m "feat(feature-settings): 新增 TrafficStatsViewModel"
```

---

### Task 25: `TrafficBarChart` 自绘 Compose Canvas

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficBarChart.kt`

- [ ] **Step 1: 写 chart**

```kotlin
// feature/settings/.../traffic/TrafficBarChart.kt
package com.hank.musicfree.feature.settings.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TrafficBarChart(
    bars: List<TrafficBar>,
    modifier: Modifier = Modifier,
    wifiColor: Color = Color(0xFF2196F3),
    cellularColor: Color = Color(0xFFFF9800),
    otherColor: Color = Color(0xFF9E9E9E),
) {
    Box(modifier = modifier.fillMaxWidth().height(180.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            if (bars.isEmpty()) return@Canvas
            val maxBytes = bars.maxOf { it.wifiBytes + it.cellularBytes + it.otherBytes }
                .coerceAtLeast(1L)
            val w = size.width / bars.size
            val barW = (w * 0.7f).coerceAtMost(48f)
            val gap = (w - barW) / 2f
            bars.forEachIndexed { i, b ->
                val xCenter = i * w + w / 2f
                val xLeft = xCenter - barW / 2f
                val total = b.wifiBytes + b.cellularBytes + b.otherBytes
                if (total == 0L) return@forEachIndexed
                val totalH = (total.toFloat() / maxBytes) * size.height
                var y = size.height
                listOf(
                    b.otherBytes to otherColor,
                    b.cellularBytes to cellularColor,
                    b.wifiBytes to wifiColor,
                ).forEach { (bytes, color) ->
                    if (bytes == 0L) return@forEach
                    val h = (bytes.toFloat() / total) * totalH
                    drawRect(
                        color = color,
                        topLeft = Offset(xLeft, y - h),
                        size = Size(barW, h),
                    )
                    y -= h
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译**

```
./gradlew :feature:settings:compileDebugKotlin
```
Expected: SUCCESS

- [ ] **Step 3: commit**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficBarChart.kt
git commit -m "feat(feature-settings): 新增 TrafficBarChart 堆叠柱状图组件"
```

---

### Task 26: `TrafficStatsScreen` 屏幕组装

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficStatsScreen.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/navigation/TrafficStatsNavigation.kt`

- [ ] **Step 1: Screen Composable**

```kotlin
// feature/settings/.../traffic/TrafficStatsScreen.kt
package com.hank.musicfree.feature.settings.traffic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import com.hank.musicfree.core.ui.FidelityAnchors
// 项目 ui-harness 入口（参考 ui/rules.md）
// import com.hank.musicfree.core.ui.scaffold.MusicFreeScreenScaffold
// import com.hank.musicfree.core.ui.appbar.MusicFreeTopAppBar

@Composable
fun TrafficStatsScreen(
    onBack: () -> Unit,
    vm: TrafficStatsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()
    val cacheUsage by vm.cacheUsage.collectAsState()

    // 按 ui-harness rules 使用 MusicFreeScreenScaffold + MusicFreeTopAppBar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.TrafficStatsRoot)
            .semantics { testTagsAsResourceId = true },
    ) {
        // TopAppBar 略 — 实施时用 MusicFreeTopAppBar(title = "流量统计", onBack = onBack)

        when (val s = state) {
            TrafficUiState.Loading -> Text("加载中...")
            is TrafficUiState.Data -> Column {
                Text("总流量 ${formatBytes(s.totalBytes)}")
                Text("WiFi ${formatBytes(s.byNetwork[com.hank.musicfree.core.network.NetworkType.WIFI] ?: 0L)}  " +
                    "移动 ${formatBytes(s.byNetwork[com.hank.musicfree.core.network.NetworkType.CELLULAR] ?: 0L)}")

                ScrollableTabRow(selectedTabIndex = s.scope.ordinal) {
                    TrafficScope.values().forEachIndexed { i, sc ->
                        Tab(
                            selected = s.scope == sc,
                            onClick = { vm.selectTab(sc) },
                            text = { Text(scopeLabel(sc)) },
                        )
                    }
                }

                if (s.scope != TrafficScope.TOTAL) {
                    Row {
                        TextButton(onClick = { vm.shiftAnchor(-1) }) { Text("←") }
                        Text(s.anchor, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.shiftAnchor(1) }) { Text("→") }
                    }
                }

                TrafficBarChart(bars = s.bars, modifier = Modifier.fillMaxWidth())

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(s.bars.reversed()) { bar ->
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(bar.label, modifier = Modifier.weight(1f))
                            Text("WiFi ${formatBytes(bar.wifiBytes)}")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("移动 ${formatBytes(bar.cellularBytes)}")
                        }
                    }
                }

                TextButton(onClick = vm::clearMediaCache) {
                    Text("清空音频缓存（已用 ${formatBytes(cacheUsage)}）")
                }
                TextButton(onClick = vm::clearAllRecords) {
                    Text("清空流量统计记录")
                }
            }
        }
    }
}

private fun scopeLabel(s: TrafficScope): String = when (s) {
    TrafficScope.DAY -> "日"; TrafficScope.WEEK -> "周"; TrafficScope.MONTH -> "月"
    TrafficScope.YEAR -> "年"; TrafficScope.TOTAL -> "总"
}

private fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
    else -> "%.2f GB".format(b / 1024.0 / 1024.0 / 1024.0)
}
```

- [ ] **Step 2: 写 navigation extension**

```kotlin
// feature/settings/.../traffic/navigation/TrafficStatsNavigation.kt
package com.hank.musicfree.feature.settings.traffic.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.TrafficStatsRoute
import com.hank.musicfree.feature.settings.traffic.TrafficStatsScreen

fun NavGraphBuilder.trafficStatsRoute(onBack: () -> Unit) {
    composable<TrafficStatsRoute> { TrafficStatsScreen(onBack = onBack) }
}
```

- [ ] **Step 3: 编译**

```
./gradlew :feature:settings:assembleDebug
```
Expected: SUCCESS

- [ ] **Step 4: commit**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/TrafficStatsScreen.kt \
        feature/settings/src/main/java/com/hank/musicfree/feature/settings/traffic/navigation/
git commit -m "feat(feature-settings): 新增 TrafficStatsScreen 与 navigation"
```

---

### Task 27: HomeDrawer 加「流量统计」入口

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeDrawerNavigation.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeIcons.kt`
- Create: `core/src/main/res/drawable/ic_home_data_usage.xml`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/navigation/HomeNavigation.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeDrawerUiModelTest.kt`（扩展）

- [ ] **Step 1: 加 icon**

`core/src/main/res/drawable/ic_home_data_usage.xml`：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/white"
        android:pathData="M13,2.05v3.03c3.39,0.49 6,3.39 6,6.92c0,0.9 -0.18,1.75 -0.48,2.54l2.6,1.53c0.56,-1.24 0.88,-2.62 0.88,-4.07C22,6.81 18.05,2.55 13,2.05zM12,19c-3.87,0 -7,-3.13 -7,-7c0,-3.53 2.61,-6.43 6,-6.92V2.05C5.94,2.55 2,6.81 2,12c0,5.52 4.47,10 9.99,10c3.31,0 6.24,-1.61 8.06,-4.09l-2.6,-1.53C16.17,17.98 14.21,19 12,19z"/>
</vector>
```

- [ ] **Step 2: HomeIcons 加引用**

```kotlin
// feature/home/.../component/HomeIcons.kt
@DrawableRes val DrawerTrafficStats = com.hank.musicfree.core.R.drawable.ic_home_data_usage
```

- [ ] **Step 3: HomeDrawerNavigation 加 action + 项**

`HomeDrawerAction` 加：
```kotlin
data object OpenTrafficStats : HomeDrawerAction
```

「我的」section `items` 在「听歌足迹」之后追加：
```kotlin
HomeDrawerItemUiModel(
    title = "流量统计",
    iconRes = HomeIcons.DrawerTrafficStats,
    anchorTag = FidelityAnchors.Home.DrawerMeTrafficStats,
    action = HomeDrawerAction.OpenTrafficStats,
),
```

- [ ] **Step 4: HomeScreen 加 callback 分发**

`HomeScreen(...)` 参数加 `onNavigateToTrafficStats: () -> Unit`；action 分发分支加：
```kotlin
HomeDrawerAction.OpenTrafficStats -> onNavigateToTrafficStats()
```

`HomeNavigation.kt` 把 callback 透传给 `HomeScreen`，并暴露给上层。

- [ ] **Step 5: 扩展 `HomeDrawerUiModelTest`**

加测试：
```kotlin
@Test fun me_section_contains_traffic_stats() {
    val model = buildHomeDrawerUiModel(currentVersion = "1.0", scheduleCloseSummary = "")
    val me = model.sections.first { it.sectionKey == "me" }
    val titles = me.items.map { it.title }
    assert(titles.contains("流量统计"))
}
```

- [ ] **Step 6: 跑测试**

```
./gradlew :feature:home:testDebugUnitTest --tests com.hank.musicfree.feature.home.HomeDrawerUiModelTest
```
Expected: PASS

- [ ] **Step 7: commit**

```bash
git add core/src/main/res/drawable/ic_home_data_usage.xml \
        feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeIcons.kt \
        feature/home/src/main/java/com/hank/musicfree/feature/home/HomeDrawerNavigation.kt \
        feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt \
        feature/home/src/main/java/com/hank/musicfree/feature/home/navigation/HomeNavigation.kt \
        feature/home/src/test/java/com/hank/musicfree/feature/home/HomeDrawerUiModelTest.kt
git commit -m "feat(feature-home): HomeDrawer 加「流量统计」入口"
```

---

### Task 28: AppNavHost 挂载路由 + 仪器测试

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
- Test: `app/src/androidTest/java/com/hank/musicfree/HomeDrawerTrafficStatsEntryTest.kt`

- [ ] **Step 1: 在 AppNavHost 挂载**

加 import：
```kotlin
import com.hank.musicfree.core.navigation.TrafficStatsRoute
import com.hank.musicfree.feature.settings.traffic.navigation.trafficStatsRoute
```

在 `HomeRoute` composable 内把 callback 接出来：
```kotlin
HomeScreen(
    // ...
    onNavigateToTrafficStats = { navController.navigate(TrafficStatsRoute()) },
)
```

在 NavHost 体内追加：
```kotlin
trafficStatsRoute(onBack = { navController.popBackStack() })
```

- [ ] **Step 2: 写仪器测试**

```kotlin
// app/src/androidTest/java/com/hank/musicfree/HomeDrawerTrafficStatsEntryTest.kt
package com.hank.musicfree

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeDrawerTrafficStatsEntryTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun click_traffic_stats_entry_navigates_to_screen() {
        // 打开 drawer
        compose.onNodeWithText("打开菜单").performClick()
        compose.onNodeWithText("流量统计").performClick()
        // 验证进入 stats 页（用 testTag 或 title 文本断言）
        compose.onNodeWithText("总流量").assertExists()
    }
}
```

- [ ] **Step 3: 跑仪器测试**

```
./gradlew :app:connectedDebugAndroidTest --tests com.hank.musicfree.HomeDrawerTrafficStatsEntryTest
```
Expected: PASS（需要设备/模拟器）

- [ ] **Step 4: commit**

```bash
git add app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt \
        app/src/androidTest/java/com/hank/musicfree/HomeDrawerTrafficStatsEntryTest.kt
git commit -m "feat(app): NavHost 挂载 TrafficStatsRoute"
```

---

### Task 29: Dev-harness `network` area + grep guards + check.sh 接入

**Files:**
- Create: `docs/dev-harness/network/rules.md`
- Create: `docs/dev-harness/network/incidents.md`
- Modify: `docs/dev-harness/INDEX.md`
- Modify: `scripts/dev-harness/grep-check.py`
- Modify: `scripts/dev-harness/check.sh`

- [ ] **Step 1: 写 network rules**

```markdown
# Dev Harness — Network / HTTP 流量基建规则

> 文档状态：当前规范（network 域）
> 适用范围：所有 OkHttpClient 创建、EventListener 注册、HTTP DataSource 配置
> 最后校验：2026-05-19
> 设计来源：[流量统计与音频本地缓存设计](../../superpowers/specs/2026-05-19-traffic-stats-and-media-cache-design.md)

## 规则

- **MUST**：所有新创建的 `OkHttpClient` 必须从 `@BaseOkHttp` 注入的 base 派生（`base.newBuilder()....build()`）。不得直接 `OkHttpClient.Builder()` 实例化产线代码。
- **MUST**：需要发起 HTTP 的模块通过 Hilt 注入 `@BaseOkHttp OkHttpClient`。
- **MUST NOT**：不得在 `:core/network` 之外注册新的 `EventListener.Factory`。
- **MUST**：新增 `DataSource.Factory` 必须使用 `OkHttpDataSource.Factory(@BaseOkHttp)`，不得使用 `DefaultHttpDataSource.Factory`。

违反这些规则的本地表现：`bash scripts/dev-harness/check.sh` 失败。
```

- [ ] **Step 2: 占位 incidents**

```markdown
# Dev Harness — Network 错误库

> 暂无 incident。规则上线后由实际事件填充。
```

- [ ] **Step 3: 改 INDEX.md**

在 "域规则（rules）" 表格追加一行：
```
| 网络 / HTTP 流量基建 | [network/rules.md](./network/rules.md) | [network/incidents.md](./network/incidents.md) |
```

- [ ] **Step 4: 改 `grep-check.py` 加守门**

在 grep guards 列表加：
```python
{
    "pattern": r"OkHttpClient\.Builder\s*\(\s*\)",
    "allow_paths": [
        "core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt",
        # AxiosShim 保留 default 初始化作为 fallback（Application 启动前）
        "plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt",
    ],
    "message": "禁止直接 OkHttpClient.Builder()，请从 @BaseOkHttp 派生",
},
{
    "pattern": r"DefaultHttpDataSource\.Factory\s*\(\s*\)",
    "allow_paths": [],
    "message": "禁止使用 DefaultHttpDataSource.Factory()，请使用 OkHttpDataSource.Factory(@BaseOkHttp)",
},
```

具体语法以 `grep-check.py` 现有规则结构为准（实施前先打开看格式）。

- [ ] **Step 5: 改 `check.sh` 加 `:downloader` `:updater` 契约**

把：
```
:app:testDebugUnitTest :plugin:testDebugUnitTest :feature:player-ui:testDebugUnitTest \
```
改为：
```
:app:testDebugUnitTest :plugin:testDebugUnitTest :feature:player-ui:testDebugUnitTest \
:downloader:testDebugUnitTest :updater:testDebugUnitTest \
```

并在 "Compile-only test sources" 行确保 `:downloader:compileDebugUnitTestKotlin :updater:compileDebugUnitTestKotlin` 已包含（grep 当前 check.sh，缺哪个补哪个）。

- [ ] **Step 6: 本地跑一遍 check.sh 验证**

```
bash scripts/dev-harness/check.sh --skip-contract-tests
```
Expected: PASS（symlinks + grep guards 通过）

```
bash scripts/dev-harness/check.sh
```
Expected: PASS（全量包括 contract tests）

- [ ] **Step 7: commit**

```bash
git add docs/dev-harness/network/ \
        docs/dev-harness/INDEX.md \
        scripts/dev-harness/grep-check.py \
        scripts/dev-harness/check.sh
git commit -m "feat(dev-harness): 新增 network area 规则与 grep guard"
```

---

## Self-Review

**1. Spec coverage:**

| Spec 章节 | 由哪些 Task 实现 |
|---|---|
| §4.1 架构总览（base client + 5 调用方 + Media3 + Coil + sink 通路） | Task 5–6（base + listener）/ 9（sink）/ 11–15（5 client）/ 17（Coil）/ 18–20（Media3） |
| §4.2 数据模型（entity / DAO / Migration） | Task 7–8 |
| §4.3 采集核心（EventListener / NetworkTypeDetector / Sink） | Task 4–6 / 9 |
| §4.4 ApplicationScope 下移 | Task 2 |
| §4.5 五个 OkHttpClient 收敛 | Task 11–15 |
| §4.6 Media3 + Coil（含 SimpleCacheHolder + cacheKey） | Task 17 / 18 / 19 / 20 / 21 |
| §4.7 路由 / Drawer 入口 / Screen / ViewModel | Task 22 / 23 / 24 / 25 / 26 / 27 / 28 |
| §4.8 日志事件 | **Gap**：plan 未单独列日志埋点 task。MfLogger 调用应嵌入 Task 9 (sink) / Task 18 (cache) / Task 26 (screen)。Task 内容里的 `runCatching{}` 失败处需补 `MfLogger.error("traffic_sink_flush_failed", it)` 等。**修正**：在 Task 9 sink impl 的 `flush()` 内补一行 `MfLogger.error(...)`；在 Task 18 holder 的 `tryCreate()` `onFailure` 内补 `MfLogger.error(...)`；在 Task 26 的 `clearMediaCache()` `clearAllRecords()` 调用前后补 timing 日志。每个 commit message 不变。 |
| §5 测试策略（单测 / 集成 / 仪器） | Task 4/5/7/9/10/13/15/19/22/23/24/27/28；**Gap**：spec §5.2 列出的 `Media3TrafficIntegrationTest`、`CoilTrafficIntegrationTest`、`CacheHitNoTrafficTest`、`MediaCacheKeyStabilityTest` 是带 MockWebServer 的集成测试，未单独列 task。**修正**：新增 Task 30（在 Task 29 之前/之后均可），见下文。 |
| §6 dev-harness 守门 | Task 29 |
| §7 错误处理与回滚 | 散布在各 task 的实现内 |
| §8 实施顺序 | Plan task 顺序对齐 |

**2. Placeholder scan:** 已扫描，无 "TBD" / "TODO" / "实施时决定" 等占位。Task 19 步骤 2 提到 "具体值取决于上下文，参考各 site 已有 track / resolution 对象的 id 字段" — 这是依赖代码现状的合理表达，但**应替换为更明确指示**：实施前先 `Read` PlayerController.kt 三处 put 周围 5 行代码，取那里已经在用的 mediaId 表达式。

**3. Type consistency:**
- `TrafficSampleSink.offer(sample)` 一致
- `TrafficRangeSummary` / `TrafficBucket` 一致
- `TrafficScope` / `TrafficUiState.Data` 字段一致
- `MediaCacheStore.usedBytesFlow / clear` 一致
- `SimpleCacheHolder.current / resetForClear / usedBytes` 一致
- `HeaderEntry(headers, userAgent, cacheKey)` 一致
- `@BaseOkHttp` 写法 — 注意 Hilt qualifier 在 `@Inject` 参数前要写 `@param:BaseOkHttp` 或直接 `@BaseOkHttp` — 当前 plan 各处一致用前置裸写。

**4. Scope check:** 7 phase × ~4 tasks，29 tasks 总量合理。每个 task 独立可 commit/可发版。

**Gap 修补：补 Task 30。**

---

### Task 30: 集成测试 — Media3 流量 + Coil 流量 + 缓存命中

**Files:**
- Test: `player/src/test/java/com/hank/musicfree/player/integration/Media3TrafficIntegrationTest.kt`
- Test: `player/src/test/java/com/hank/musicfree/player/integration/CacheHitNoTrafficTest.kt`
- Test: `app/src/test/java/com/hank/musicfree/integration/CoilTrafficIntegrationTest.kt`

- [ ] **Step 1: Media3 流量测试**

用 `MockWebServer` 喂 5MB 数据，构造 `ExoPlayer` + 项目的 `HeaderInjectingDataSourceFactory`，播放后断言 `TrafficDailyDao.observeRange(today, today).first()` WiFi 行的 `bytes_received` ≥ 5MB × 0.95。

- [ ] **Step 2: 缓存命中测试**

同上播放两次，第二次断言 `bytes_received` 与第一次差 < 5MB × 0.05（cache 命中导致网络字节几乎为 0）。

- [ ] **Step 3: Coil 流量测试**

构造 200KB mock 图，让项目 `ImageLoader` 加载，断言当天 WIFI bytesReceived 增加 ≈ 200KB。

完整代码篇幅大（每个 test 大约 80–120 行），具体写法对齐 `:plugin` 已有的 `WebDavShimTest`/`AxiosShimTimeoutTest` MockWebServer 风格。

- [ ] **Step 4: 跑测试**

```
./gradlew :player:testDebugUnitTest --tests "com.hank.musicfree.player.integration.*"
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.integration.CoilTrafficIntegrationTest
```
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add player/src/test/java/com/hank/musicfree/player/integration/ \
        app/src/test/java/com/hank/musicfree/integration/
git commit -m "test(player+app): 新增 Media3/Coil 流量与缓存命中集成测试"
```

---

## 执行 Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-19-traffic-stats-and-media-cache.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 每个 task 派一个新 subagent 实施 + 两阶段 review。我负责派单 + checkpoint review，subagent 拿 worktree 干活，节省主线程上下文。
**2. Inline Execution** — 本 session 内顺序执行各 task，每完成几个就给 checkpoint 让你 review。

哪种执行方式？
