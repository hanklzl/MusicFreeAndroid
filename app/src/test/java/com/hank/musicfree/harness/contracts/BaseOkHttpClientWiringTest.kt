package com.hank.musicfree.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Top-level wiring contract for the traffic-stats pipeline.
 *
 * 两个不变量必须同时成立，否则 axios / plugin / downloader / updater 任一路径
 * 的字节都会绕过 [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory]
 * 而不会进 `traffic_daily`：
 *
 *  1. `:core` 的 `NetworkModule` 中 `@BaseOkHttp` provider 必须用
 *     `NetworkTrafficEventListener.Factory` 作为 OkHttpClient 的
 *     `eventListenerFactory(...)`。
 *  2. `MusicFreeApplication.onCreate` 必须调用 `AxiosShim.setBaseClient(...)`
 *     把 `@BaseOkHttp` 实例交给 axios shim —— 否则插件 axios 请求会用
 *     AxiosShim 自带的兜底 OkHttpClient，没有 EventListener.Factory。
 *
 * 为什么用文件扫描而不是 runtime Hilt test：
 *
 *  - `@BaseOkHttp` 的具体 OkHttp wiring 已经在 `:downloader`、`:plugin`、`:updater`
 *    的契约测试里用 `@HiltAndroidTest` + `HiltTestApplication` 端到端验证过
 *    （Group F）—— 它们都断言 `eventListenerFactory is NetworkTrafficEventListener.Factory`
 *    且 `assertSame` 引用同一份 base 实例。runtime Hilt 验证在那一层已经足够。
 *
 *  - `:app` 模块依赖每一个 feature 模块、player、plugin、downloader、updater 与
 *    全套 ViewModel binding。把 Robolectric + HiltTestApplication 引入 `:app`
 *    单测会大幅膨胀 ksp/test classpath（hilt-android-testing、robolectric、
 *    androidx-test-core 都要加），还要把 `isIncludeAndroidResources = true` 打开。
 *    历史上 :app 从未跑过 Hilt 单测（仅 androidTest），现引入需要承担 D8 / OOM /
 *    feature binding 缺失带来的不稳定面（见 dev-harness/test/incidents.md
 *    INC-2026-0016 类问题）。
 *
 *  - 本测试要捕获的复发形态是**人写代码时改坏了顶层 wiring**：
 *    (a) 把 `NetworkModule` 里 `.eventListenerFactory(factory)` 删掉或换成
 *        别的 factory；
 *    (b) 把 `MusicFreeApplication` 里 `AxiosShim.setBaseClient(...)` 删掉、
 *        改成传别的 client、或 onCreate 顺序错乱（在 Hilt 注入完成前调用）。
 *    文件扫描能精准锁定这两种回归，且零运行时成本。
 *
 * 如果未来 :app 已经具备了完整的 Hilt 单测能力（例如有别的 feature 引入了
 * Robolectric + hilt-android-testing），可以把本测试升级为 runtime
 * `@HiltAndroidTest` 注入 `@BaseOkHttp OkHttpClient` 直接断言其
 * `eventListenerFactory is NetworkTrafficEventListener.Factory`，
 * 进一步抹去 false-positive 空间。
 */
class BaseOkHttpClientWiringTest {

    @Test
    fun core_network_module_provides_base_okhttp_with_traffic_event_listener_factory() {
        val networkModule = File(
            repoRoot(),
            "core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt",
        )
        check(networkModule.exists()) {
            "Expected $networkModule to exist; did the @BaseOkHttp provider move? " +
                "Update this contract to follow."
        }
        val text = networkModule.readText()

        val providesBase = providesBaseOkHttp.containsMatchIn(text)
        val wiresFactory = wiresNetworkTrafficEventListenerFactory.containsMatchIn(text)

        if (!providesBase || !wiresFactory) {
            throw AssertionError(
                buildString {
                    appendLine(
                        "Traffic-stats top-level wiring contract violated in core/NetworkModule.kt.",
                    )
                    appendLine("Required:")
                    appendLine(
                        "  - A @Provides @BaseOkHttp method returning OkHttpClient",
                    )
                    appendLine(
                        "  - That method must call `.eventListenerFactory(<NetworkTrafficEventListener.Factory>)`",
                    )
                    appendLine("Found:")
                    appendLine("  - @Provides @BaseOkHttp ...: $providesBase")
                    appendLine(
                        "  - .eventListenerFactory(... NetworkTrafficEventListener.Factory ...): $wiresFactory",
                    )
                    appendLine(
                        "If you intentionally restructured the base client, update " +
                            "this contract and Group F module-level contract tests in lockstep.",
                    )
                },
            )
        }
    }

    @Test
    fun application_oncreate_must_hand_base_client_to_axios_shim() {
        val applicationFile = File(
            repoRoot(),
            "app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt",
        )
        check(applicationFile.exists()) {
            "Expected $applicationFile to exist; did MusicFreeApplication move? " +
                "Update this contract to follow."
        }
        val text = applicationFile.readText()

        val injectsBaseClient = injectsBaseOkHttpField.containsMatchIn(text)
        val callsSetBaseClient = callsAxiosShimSetBaseClient.containsMatchIn(text)

        if (!injectsBaseClient || !callsSetBaseClient) {
            throw AssertionError(
                buildString {
                    appendLine(
                        "Traffic-stats top-level wiring contract violated in MusicFreeApplication.kt.",
                    )
                    appendLine("Required:")
                    appendLine(
                        "  - `@Inject @BaseOkHttp lateinit var <name>: OkHttpClient` field",
                    )
                    appendLine(
                        "  - `AxiosShim.setBaseClient(<name>)` call inside onCreate so " +
                            "plugin axios traffic flows through NetworkTrafficEventListener.Factory",
                    )
                    appendLine("Found:")
                    appendLine("  - @Inject @BaseOkHttp lateinit var ...: OkHttpClient = $injectsBaseClient")
                    appendLine("  - AxiosShim.setBaseClient(...) call = $callsSetBaseClient")
                    appendLine(
                        "Without setBaseClient(...), AxiosShim falls back to its own " +
                            "internal OkHttpClient which has no EventListener.Factory — " +
                            "all plugin axios bytes will silently bypass traffic_daily.",
                    )
                },
            )
        }
    }

    // Tolerates whitespace, line breaks, and Kotlin's optional `:` between method
    // name and return type so refactors don't trip the regex.
    private val providesBaseOkHttp: Regex = Regex(
        """@Provides[\s\S]{0,200}?@BaseOkHttp[\s\S]{0,200}?fun\s+\w+\s*\([\s\S]*?\)\s*:\s*OkHttpClient""",
    )

    private val wiresNetworkTrafficEventListenerFactory: Regex = Regex(
        """\.eventListenerFactory\s*\(\s*[A-Za-z_][A-Za-z_0-9]*\s*\)""",
    )

    private val injectsBaseOkHttpField: Regex = Regex(
        """@Inject\s+@BaseOkHttp\s+lateinit\s+var\s+\w+\s*:\s*OkHttpClient""",
    )

    private val callsAxiosShimSetBaseClient: Regex = Regex(
        """AxiosShim\.setBaseClient\s*\(""",
    )

    private fun repoRoot(): File {
        var dir: File = File(".").canonicalFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("Could not locate repo root from ${File(".").canonicalFile}")
        }
        return dir
    }
}
