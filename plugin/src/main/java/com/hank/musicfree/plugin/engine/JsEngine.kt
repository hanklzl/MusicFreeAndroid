package com.hank.musicfree.plugin.engine

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import kotlinx.coroutines.Dispatchers

/**
 * Wrapper around [QuickJs] providing lifecycle management.
 *
 * All [evaluate] calls are suspend and internally dispatched by quickjs-kt.
 * The old evaluateAsync hack, jsObjectToMap, jsArrayToList, setGlobalMap
 * and manual thread management are no longer needed.
 */
class JsEngine private constructor(
    val quickJs: QuickJs,
) {

    companion object {
        /**
         * Create a new JsEngine backed by a fresh QuickJs context.
         * Uses a single-thread dispatcher for JS thread affinity.
         */
        fun create(): JsEngine {
            val qjs = QuickJs.create(Dispatchers.IO.limitedParallelism(1))
            // Register console object (quickjs-kt does not provide one by default)
            qjs.define("console") {
                function<Any?>("log") { args ->
                    logConsole(level = "log", args = args)
                }
                function<Any?>("warn") { args ->
                    logConsole(level = "warn", args = args)
                }
                function<Any?>("error") { args ->
                    logConsole(level = "error", args = args)
                }
                function<Any?>("info") { args ->
                    logConsole(level = "info", args = args)
                }
                function<Any?>("debug") { args ->
                    logConsole(level = "debug", args = args)
                }
            }
            return JsEngine(qjs)
        }

        private fun logConsole(level: String, args: Array<Any?>) {
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "js_console",
                fields = mapOf(
                    "level" to level,
                    "detail" to args.joinToString(" ") { it?.toString().orEmpty() },
                ),
            )
        }
    }

    /**
     * Evaluate JavaScript code and return the result.
     * Supports top-level `await` — Promises are automatically resolved.
     */
    suspend inline fun <reified T> evaluate(code: String): T {
        val startedAt = System.nanoTime()
        return try {
            val result = quickJs.evaluate<T>(code)
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "js_evaluate_success",
                fields = mapOf(
                    "operation" to "evaluate",
                    "result" to LogFields.Result.SUCCESS,
                    "durationMs" to elapsedMs(startedAt),
                    "scriptLength" to code.length,
                ),
            )
            result
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "js_evaluate_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "evaluate",
                    "result" to LogFields.Result.FAILURE,
                    "durationMs" to elapsedMs(startedAt),
                    "scriptLength" to code.length,
                ),
            )
            throw e
        }
    }

    /**
     * Evaluate JavaScript code, returning the result as a nullable type.
     * Returns null for JS `undefined` and `null`.
     */
    suspend fun evaluateOrNull(code: String): Any? {
        val startedAt = System.nanoTime()
        return try {
            val result = quickJs.evaluate<Any?>(code)
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "js_evaluate_success",
                fields = mapOf(
                    "operation" to "evaluate_or_null",
                    "result" to LogFields.Result.SUCCESS,
                    "durationMs" to elapsedMs(startedAt),
                    "scriptLength" to code.length,
                ),
            )
            result
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "js_evaluate_failed",
                throwable = e,
                fields = mapOf(
                    "operation" to "evaluate_or_null",
                    "status" to "failed",
                    "result" to LogFields.Result.FAILURE,
                    "durationMs" to elapsedMs(startedAt),
                    "scriptLength" to code.length,
                ),
            )
            null
        }
    }

    /**
     * Register a synchronous global function callable from JS.
     */
    inline fun <reified R : Any?> function(
        name: String,
        crossinline block: (args: Array<Any?>) -> R,
    ) {
        quickJs.function(name) { args: Array<Any?> -> block(args) }
    }

    /**
     * Register an async global function callable from JS via `await`.
     * The [block] is a suspend function — JS thread is released during execution.
     */
    inline fun <reified R> asyncFunction(
        name: String,
        crossinline block: suspend (args: Array<Any?>) -> R,
    ) {
        quickJs.asyncFunction(name) { args: Array<Any?> -> block(args) }
    }

    /**
     * Define a named JS object with nested functions/properties.
     */
    fun define(name: String, block: com.dokar.quickjs.binding.ObjectBindingScope.() -> Unit) {
        quickJs.define(name, block)
    }

    /** Close the QuickJs context and release all resources. */
    fun close() {
        try {
            quickJs.close()
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_error",
                throwable = e,
                fields = mapOf(
                    "operation" to "close",
                    "status" to "failed",
                ),
            )
        }
    }

    val isClosed: Boolean get() = quickJs.isClosed
}

@PublishedApi
internal fun elapsedMs(startedAt: Long): Long =
    (System.nanoTime() - startedAt) / 1_000_000
