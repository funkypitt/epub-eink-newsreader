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

    private var loadedKey: String? = null

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
            val rawFile = withContext(Dispatchers.IO) { resolveFile(book.filePath) }
            if (rawFile == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Could not access ePub file.") }
                return@launch
            }
            parseAndPublish(rawFile, currentArticleHref = book.currentArticleHref)
        }
    }

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

    private fun resolveFile(filePath: String): File? {
        val file = File(filePath)
        if (file.exists() && file.canRead()) return file
        return fileProvider.getFileFromBook(
            ua.acclorite.book_story.domain.model.library.Book.default.copy(filePath = filePath)
        ).getOrNull()?.rawFile
    }

    private suspend fun parseAndPublish(rawFile: File, currentArticleHref: String?) {
        val issue = withContext(Dispatchers.IO) { magazineParser.parse(rawFile) }
        if (issue == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    epubPath = rawFile.absolutePath,
                    errorMessage = "Could not parse this ePub.",
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
