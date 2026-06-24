/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.runtime.Composable
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition
import ua.acclorite.book_story.presentation.library.model.SelectableBook

@Composable
fun LibraryItem(
    book: SelectableBook,
    layout: LibraryLayout,
    hasSelectedItems: Boolean,
    titlePosition: LibraryTitlePosition,
    showProgress: Boolean,
    selectBook: (select: Boolean?) -> Unit,
    navigateToReader: () -> Unit
) {
    when (layout) {
        LibraryLayout.LIST -> {
            LibraryListItem(
                book = book,
                hasSelectedItems = hasSelectedItems,
                showProgress = showProgress,
                selectBook = selectBook,
                navigateToReader = navigateToReader
            )
        }

        LibraryLayout.GRID -> {
            LibraryGridItem(
                book = book,
                hasSelectedItems = hasSelectedItems,
                titlePosition = titlePosition,
                showProgress = showProgress,
                selectBook = selectBook,
                navigateToReader = navigateToReader
            )
        }
    }
}