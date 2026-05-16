package com.hank.musicfree.logging

inline fun <T> timedFields(block: () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    val result = block()
    return result to (System.nanoTime() - start) / 1_000_000
}

suspend inline fun <T> timedSuspend(crossinline block: suspend () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    return block() to ((System.nanoTime() - start) / 1_000_000)
}
