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
import ua.acclorite.book_story.presentation.navigator.Navigator
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.magazine.MagazineTocContent
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Parcelize
data class MagazineTocScreen(val bookId: Int) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val model = hiltViewModel<MagazineTocModel>()
        val state by model.state.collectAsStateWithLifecycle()

        LaunchedEffect(bookId) { model.load(bookId) }

        MagazineTocContent(
            state = state,
            onArticleClick = { article ->
                navigator.push(MagazineArticleScreen(bookId = bookId, articleHref = article.contentHref))
            },
            onHome = { navigator.pop() },
            onAppHome = { navigator.popToRoot() },
        )
    }
}

internal fun Navigator.popToRoot() {
    while (items.value.size > 1) pop()
}
