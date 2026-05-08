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
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MagazineTocModel @Inject constructor(
    private val getBook: GetBookUseCase,
    private val magazineParser: MagazineParser,
) : ViewModel() {

    private val _state = MutableStateFlow(MagazineTocState())
    val state = _state.asStateFlow()

    fun load(bookId: Int) {
        if (_state.value.issue != null) return
        viewModelScope.launch {
            val book = getBook(bookId)
            if (book == null) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = "Book #$bookId not found")
                }
                return@launch
            }
            val issue = withContext(Dispatchers.IO) {
                magazineParser.parse(File(book.filePath))
            }
            if (issue == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        epubPath = book.filePath,
                        errorMessage = "This ePub is not a magazine.",
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    issue = issue,
                    epubPath = book.filePath,
                    currentArticleHref = book.currentArticleHref,
                )
            }
        }
    }
}
