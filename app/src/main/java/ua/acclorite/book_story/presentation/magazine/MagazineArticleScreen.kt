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
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.magazine.MagazineArticleContent
import ua.acclorite.book_story.ui.navigator.LocalNavigator

@Parcelize
data class MagazineArticleScreen(
    val bookId: Int,
    val articleHref: String,
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val model = hiltViewModel<MagazineArticleModel>()
        val state by model.state.collectAsStateWithLifecycle()

        LaunchedEffect(bookId, articleHref) { model.load(bookId, articleHref) }

        MagazineArticleContent(
            state = state,
            onPrev = { model.goToOffset(-1) },
            onTocHome = { navigator.pop() },
            onAppHome = { navigator.popToRoot() },
            onNext = { model.goToOffset(+1) },
        )
    }
}
