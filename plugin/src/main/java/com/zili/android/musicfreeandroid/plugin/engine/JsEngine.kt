package com.zili.android.musicfreeandroid.plugin.engine

import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext

/**
 * Wrapper around [QuickJSContext] providing lifecycle management and
 * convenient Kotlin <-> JS conversion utilities.
 */
class JsEngine {

    companion object {
        private const val TAG = "JsEngine"
        private var nativeLoaded = false

        /** Ensure the native QuickJS library is loaded exactly once. */
        @Synchronized
        fun ensureNativeLoaded() {
            if (!nativeLoaded) {
                QuickJSLoader.init()
                nativeLoaded = true
            }
        }
    }

    /** The underlying QuickJS context. Accessible for advanced use (e.g. AxiosShim). */
    var context: QuickJSContext? = null
        private set

    /** Create a new QuickJS context. Must be called before any evaluation. */
    fun create() {
        ensureNativeLoaded()
        context = QuickJSContext.create().also {
            @Suppress("DEPRECATION")
            QuickJSLoader.initConsoleLog(it)
        }
    }

    /** Destroy the QuickJS context and release all resources. */
    fun destroy() {
        try {
            context?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying QuickJS context", e)
        } finally {
            context = null
        }
    }

    /** Evaluate JavaScript code and return the result. */
    fun evaluate(code: String): Any? {
        val ctx = requireContext()
        return ctx.evaluate(code)
    }

    /** Get the JS global object. */
    fun getGlobalObject(): JSObject? {
        return context?.globalObject
    }

    /**
     * Recursively convert a [JSObject] to a [Map].
     * Uses the library's built-in `toMap()` which handles nested objects.
     */
    fun jsObjectToMap(obj: JSObject): Map<String, Any?> {
        return obj.toMap()
    }

    /**
     * Recursively convert a [JSArray] to a [List].
     * Uses the library's built-in `toArray()` which handles nested objects.
     */
    fun jsArrayToList(arr: JSArray): List<Any?> {
        return arr.toArray()
    }

    /**
     * Inject a Kotlin [Map] into the JS global scope as a JS object with the given [name].
     * Supports nested maps, lists, and primitive values.
     */
    fun setGlobalMap(name: String, map: Map<String, Any?>) {
        val ctx = requireContext()
        val jsObj = mapToJsObject(ctx, map)
        ctx.globalObject.setProperty(name, jsObj)
    }

    // -- Internal helpers --

    private fun requireContext(): QuickJSContext {
        return context ?: throw IllegalStateException("JsEngine not created. Call create() first.")
    }

    private fun mapToJsObject(ctx: QuickJSContext, map: Map<String, Any?>): JSObject {
        val obj = ctx.createNewJSObject()
        for ((key, value) in map) {
            setJsProperty(obj, key, ctx, value)
        }
        return obj
    }

    private fun listToJsArray(ctx: QuickJSContext, list: List<*>): JSArray {
        val arr = ctx.createNewJSArray()
        for ((index, value) in list.withIndex()) {
            val jsValue = toJsValue(ctx, value)
            arr.set(jsValue, index)
        }
        return arr
    }

    @Suppress("UNCHECKED_CAST")
    private fun setJsProperty(obj: JSObject, key: String, ctx: QuickJSContext, value: Any?) {
        when (value) {
            null -> {
                // Set as undefined by not setting (QuickJS has no explicit null setter on JSObject)
                // Use evaluate as workaround is unnecessary; just skip or set empty string
            }
            is String -> obj.setProperty(key, value)
            is Boolean -> obj.setProperty(key, value)
            is Int -> obj.setProperty(key, value)
            is Long -> obj.setProperty(key, value)
            is Double -> obj.setProperty(key, value)
            is Float -> obj.setProperty(key, value.toDouble())
            is Number -> obj.setProperty(key, value.toDouble())
            is Map<*, *> -> obj.setProperty(key, mapToJsObject(ctx, value as Map<String, Any?>))
            is List<*> -> obj.setProperty(key, listToJsArray(ctx, value))
            is JSObject -> obj.setProperty(key, value)
            is JSCallFunction -> obj.setProperty(key, value)
            else -> obj.setProperty(key, value.toString())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toJsValue(ctx: QuickJSContext, value: Any?): Any? {
        return when (value) {
            null -> null
            is String, is Boolean, is Int, is Long, is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            is Map<*, *> -> mapToJsObject(ctx, value as Map<String, Any?>)
            is List<*> -> listToJsArray(ctx, value)
            else -> value.toString()
        }
    }
}
