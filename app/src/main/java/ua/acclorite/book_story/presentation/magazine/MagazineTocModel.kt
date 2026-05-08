/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.magazine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.parser.magazine.MagazineParser
import ua.acclorite.book_story.domain.service.FileProvider
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MagazineTocModel @Inject constructor(
    private val getBook: GetBookUseCase,
    private val fileProvider: FileProvider,
    private val magazineParser: MagazineParser,
) : ViewModel() {

    private val _state = MutableStateFlow(MagazineTocState())
    val state = _state.asStateFlow()

    // Identifies which source the current state corresponds to. Without
    // this the activity-scoped VM would happily serve the first issue's
    // TOC for every subsequent open, since `_state.value.issue != null`
    // alone can't tell whether the cached issue matches the new request.
    private var loadedKey: String? = null

    /**
     * Library mode — resolves the book by id and locates its ePub through
     * SAF-based [FileProvider]. Persists `currentArticleHref` for the
     * "highlight last-read" feature.
     */
    fun loadFromLibrary(bookId: Int) {
        val key = "lib:$bookId"
        if (loadedKey == key && _state.value.issue != null) return
        loadedKey = key
        _state.value = MagazineTocState()
        viewModelScope.launch {
            val book = getBook(bookId)
            if (book == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Book #$bookId not found") }
                return@launch
            }
            val rawFile = withContext(Dispatchers.IO) {
                fileProvider.getFileFromBook(book).getOrNull()?.rawFile
            }
            if (rawFile == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Could not access ePub file.") }
                return@launch
            }
            parseAndPublish(rawFile, currentArticleHref = book.currentArticleHref)
        }
    }

    /**
     * Direct mode — opens an ePub already living in the app's filesDir
     * (e.g. one that arrived via the system's "Open with" sheet). No
     * library entry, no last-read tracking.
     */
    fun loadFromPath(epubPath: String) {
        val key = "path:$epubPath"
        if (loadedKey == key && _state.value.issue != null) return
        loadedKey = key
        _state.value = MagazineTocState()
        viewModelScope.launch {
            val rawFile = File(epubPath)
            if (!rawFile.exists()) {
                _state.update { it.copy(isLoading = false, errorMessage = "File not found: $epubPath") }
                return@launch
            }
            parseAndPublish(rawFile, currentArticleHref = null)
        }
    }

    private suspend fun parseAndPublish(rawFile: File, currentArticleHref: String?) {
        val issue = withContext(Dispatchers.IO) { magazineParser.parse(rawFile) }
        if (issue == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    epubPath = rawFile.absolutePath,
                    errorMessage = "This ePub is not a magazine.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                isLoading = false,
                issue = issue,
                epubPath = rawFile.absolutePath,
                currentArticleHref = currentArticleHref,
            )
        }
    }
}
