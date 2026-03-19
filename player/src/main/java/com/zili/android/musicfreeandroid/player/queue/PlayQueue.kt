package com.zili.android.musicfreeandroid.player.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode

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
        val insertAt = if (isEmpty) 0 else currentIndex + 1
        _items.add(insertAt, item)
        if (_items.size == 1) currentIndex = 0
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

    fun clear() {
        _items.clear()
        currentIndex = -1
    }
}
