package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchMusicListViewModel internal constructor(
    private val route: SearchMusicListRoute,
    private val sourceLoader: SearchMusicListSourceLoader,
    private val playerController: PlayerController,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        sourceLoader: SearchMusicListSourceLoader,
        playerController: PlayerController,
    ) : this(
        route = savedStateHandle.toRoute<SearchMusicListRoute>(),
        sourceLoader = sourceLoader,
        playerController = playerController,
    )

    private val query = MutableStateFlow("")
    private var initialAutofocusConsumed = false

    val uiState = combine(
        query,
        sourceLoader.observe(route),
    ) { currentQuery, sourceItems ->
        SearchMusicListUiState(
            query = currentQuery,
            sourceItems = sourceItems,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SearchMusicListUiState(),
    )

    fun consumeInitialAutofocusRequest(): Boolean {
        if (initialAutofocusConsumed) return false
        initialAutofocusConsumed = true
        return true
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun playFilteredItem(index: Int): Boolean {
        val filteredItems = uiState.value.filteredItems
        if (index !in filteredItems.indices) return false
        playerController.playQueue(filteredItems, index)
        return true
    }
}
