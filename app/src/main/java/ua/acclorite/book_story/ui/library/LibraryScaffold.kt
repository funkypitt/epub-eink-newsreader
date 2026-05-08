/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition
import ua.acclorite.book_story.presentation.library.model.SelectableBook
import ua.acclorite.book_story.ui.theme.DefaultTransition

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LibraryScaffold(
    books: List<SelectableBook>,
    selectedItemsCount: Int,
    hasSelectedItems: Boolean,
    titlePosition: LibraryTitlePosition,
    readButton: Boolean,
    showProgress: Boolean,
    showBookCount: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    bookCount: Int,
    focusRequester: FocusRequester,
    isLoading: Boolean,
    isRefreshing: Boolean,
    refreshState: PullRefreshState,
    layout: LibraryLayout,
    gridSize: Int,
    autoGridSize: Boolean,
    searchVisibility: (LibraryEvent.OnSearchVisibility) -> Unit,
    selectBook: (LibraryEvent.OnSelectBook) -> Unit,
    requestFocus: (LibraryEvent.OnRequestFocus) -> Unit,
    searchQueryChange: (LibraryEvent.OnSearchQueryChange) -> Unit,
    search: (LibraryEvent.OnSearch) -> Unit,
    selectBooks: (LibraryEvent.OnSelectBooks) -> Unit,
    clearSelectedBooks: (LibraryEvent.OnClearSelectedBooks) -> Unit,
    showDeleteDialog: (LibraryEvent.OnShowDeleteDialog) -> Unit,
    navigateToBrowse: (LibraryEvent.OnNavigateToBrowse) -> Unit,
    navigateToBookInfo: (LibraryEvent.OnNavigateToBookInfo) -> Unit,
    navigateToReader: (LibraryEvent.OnNavigateToReader) -> Unit,
) {
    Scaffold(
        Modifier
            .fillMaxSize()
            .pullRefresh(refreshState),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LibraryTopBar(
                books = books,
                selectedItemsCount = selectedItemsCount,
                hasSelectedItems = hasSelectedItems,
                showBookCount = showBookCount,
                showSearch = showSearch,
                searchQuery = searchQuery,
                bookCount = bookCount,
                focusRequester = focusRequester,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                searchVisibility = searchVisibility,
                requestFocus = requestFocus,
                searchQueryChange = searchQueryChange,
                search = search,
                selectBooks = selectBooks,
                clearSelectedBooks = clearSelectedBooks,
                showDeleteDialog = showDeleteDialog,
            )
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            DefaultTransition(visible = !isLoading) {
                LibraryLayout(
                    books = books,
                    gridSize = gridSize,
                    autoGridSize = autoGridSize,
                    layout = layout
                ) { book ->
                    LibraryItem(
                        book = book,
                        layout = layout,
                        hasSelectedItems = hasSelectedItems,
                        titlePosition = titlePosition,
                        readButton = readButton,
                        showProgress = showProgress,
                        selectBook = { select ->
                            selectBook(
                                LibraryEvent.OnSelectBook(
                                    id = book.data.id,
                                    select = select
                                )
                            )
                        },
                        navigateToBookInfo = {
                            navigateToBookInfo(
                                LibraryEvent.OnNavigateToBookInfo(
                                    book.data.id
                                )
                            )
                        },
                        navigateToReader = {
                            navigateToReader(
                                LibraryEvent.OnNavigateToReader(
                                    book.data.id
                                )
                            )
                        }
                    )
                }
            }

            LibraryEmptyPlaceholder(
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                isBooksEmpty = books.isEmpty(),
                navigateToBrowse = navigateToBrowse
            )

            LibraryRefreshIndicator(
                isRefreshing = isRefreshing,
                refreshState = refreshState
            )
        }
    }
}
