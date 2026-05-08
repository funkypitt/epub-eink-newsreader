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
import ua.acclorite.book_story.presentation.library.LibraryScreen
import ua.acclorite.book_story.presentation.magazine.MagazineTocScreen
import ua.acclorite.book_story.presentation.navigator.Screen
import ua.acclorite.book_story.ui.navigator.LocalNavigator

/**
 * Entry point used by Android's "Open with" sheet. Receives a content/file
 * URI string, copies it into the app's filesDir and pushes the magazine
 * TOC. If the file isn't a supported magazine, falls back to the library.
 */
@Parcelize
data class OpenIntentScreen(val uriString: String) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current
        val model = hiltViewModel<OpenIntentModel>()

        LaunchedEffect(uriString) { model.handle(context, uriString) }
        LaunchedEffect(model) {
            model.target.collect { target ->
                when (target) {
                    is OpenIntentTarget.Magazine -> {
                        navigator.push(
                            targetScreen = MagazineTocScreen(epubPath = target.epubPath),
                            saveInBackStack = false,
                        )
                    }
                    is OpenIntentTarget.Failed -> {
                        Toast.makeText(context, target.reason, Toast.LENGTH_LONG).show()
                        navigator.push(LibraryScreen, saveInBackStack = false)
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
