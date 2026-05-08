/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.open_book

import android.os.Parcelable
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.parcelize.Parcelize
import ua.acclorite.book_story.presentation.magazine.MagazineTocScreen
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.navigator.LocalNavigator

/**
 * Transient dispatcher screen: pushes [MagazineTocScreen] when the underlying ePub
 * looks like a supported magazine, otherwise shows a toast and pops back to the
 * previous screen. There is no fallback reader in this build.
 */
@Parcelize
data class OpenBookScreen(val bookId: Int) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current
        val model = hiltViewModel<OpenBookModel>()

        LaunchedEffect(bookId) { model.decide(bookId) }
        LaunchedEffect(model) {
            model.target.collect { target ->
                when (target) {
                    is OpenBookTarget.Magazine -> {
                        navigator.push(
                            targetScreen = MagazineTocScreen(bookId = target.bookId),
                            saveInBackStack = false,
                        )
                    }
                    is OpenBookTarget.Unsupported -> {
                        Toast.makeText(
                            context,
                            "This ePub is not a supported magazine",
                            Toast.LENGTH_LONG,
                        ).show()
                        navigator.pop()
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
