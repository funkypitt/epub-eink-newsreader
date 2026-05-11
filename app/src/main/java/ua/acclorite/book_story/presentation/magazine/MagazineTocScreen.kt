/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.magazine

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.library.LibraryScreen
import ua.acclorite.book_story.presentation.navigator.Navigator
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.magazine.MagazineTocContent
import ua.acclorite.book_story.ui.navigator.LocalNavigator

/**
 * Magazine TOC screen. Reachable from two paths:
 * - Library: opens a book by id (looked up via [GetBookUseCase] and
 *   resolved through SAF [FileProvider]). Last-read article is persisted.
 * - Intent: opens a file already cached locally by [OpenIntentScreen].
 *   No DB write-back.
 *
 * Exactly one of [bookId] / [epubPath] must be set.
 */
@Parcelize
data class MagazineTocScreen(
    val bookId: Int? = null,
    val epubPath: String? = null,
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val model = hiltViewModel<MagazineTocModel>()
        val state by model.state.collectAsStateWithLifecycle()

        LaunchedEffect(bookId, epubPath) {
            when {
                bookId != null -> model.loadFromLibrary(bookId)
                epubPath != null -> model.loadFromPath(epubPath)
            }
        }

        MagazineTocContent(
            state = state,
            onArticleClick = { article ->
                navigator.push(
                    MagazineArticleScreen(
                        bookId = bookId,
                        epubPath = epubPath,
                        articleHref = article.contentHref,
                    )
                )
            },
            onHome = { navigator.pop() },
            onAppHome = { navigator.goToLibrary() },
        )
    }
}

/**
 * Navigate to the library root. When launched via Android's "Open with" sheet,
 * OpenIntentScreen replaces itself with the TOC and never puts LibraryScreen
 * on the stack — so popping is a no-op. Pop down to a single entry and, if
 * that entry isn't the library, replace it.
 */
internal fun Navigator.goToLibrary() {
    while (items.value.size > 1) pop()
    if (lastItem.value !== LibraryScreen) {
        push(LibraryScreen, saveInBackStack = false)
    }
}
