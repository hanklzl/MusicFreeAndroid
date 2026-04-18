package com.zili.android.musicfreeandroid.plugin.engine

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
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
        private const val TAG = "JsEngine"

        /**
         * Create a new JsEngine backed by a fresh QuickJs context.
         * Uses a single-thread dispatcher for JS thread affinity.
         */
        fun create(): JsEngine {
            val qjs = QuickJs.create(Dispatchers.IO.limitedParallelism(1))
            // Register console object (quickjs-kt does not provide one by default)
            qjs.define("console") {
                function<Any?>("log") { args ->
                    Log.i("JSConsole", args.joinToString(" "))
                }
                function<Any?>("warn") { args ->
                    Log.w("JSConsole", args.joinToString(" "))
                }
                function<Any?>("error") { args ->
                    Log.e("JSConsole", args.joinToString(" "))
                }
                function<Any?>("info") { args ->
                    Log.i("JSConsole", args.joinToString(" "))
                }
                function<Any?>("debug") { args ->
                    Log.d("JSConsole", args.joinToString(" "))
                }
            }
            return JsEngine(qjs)
        }
    }

    /**
     * Evaluate JavaScript code and return the result.
     * Supports top-level `await` — Promises are automatically resolved.
     */
    suspend inline fun <reified T> evaluate(code: String): T {
        return quickJs.evaluate<T>(code)
    }

    /**
     * Evaluate JavaScript code, returning the result as a nullable type.
     * Returns null for JS `undefined` and `null`.
     */
    suspend fun evaluateOrNull(code: String): Any? {
        return try {
            quickJs.evaluate<Any?>(code)
        } catch (e: Exception) {
            Log.w(TAG, "evaluateOrNull failed", e)
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
            Log.w(TAG, "Error closing QuickJs context", e)
        }
    }

    val isClosed: Boolean get() = quickJs.isClosed
}
