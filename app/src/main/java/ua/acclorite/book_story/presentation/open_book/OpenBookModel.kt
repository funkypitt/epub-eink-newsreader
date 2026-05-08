/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.open_book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.parser.magazine.MagazineParser
import ua.acclorite.book_story.domain.service.FileProvider
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import javax.inject.Inject

sealed class OpenBookTarget {
    abstract val bookId: Int
    data class Reader(override val bookId: Int) : OpenBookTarget()
    data class Magazine(override val bookId: Int) : OpenBookTarget()
}

@HiltViewModel
class OpenBookModel @Inject constructor(
    private val getBook: GetBookUseCase,
    private val fileProvider: FileProvider,
    private val magazineParser: MagazineParser,
) : ViewModel() {

    private val _target = MutableSharedFlow<OpenBookTarget>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val target = _target.asSharedFlow()

    private var decided = false

    fun decide(bookId: Int) {
        if (decided) return
        decided = true
        viewModelScope.launch {
            val book = getBook(bookId)
            val isMagazine = book?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val rawFile = fileProvider.getFileFromBook(it).getOrNull()?.rawFile
                            ?: return@runCatching false
                        magazineParser.canParse(rawFile)
                    }.getOrDefault(false)
                }
            } ?: false
            _target.tryEmit(
                if (isMagazine) OpenBookTarget.Magazine(bookId)
                else OpenBookTarget.Reader(bookId)
            )
        }
    }
}
