package com.hank.musicfree.player.queue

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RepeatMode

class PlayQueue {

    private val _items = mutableListOf<MusicItem>()
    val items: List<MusicItem> get() = _items.toList()

    var currentIndex: Int = -1
        private set

    val currentItem: MusicItem?
        get() = _items.getOrNull(currentIndex)

    val size: Int get() = _items.size
    val isEmpty: Boolean get() = _items.isEmpty()

    fun setQueue(items: List<MusicItem>, startIndex: Int = 0) {
        _items.clear()
        _items.addAll(items)
        currentIndex = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.lastIndex)
    }

    fun add(item: MusicItem) {
        _items.add(item)
        if (_items.size == 1) currentIndex = 0
    }

    fun addNext(item: MusicItem) {
        if (isEmpty) {
            _items.add(item)
            currentIndex = 0
            return
        }
        if (currentItem?.sameQueueIdentity(item) == true) return

        val existingIndex = _items.indexOfFirst { it.sameQueueIdentity(item) }
        if (existingIndex >= 0) {
            _items.removeAt(existingIndex)
            if (existingIndex < currentIndex) {
                currentIndex--
            }
        }

        val insertAt = (currentIndex + 1).coerceIn(0, _items.size)
        _items.add(insertAt, item)
    }

    fun remove(index: Int): MusicItem? {
        if (index !in _items.indices) return currentItem
        _items.removeAt(index)
        if (_items.isEmpty()) {
            currentIndex = -1
            return null
        }
        when {
            index < currentIndex -> currentIndex--
            index == currentIndex -> {
                currentIndex = currentIndex.coerceAtMost(_items.lastIndex)
            }
        }
        return currentItem
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in _items.indices || toIndex !in _items.indices) return
        val item = _items.removeAt(fromIndex)
        _items.add(toIndex, item)
        currentIndex = when {
            currentIndex == fromIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
    }

    fun replaceCurrent(expectedIndex: Int, expectedItem: MusicItem, item: MusicItem): Boolean {
        if (expectedIndex != currentIndex || expectedIndex !in _items.indices) return false
        if (_items[expectedIndex] != expectedItem) return false
        _items[expectedIndex] = item
        originalOrder = originalOrder?.let { saved ->
            val updated = saved.toMutableList()
            val originalIndex = updated.indexOfFirst { it == expectedItem }
            if (originalIndex >= 0) {
                updated[originalIndex] = item
            }
            updated
        }
        return true
    }

    fun replaceAt(expectedIndex: Int, expectedItem: MusicItem, item: MusicItem): Boolean {
        if (expectedIndex !in _items.indices) return false
        if (_items[expectedIndex] != expectedItem) return false
        _items[expectedIndex] = item
        originalOrder = originalOrder?.let { saved ->
            val updated = saved.toMutableList()
            val originalIndex = updated.indexOfFirst { it == expectedItem }
            if (originalIndex >= 0) {
                updated[originalIndex] = item
            }
            updated
        }
        return true
    }

    /**
     * Returns the item that would play after [currentItem] without advancing the queue.
     * Returns null when there is no following item (e.g. queue is empty or [RepeatMode.OFF]
     * at the last track). Does not handle [RepeatMode.ONE] — callers needing that case
     * should read [currentItem] directly.
     */
    fun peekNextItem(repeatMode: RepeatMode): MusicItem? {
        val nextIndex = peekNextIndex(repeatMode) ?: return null
        return _items.getOrNull(nextIndex)
    }

    fun peekNextIndex(repeatMode: RepeatMode): Int? {
        if (isEmpty) return null
        return when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL -> (currentIndex + 1) % _items.size
            RepeatMode.OFF -> {
                val idx = currentIndex + 1
                if (idx > _items.lastIndex) return null else idx
            }
        }
    }

    fun peekPreviousIndex(repeatMode: RepeatMode): Int? {
        if (isEmpty) return null
        return when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL -> (currentIndex - 1 + _items.size) % _items.size
            RepeatMode.OFF -> {
                val idx = currentIndex - 1
                if (idx < 0) return null else idx
            }
        }
    }

    fun next(repeatMode: RepeatMode): MusicItem? {
        if (isEmpty) return null
        val nextIndex = when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL -> (currentIndex + 1) % _items.size
            RepeatMode.OFF -> {
                val idx = currentIndex + 1
                if (idx > _items.lastIndex) return null else idx
            }
        }
        currentIndex = nextIndex
        return currentItem
    }

    fun previous(repeatMode: RepeatMode): MusicItem? {
        if (isEmpty) return null
        val prevIndex = when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL -> (currentIndex - 1 + _items.size) % _items.size
            RepeatMode.OFF -> {
                val idx = currentIndex - 1
                if (idx < 0) return null else idx
            }
        }
        currentIndex = prevIndex
        return currentItem
    }

    fun skipTo(index: Int): MusicItem? {
        if (index !in _items.indices) return null
        currentIndex = index
        return currentItem
    }

    private var originalOrder: List<MusicItem>? = null
    val isShuffled: Boolean get() = originalOrder != null

    fun shuffle() {
        if (_items.size <= 1) return
        val current = currentItem ?: return
        originalOrder = _items.toList()

        _items.remove(current)
        _items.shuffle()
        _items.add(0, current)
        currentIndex = 0
    }

    fun unshuffle() {
        val saved = originalOrder ?: return
        val current = currentItem
        val survivingIds = _items.map { "${it.platform}:${it.id}" }.toSet()
        val originalIds = saved.map { "${it.platform}:${it.id}" }.toSet()
        val newItems = _items.filter { "${it.platform}:${it.id}" !in originalIds }

        _items.clear()
        for (item in saved) {
            if ("${item.platform}:${item.id}" in survivingIds) {
                _items.add(item)
            }
        }
        _items.addAll(newItems)

        currentIndex = if (current != null) {
            _items.indexOfFirst { it.id == current.id && it.platform == current.platform }
                .coerceAtLeast(0)
        } else {
            -1
        }
        originalOrder = null
    }

    fun clear() {
        _items.clear()
        currentIndex = -1
        originalOrder = null
    }

    private fun MusicItem.sameQueueIdentity(other: MusicItem): Boolean =
        id == other.id && platform == other.platform
}
