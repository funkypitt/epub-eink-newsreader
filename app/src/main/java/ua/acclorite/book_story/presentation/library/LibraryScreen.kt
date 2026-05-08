/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.library

import android.os.Parcelable
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.core.helpers.compareByWithOrder
import ua.acclorite.book_story.presentation.library.model.LibrarySortOrder
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import ua.acclorite.book_story.ui.library.LibraryContent
import ua.acclorite.book_story.ui.library.LibraryEffects

@Parcelize
object LibraryScreen : Screen, Parcelable {

    @IgnoredOnParcel
    const val DELETE_DIALOG = "delete_dialog"

    @IgnoredOnParcel
    val refreshListChannel: Channel<Long> = Channel(Channel.CONFLATED)

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val screenModel = hiltViewModel<LibraryModel>()
        val settings = LocalSettings.current

        val state = screenModel.state.collectAsStateWithLifecycle()

        val sortOrder = settings.librarySortOrder.value
        val sortOrderDescending = settings.librarySortOrderDescending.value

        val sortedBooks = remember(state.value.books, sortOrder, sortOrderDescending) {
            derivedStateOf {
                state.value.books.sortedWith(
                    compareByWithOrder(sortOrderDescending) { book ->
                        when (sortOrder) {
                            LibrarySortOrder.NAME -> book.data.title.trim()
                            LibrarySortOrder.LAST_READ -> book.data.lastOpened
                            LibrarySortOrder.PROGRESS -> book.data.progress
                            LibrarySortOrder.AUTHOR -> book.data.author.getAsString()
                        }
                    }
                )
            }
        }

        val focusRequester = remember { FocusRequester() }
        val refreshState = rememberPullRefreshState(
            refreshing = state.value.isRefreshing,
            onRefresh = {
                screenModel.onEvent(
                    LibraryEvent.OnRefreshList(
                        loading = false,
                        hideSearch = true
                    )
                )
            }
        )

        LibraryEffects(
            effects = screenModel.effects,
            focusRequester = focusRequester
        )

        LibraryContent(
            books = sortedBooks.value,
            selectedItemsCount = state.value.selectedItemsCount,
            hasSelectedItems = state.value.hasSelectedItems,
            titlePosition = settings.libraryTitlePosition.value,
            readButton = settings.libraryShowReadButton.value,
            showProgress = settings.libraryShowProgress.value,
            showBookCount = settings.libraryShowBookCount.value,
            showSearch = state.value.showSearch,
            searchQuery = state.value.searchQuery,
            bookCount = sortedBooks.value.count(),
            focusRequester = focusRequester,
            isLoading = state.value.isLoading,
            isRefreshing = state.value.isRefreshing,
            doublePressExit = settings.doublePressExit.value,
            layout = settings.libraryLayout.value,
            gridSize = settings.libraryGridSize.value,
            autoGridSize = settings.libraryAutoGridSize.value,
            refreshState = refreshState,
            dialog = state.value.dialog,
            selectBook = screenModel::onEvent,
            searchVisibility = screenModel::onEvent,
            requestFocus = screenModel::onEvent,
            searchQueryChange = screenModel::onEvent,
            search = screenModel::onEvent,
            selectBooks = screenModel::onEvent,
            clearSelectedBooks = screenModel::onEvent,
            actionDeleteDialog = screenModel::onEvent,
            showDeleteDialog = screenModel::onEvent,
            dismissDialog = screenModel::onEvent,
            navigateToBrowse = screenModel::onEvent,
            navigateToReader = screenModel::onEvent,
            navigateToBookInfo = screenModel::onEvent,
        )
    }
}
