/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.open_book

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.magazine.MagazineTocScreen
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.presentation.reader.ReaderScreen
import ua.acclorite.book_story.ui.navigator.LocalNavigator

/**
 * Transient dispatcher screen: decides between [ReaderScreen] and
 * [MagazineTocScreen] based on whether the underlying ePub looks like a
 * magazine. Replaces itself in the navigation stack so the back button
 * jumps straight to the previous screen.
 */
@Parcelize
data class OpenBookScreen(val bookId: Int) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val model = hiltViewModel<OpenBookModel>()

        LaunchedEffect(bookId) { model.decide(bookId) }
        LaunchedEffect(model) {
            model.target.collect { target ->
                val screen: Screen = when (target) {
                    is OpenBookTarget.Magazine -> MagazineTocScreen(bookId = target.bookId)
                    is OpenBookTarget.Reader -> ReaderScreen(bookId = target.bookId)
                }
                navigator.push(targetScreen = screen, saveInBackStack = false)
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
